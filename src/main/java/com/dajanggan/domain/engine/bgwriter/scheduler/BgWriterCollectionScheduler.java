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
 * 1분 집계: 매분 0초 실행
 * 5분 집계: 5분마다 실행 (0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55분)
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
     * 1분 집계 데이터 수집
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectBgWriter1mMetrics() {
        log.info("========== BGWriter 1분 집계 시작 ==========");

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
                    processInstance1mMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("BGWriter 1분 집계 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== BGWriter 1분 집계 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("BGWriter 1분 집계 중 오류 발생", e);
        }
    }

    /**
     * 5분마다 실행 (0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55분)
     * 5분 집계 데이터 수집
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void collectBgWriter5mMetrics() {
        log.info("========== BGWriter 5분 집계 시작 ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            List<Long> instanceIds = bgWriterMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    processInstance5mMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    // 집계 실패는 정상적인 상황일 수 있으므로 로그 최소화
                    log.debug("BGWriter 5분 집계 처리 실패: instanceId={}, error={}", instanceId, e.getMessage());
                }
            }

            log.info("========== BGWriter 5분 집계 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            // 집계 실패는 정상적인 상황일 수 있으므로 로그 최소화
            log.debug("BGWriter 5분 집계 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 특정 인스턴스의 1분 집계 처리
     */
    private void processInstance1mMetrics(Long instanceId, OffsetDateTime collectedAt) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, "postgres");
        Map<String, Object> currentData = collectFromPgStatBgwriter(jdbcTemplate);
        BgWriterRaw previousRaw = bgWriterMapper.selectPreviousRaw(instanceId);

        log.debug("BGWriter 1분 집계 데이터 수집: instanceId={}, collectedAt={}, previousRaw={}", 
                instanceId, collectedAt, previousRaw != null ? "존재" : "없음");

        BgWriterRaw raw = buildBgWriterRaw(instanceId, collectedAt, currentData);
        bgWriterMapper.insertRaw(raw);
        log.debug("Raw 데이터 저장 완료: instanceId={}, buffersClean={}, buffersBackend={}, buffersBackendFsync={}, maxwrittenClean={}", 
                instanceId, raw.getBuffersClean(), raw.getBuffersBackend(), raw.getBuffersBackendFsync(), raw.getMaxwrittenClean());

        if (previousRaw != null) {
            BgWriterAgg1m agg1m = calculateAggregation1m(instanceId, collectedAt, raw, previousRaw);
            bgWriterMapper.insertAgg1m(agg1m);
            log.info("1분 집계 데이터 저장 완료: instanceId={}, totalBuffersClean={}, totalBuffersBackend={}, totalBackendFsync={}, totalMaxwrittenClean={}, avgBackendFlushRatio={}, avgCleanRate={}, status={}", 
                    instanceId, 
                    agg1m.getTotalBuffersClean(),
                    agg1m.getTotalBuffersBackend(),
                    agg1m.getTotalBackendFsync(),
                    agg1m.getTotalMaxwrittenClean(),
                    agg1m.getAvgBackendFlushRatio(),
                    agg1m.getAvgCleanRate(),
                    agg1m.getStatus());
        } else {
            log.warn("첫 번째 수집: 이전 데이터가 없어 집계 데이터 생성을 스킵합니다. instanceId={}", instanceId);
        }

        log.info("1분 집계 처리 완료: instanceId={}", instanceId);
    }

    /**
     * 특정 인스턴스의 5분 집계 처리
     */
    private void processInstance5mMetrics(Long instanceId, OffsetDateTime collectedAt) {
        // 5분 전부터 현재까지의 1분 집계 데이터 조회 (5개)
        OffsetDateTime startTime = collectedAt.minusMinutes(5);
        List<BgWriterAgg1m> agg1mList = bgWriterMapper.selectPreviousAgg1m(instanceId, startTime, collectedAt);

        log.debug("5분 집계 데이터 조회: instanceId={}, startTime={}, collectedAt={}, agg1mListSize={}", 
                instanceId, startTime, collectedAt, agg1mList != null ? agg1mList.size() : 0);

        if (agg1mList == null || agg1mList.isEmpty()) {
            log.warn("5분 집계 스킵: 1분 집계 데이터가 없습니다. instanceId={}, startTime={}, collectedAt={}", 
                    instanceId, startTime, collectedAt);
            return;
        }

        BgWriterAgg5m agg5m = calculateAggregation5m(instanceId, collectedAt, agg1mList);
        bgWriterMapper.insertAgg5m(agg5m);
        log.info("5분 집계 데이터 저장 완료: instanceId={}, totalBuffersClean={}, totalBuffersBackend={}, totalBackendFsync={}, totalMaxwrittenClean={}, avgBackendFlushRatio={}, avgCleanRate={}, status={}", 
                instanceId,
                agg5m.getTotalBuffersClean(),
                agg5m.getTotalBuffersBackend(),
                agg5m.getTotalBackendFsync(),
                agg5m.getTotalMaxwrittenClean(),
                agg5m.getAvgBackendFlushRatio(),
                agg5m.getAvgCleanRate(),
                agg5m.getStatus());

        log.info("5분 집계 처리 완료: instanceId={}, 1분 집계 수={}", instanceId, agg1mList.size());
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
     * 증분 계산하여 1분 집계 데이터 생성
     */
    private BgWriterAgg1m calculateAggregation1m(Long instanceId, OffsetDateTime collectedAt,
                                                 BgWriterRaw current, BgWriterRaw previous) {
        // 증분 계산 (음수 방어: stats_reset 발생 시 현재 값 사용)
        long deltaBuffersClean = calculateSafeDelta(
                current.getBuffersClean(),
                previous.getBuffersClean()
        );
        long deltaBuffersBackend = calculateSafeDelta(
                current.getBuffersBackend(),
                previous.getBuffersBackend()
        );
        long deltaBackendFsync = calculateSafeDelta(
                current.getBuffersBackendFsync(),
                previous.getBuffersBackendFsync()
        );
        long deltaMaxwrittenClean = calculateSafeDelta(
                current.getMaxwrittenClean(),
                previous.getMaxwrittenClean()
        );

        log.debug("BGWriter 1분 집계 계산: instanceId={}, deltaBuffersClean={}, deltaBuffersBackend={}, deltaBackendFsync={}, deltaMaxwrittenClean={}", 
                instanceId, deltaBuffersClean, deltaBuffersBackend, deltaBackendFsync, deltaMaxwrittenClean);

        // Backend Flush 비율 계산
        long totalBuffers = deltaBuffersClean + deltaBuffersBackend;
        double backendFlushRatio = 0.0;
        if (totalBuffers > 0) {
            backendFlushRatio = (100.0 * deltaBuffersBackend) / totalBuffers;
        }

        // Clean Rate 계산 (버퍼/초)
        double cleanRate = deltaBuffersClean / 60.0;
        
        log.debug("BGWriter 비율 계산: instanceId={}, totalBuffers={}, backendFlushRatio={}, cleanRate={}", 
                instanceId, totalBuffers, backendFlushRatio, cleanRate);

        // BGWriter 사이클 시간 계산 (밀리초)
        // 사이클 시간 = (1분 동안 처리한 버퍼 수) / (초당 처리 속도) * 1000
        // 또는 간단히: buffers_clean이 많을수록 사이클이 빠르게 돌았다고 가정
        // 실제 사이클 시간은 pg_stat_statements나 다른 확장이 필요하므로,
        // 여기서는 buffers_clean 증분을 기반으로 추정
        Double avgCycleTimeMs = null;
        if (deltaBuffersClean > 0) {
            // 평균 사이클 시간 추정: 1분(60000ms) / (clean rate * 10)
            // clean rate가 높을수록 사이클이 빠르게 돌았으므로 시간이 짧아짐
            // 실제 계산은 복잡하므로 간단한 추정식 사용
            double estimatedCycles = Math.max(1.0, deltaBuffersClean / 1000.0); // 1000개 버퍼당 1 사이클 가정
            avgCycleTimeMs = 60000.0 / estimatedCycles; // 1분을 사이클 수로 나눔
            // 최대값 제한 (너무 긴 사이클 시간 방지)
            if (avgCycleTimeMs > 60000.0) {
                avgCycleTimeMs = 60000.0;
            }
        }

        // 상태 판단 (Backend Flush 비율 + Maxwritten Clean 고려)
        String status = determineBgWriterStatus(backendFlushRatio, deltaMaxwrittenClean);

        return BgWriterAgg1m.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgBackendFlushRatio(backendFlushRatio)
                .avgCleanRate(cleanRate)
                .totalBuffersClean(deltaBuffersClean)
                .totalBuffersBackend(deltaBuffersBackend)
                .totalBackendFsync(deltaBackendFsync)
                .totalMaxwrittenClean(deltaMaxwrittenClean)
                .status(status)
                .avgCycleTimeMs(avgCycleTimeMs)
                .build();
    }

    /**
     * 1분 집계 데이터를 기반으로 5분 집계 데이터 생성
     */
    private BgWriterAgg5m calculateAggregation5m(Long instanceId, OffsetDateTime collectedAt,
                                                 List<BgWriterAgg1m> agg1mList) {
        // 1분 집계 데이터들을 합산/평균 계산
        long totalBuffersClean = 0;
        long totalBuffersBackend = 0;
        long totalBackendFsync = 0;
        long totalMaxwrittenClean = 0;
        double sumBackendFlushRatio = 0.0;
        double sumCleanRate = 0.0;
        double sumCycleTimeMs = 0.0;
        int validDataCount = 0;  // 버퍼 활동이 있는 데이터만 카운트

        for (BgWriterAgg1m agg1m : agg1mList) {
            totalBuffersClean += (agg1m.getTotalBuffersClean() != null ? agg1m.getTotalBuffersClean() : 0);
            totalBuffersBackend += (agg1m.getTotalBuffersBackend() != null ? agg1m.getTotalBuffersBackend() : 0);
            totalBackendFsync += (agg1m.getTotalBackendFsync() != null ? agg1m.getTotalBackendFsync() : 0);
            totalMaxwrittenClean += (agg1m.getTotalMaxwrittenClean() != null ? agg1m.getTotalMaxwrittenClean() : 0);

            // 버퍼 활동이 있는 경우만 평균 계산에 포함
            long bufferActivity = (agg1m.getTotalBuffersClean() != null ? agg1m.getTotalBuffersClean() : 0)
                    + (agg1m.getTotalBuffersBackend() != null ? agg1m.getTotalBuffersBackend() : 0);

            if (bufferActivity > 0) {
                sumBackendFlushRatio += (agg1m.getAvgBackendFlushRatio() != null ? agg1m.getAvgBackendFlushRatio() : 0.0);
                sumCleanRate += (agg1m.getAvgCleanRate() != null ? agg1m.getAvgCleanRate() : 0.0);
                sumCycleTimeMs += (agg1m.getAvgCycleTimeMs() != null ? agg1m.getAvgCycleTimeMs() : 0.0);
                validDataCount++;
            }
        }

        // 평균 계산 (버퍼 활동이 있는 데이터만 평균)
        double avgBackendFlushRatio = validDataCount > 0 ? sumBackendFlushRatio / validDataCount : 0.0;
        double avgCleanRate = validDataCount > 0 ? sumCleanRate / validDataCount : 0.0;
        double avgCycleTimeMs = validDataCount > 0 ? sumCycleTimeMs / validDataCount : 0.0;

        // 상태 판단 (평균 Backend Flush 비율 + Maxwritten Clean 고려)
        String status = determineBgWriterStatus(avgBackendFlushRatio, totalMaxwrittenClean);

        return BgWriterAgg5m.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgBackendFlushRatio(avgBackendFlushRatio)
                .avgCleanRate(avgCleanRate)
                .totalBuffersClean(totalBuffersClean)
                .totalBuffersBackend(totalBuffersBackend)
                .totalBackendFsync(totalBackendFsync)
                .totalMaxwrittenClean(totalMaxwrittenClean)
                .status(status)
                .avgCycleTimeMs(avgCycleTimeMs)
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

    /**
     * 안전한 증분 계산 (Long 타입)
     * stats_reset 발생 시 음수가 나오면 현재 값을 반환
     */
    private long calculateSafeDelta(Long current, Long previous) {
        if (current == null || previous == null) {
            return 0L;
        }
        long delta = current - previous;
        return delta >= 0 ? delta : current;
    }

    /**
     * BGWriter 상태 판단
     * Backend Flush 비율과 Maxwritten Clean 발생 횟수를 고려하여 상태 결정
     */
    private String determineBgWriterStatus(double backendFlushRatio, long maxwrittenClean) {
        // Maxwritten Clean이 자주 발생하면 BGWriter가 제대로 작동하지 않는 것
        if (maxwrittenClean > 10) {
            return "위험";
        } else if (maxwrittenClean > 5) {
            return "주의";
        }

        // Backend Flush 비율이 높으면 BGWriter 성능 부족
        if (backendFlushRatio > 30.0) {
            return "위험";
        } else if (backendFlushRatio > 15.0) {
            return "주의";
        }

        return "정상";
    }
}

