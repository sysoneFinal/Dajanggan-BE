package com.dajanggan.domain.engine.bgwriter.scheduler;

import com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg1m;
import com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg5m;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * BGWriter 메트릭 수집 스케줄러
 * Raw 데이터만 수집 (집계는 Batch Aggregator에서 수행)
 * 
 * 주의: 아래 메서드들은 현재 사용되지 않지만 향후 사용을 위해 보존:
 * - calculateAggregation1m()
 * - calculateAggregation5m()
 * - determineBgWriterStatus()
 * - calculateSafeDelta()
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
     * 1분마다 실행 (매분 5초) - metric/batch 스타일에 맞춤
     * Raw 데이터만 수집 및 저장 (집계는 Batch Aggregator에서 수행)
     */
    @Scheduled(cron = "5 * * * * *")
    public void collectBgWriterMetrics() {
        log.info("========== BGWriter 메트릭 수집 시작 ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            List<Long> instanceIds = bgWriterMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());
            
            if (instanceIds.isEmpty()) {
                log.warn("활성 인스턴스가 없습니다. DB의 instance 테이블에 is_active=true인 인스턴스가 있는지 확인하세요.");
                return;
            }

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
     * 특정 인스턴스의 메트릭 처리 (Raw 데이터만 저장)
     */
    private void processInstanceMetrics(Long instanceId, OffsetDateTime collectedAt) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, "postgres");
        Map<String, Object> currentData = collectFromPgStatBgwriter(jdbcTemplate);
        BgWriterRaw previousRaw = bgWriterMapper.selectPreviousRaw(instanceId, collectedAt);

        log.debug("BGWriter 1분 집계 데이터 수집: instanceId={}, collectedAt={}, previousRaw={}", 
                instanceId, collectedAt, previousRaw != null ? "존재" : "없음");

        BgWriterRaw raw = buildBgWriterRaw(instanceId, collectedAt, currentData);
        bgWriterMapper.insertRaw(raw);
        log.debug("Raw 데이터 저장 완료: instanceId={}, buffersClean={}, buffersBackend={}, buffersBackendFsync={}, maxwrittenClean={}", 
                instanceId, raw.getBuffersClean(), raw.getBuffersBackend(), raw.getBuffersBackendFsync(), raw.getMaxwrittenClean());

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
    private BgWriterRaw buildBgWriterRaw(Long instanceId, OffsetDateTime collectedAt,
                                         Map<String, Object> data) {
        Long buffersClean = getLongValue(data, "buffers_clean");
        Long maxwrittenClean = getLongValue(data, "maxwritten_clean");
        Long buffersBackend = getLongValue(data, "buffers_backend");
        Long buffersBackendFsync = getLongValue(data, "buffers_backend_fsync");
        Long buffersAlloc = getLongValue(data, "buffers_alloc");

        log.debug("pg_stat_bgwriter 데이터: instanceId={}, buffersClean={}, buffersBackend={}, buffersBackendFsync={}, maxwrittenClean={}, buffersAlloc={}", 
                instanceId, buffersClean, buffersBackend, buffersBackendFsync, maxwrittenClean, buffersAlloc);

        return BgWriterRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .buffersClean(buffersClean)
                .maxwrittenClean(maxwrittenClean)
                .buffersBackend(buffersBackend)
                .buffersBackendFsync(buffersBackendFsync)
                .buffersAlloc(buffersAlloc)
                .avgCycleTimeMs(null)  // Raw 데이터에는 사이클 시간 없음 (Agg에서만 계산)
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

