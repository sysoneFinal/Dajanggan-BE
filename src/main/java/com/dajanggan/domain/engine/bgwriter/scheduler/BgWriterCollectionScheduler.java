package com.dajanggan.domain.engine.bgwriter.scheduler;

import com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg;
import com.dajanggan.domain.engine.bgwriter.domain.BgWriterRaw;
import com.dajanggan.domain.engine.bgwriter.repository.BgWriterMapper;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * BGWriter 메트릭 수집 스케줄러
 * 1분마다 실행:
 * 1. pg_stat_bgwriter에서 bgwriter 관련 데이터 수집
 * 2. Raw 데이터 저장
 * 3. 이전 데이터와 비교하여 증분 계산 후 Agg 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BgWriterCollectionScheduler {

    private final BgWriterMapper bgWriterMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    @PostConstruct
    public void init() {
        log.info("========== BgWriterCollectionScheduler 초기화 완료 ==========");
    }

    /**
     * 1분마다 실행 (매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectBgWriterMetrics() {
        log.info("========== BGWriter 메트릭 수집 시작 ==========");

        try {
            LocalDateTime collectedAt = LocalDateTime.now();
            List<Long> instanceIds = bgWriterMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    processInstanceMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("BGWriter 메트릭 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== BGWriter 메트릭 수집 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("BGWriter 메트릭 수집 중 오류 발생", e);
        }
    }

    /**
     * 특정 인스턴스의 메트릭 처리
     */
    private void processInstanceMetrics(Long instanceId, LocalDateTime collectedAt) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, "postgres");
        Map<String, Object> currentData = collectFromPgStatBgwriter(jdbcTemplate);
        BgWriterRaw previousRaw = bgWriterMapper.selectPreviousRaw(instanceId);

        BgWriterRaw raw = buildBgWriterRaw(instanceId, collectedAt, currentData);
        bgWriterMapper.insertRaw(raw);
        log.debug("Raw 데이터 저장 완료: instanceId={}", instanceId);

        if (previousRaw != null) {
            BgWriterAgg agg = calculateAggregation(instanceId, collectedAt, raw, previousRaw);
            bgWriterMapper.insertAgg(agg);
            log.debug("Agg 데이터 저장 완료: instanceId={}", instanceId);
        }

        log.info("메트릭 처리 완료: instanceId={}", instanceId);
    }

    /**
     * pg_stat_bgwriter에서 데이터 수집
     */
    private Map<String, Object> collectFromPgStatBgwriter(JdbcTemplate jdbcTemplate) {
        String query = """
            SELECT 
                buffers_clean,
                maxwritten_clean,
                buffers_backend,
                buffers_backend_fsync,
                buffers_alloc,
                stats_reset
            FROM pg_stat_bgwriter
            """;

        return jdbcTemplate.queryForMap(query);
    }

    /**
     * BgWriterRaw 객체 생성
     */
    private BgWriterRaw buildBgWriterRaw(Long instanceId, LocalDateTime collectedAt,
                                         Map<String, Object> data) {
        Long buffersClean = getLongValue(data, "buffers_clean");
        Long maxwrittenClean = getLongValue(data, "maxwritten_clean");
        Long buffersBackend = getLongValue(data, "buffers_backend");
        Long buffersBackendFsync = getLongValue(data, "buffers_backend_fsync");
        Long buffersAlloc = getLongValue(data, "buffers_alloc");

        return BgWriterRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .buffersClean(buffersClean)
                .maxwrittenClean(maxwrittenClean)
                .buffersBackend(buffersBackend)
                .buffersBackendFsync(buffersBackendFsync)
                .buffersAlloc(buffersAlloc)
                .avgCycleTimeMs(0.0)  // 계산 필요 시 추가
                .build();
    }

    /**
     * 증분 계산하여 Agg 데이터 생성
     */
    private BgWriterAgg calculateAggregation(Long instanceId, LocalDateTime collectedAt,
                                             BgWriterRaw current, BgWriterRaw previous) {
        // 증분 계산
        long deltaBuffersClean = current.getBuffersClean() - previous.getBuffersClean();
        long deltaBuffersBackend = current.getBuffersBackend() - previous.getBuffersBackend();
        long deltaBackendFsync = current.getBuffersBackendFsync() - previous.getBuffersBackendFsync();
        long deltaMaxwrittenClean = current.getMaxwrittenClean() - previous.getMaxwrittenClean();

        // Backend Flush 비율 계산
        long totalBuffers = deltaBuffersClean + deltaBuffersBackend;
        double backendFlushRatio = 0.0;
        if (totalBuffers > 0) {
            backendFlushRatio = (100.0 * deltaBuffersBackend) / totalBuffers;
        }

        // Clean Rate 계산 (버퍼/초)
        double cleanRate = deltaBuffersClean / 60.0;

        // 상태 판단 (Backend Flush 비율 기준)
        String status = "정상";
        if (backendFlushRatio > 30) {
            status = "위험";
        } else if (backendFlushRatio > 15) {
            status = "주의";
        }

        return BgWriterAgg.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgBackendFlushRatio(backendFlushRatio)
                .avgCleanRate(cleanRate)
                .totalBuffersClean(deltaBuffersClean)
                .totalBuffersBackend(deltaBuffersBackend)
                .totalBackendFsync(deltaBackendFsync)
                .totalMaxwrittenClean(deltaMaxwrittenClean)
                .status(status)
                .avgCycleTimeMs(0.0)  // 계산 필요 시 추가
                .build();
    }

    /**
     * Map에서 Long 값 추출 헬퍼
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
