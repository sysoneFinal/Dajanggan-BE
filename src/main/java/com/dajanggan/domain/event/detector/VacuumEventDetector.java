package com.dajanggan.domain.event.detector;

import com.dajanggan.domain.event.dto.*;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class VacuumEventDetector {

    // ========== Thresholds (요청해주신 값들) ==========
    // Autovacuum Worker Utilization (%)
    public static final double WORKER_UTIL_WARNING = 60.0;
    public static final double WORKER_UTIL_CRITICAL = 80.0;

    // Blockers per Hour (count)
    public static final int BLOCKERS_WARNING = 3;
    public static final int BLOCKERS_CRITICAL = 5;

    // Transaction Age (seconds)
    public static final long TX_AGE_WARNING = 1800L;   // 30분
    public static final long TX_AGE_CRITICAL = 7200L;  // 2시간

    // Block Duration (seconds)
    public static final long BLOCK_DURATION_WARNING = 300L;   // 5분
    public static final long BLOCK_DURATION_CRITICAL = 900L;  // 15분

    // Wraparound Progress (%)
    public static final double WRAPAROUND_WARNING = 50.0;
    public static final double WRAPAROUND_CRITICAL = 75.0;

    // Total Table Bloat (bytes)
    public static final long TOTAL_BLOAT_WARNING = 500L * 1024 * 1024;      // 500MB
    public static final long TOTAL_BLOAT_CRITICAL = 2L * 1024 * 1024 * 1024; // 2GB

    // Bloat % (percentage)
    public static final double BLOAT_PCT_WARNING = 10.0;
    public static final double BLOAT_PCT_CRITICAL = 20.0;

    // Dead Tuples per Table (count)
    public static final long DEAD_TUPLES_TABLE_WARNING = 50_000L;   // 50K
    public static final long DEAD_TUPLES_TABLE_CRITICAL = 200_000L; // 200K

    // Table Size (bytes) - 재사용: Total bloat 임계치 기준으로 사용(필요시 조정)
    public static final long TABLE_SIZE_WARNING = TOTAL_BLOAT_WARNING;
    public static final long TABLE_SIZE_CRITICAL = TOTAL_BLOAT_CRITICAL;


    /**
     * 지정된 지표들만 검사해서 이벤트를 생성합니다.
     *
     * @param metrics      테이블/인덱스 단위의 vacuum 지표 리스트
     * @param databaseId
     * @param instanceId
     * @param databaseName
     * @param instanceName
     * @return 이벤트 목록
     */
    public List<EventLog> detectEvents(List<VacuumRawMetricDto> metrics,
                                       Long databaseId, Long instanceId,
                                       String databaseName, String instanceName) {
        List<EventLog> events = new ArrayList<>();
        if (metrics == null || metrics.isEmpty()) {
            log.warn("⚠️ VacuumEventDetector: metrics가 null이거나 비어있습니다.");
            return events;
        }

        log.info("🔍 VacuumEventDetector: {}건의 메트릭 분석 시작", metrics.size());

        for (VacuumRawMetricDto m : metrics) {
            String table = Optional.ofNullable(m.getTableName()).orElse("알수없음");
            
            // 디버깅: 각 메트릭의 주요 값 로깅
            log.debug("📊 메트릭 분석: table={}, nDeadTup={}, bloatBytes={}, bloatRatio={}, " +
                    "transactionAge={}, blockedSeconds={}, isBlocked={}, wraparoundProgress={}, " +
                    "maxWorkers={}, activeWorkers={}, relsizeTotalBytes={}",
                    table, m.getNDeadTup(), m.getBloatBytes(), m.getBloatRatio(),
                    m.getTransactionAge(), m.getBlockedSeconds(), m.getIsBlocked(), m.getWraparoundProgress(),
                    m.getMaxWorkers(), m.getActiveWorkers(), m.getRelsizeTotalBytes());

            // Autovacuum Worker Utilization (계산 필요: activeWorkers / maxWorkers * 100)
            // duration은 퍼센트 값이므로 NULL로 설정
            if (m.getMaxWorkers() != null && m.getMaxWorkers() > 0 && m.getActiveWorkers() != null) {
                Double workerUtil = (m.getActiveWorkers().doubleValue() / m.getMaxWorkers().doubleValue()) * 100.0;
                EventLevel workerLevel = evaluateWorkerUtil(workerUtil);
                log.debug("🔍 Worker Utilization 평가: table={}, workerUtil={}%, level={}", table, workerUtil, workerLevel);
                if (workerLevel != null) {
                    events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                            EventType.Autovacuum_Worker_Utilization.name(), ResourceType.TABLE.name(),
                            null, workerLevel.name(), // duration은 NULL (퍼센트 값)
                            String.format("Autovacuum worker 활용도 경고: 현재 %.1f%% (테이블: %s)", workerUtil, table)));
                    log.info("🔍 Vacuum 이벤트 감지: Autovacuum_Worker_Utilization - {} (테이블: {})", workerLevel, table);
                }
            } else {
                log.debug("🔍 Worker Utilization 스킵: table={}, maxWorkers={}, activeWorkers={}", 
                        table, m.getMaxWorkers(), m.getActiveWorkers());
            }

            // Blockers Per Hour (isBlocked가 true인 경우)
            // duration은 카운트 값이므로 NULL로 설정
            if (Boolean.TRUE.equals(m.getIsBlocked())) {
                EventLevel blockersLevel = EventLevel.WARN; // 블로커가 있으면 경고
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Blockers_Per_Hour.name(), ResourceType.TABLE.name(),
                        null, blockersLevel.name(), // duration은 NULL (카운트 값)
                        String.format("과도한 차단(블로커) 감지: PID %d가 차단됨 (테이블: %s)", 
                                Optional.ofNullable(m.getBlockerPid()).orElse(0), table)));
                log.info("🔍 Vacuum 이벤트 감지: Blockers_Per_Hour - {} (테이블: {})", blockersLevel, table);
            }

            // Transaction Age
            EventLevel txAgeLevel = evaluateTxAge(m.getTransactionAge());
            if (txAgeLevel != null) {
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Transaction_Age.name(), ResourceType.SESSION.name(),
                        (m.getTransactionAge() != null) ? m.getTransactionAge().doubleValue() : null, txAgeLevel.name(),
                        String.format("장시간 트랜잭션 감지: 트랜잭션 지속시간 %s (테이블: %s)", formatSecondsHMS(m.getTransactionAge()), table)));
                log.info("🔍 Vacuum 이벤트 감지: Transaction_Age - {} (테이블: {})", txAgeLevel, table);
            }

            // Block Duration
            EventLevel blockDurLevel = evaluateBlockDuration(m.getBlockedSeconds() != null ? m.getBlockedSeconds().longValue() : null);
            if (blockDurLevel != null) {
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Block_Duration.name(), ResourceType.TABLE.name(),
                        (m.getBlockedSeconds() != null) ? m.getBlockedSeconds().doubleValue() : null, blockDurLevel.name(),
                        String.format("블록(락) 지속시간 경고: 블록 시간 %s (테이블: %s)", formatSecondsHMS(m.getBlockedSeconds() != null ? m.getBlockedSeconds().longValue() : null), table)));
                log.info("🔍 Vacuum 이벤트 감지: Block_Duration - {} (테이블: {})", blockDurLevel, table);
            }

            // Wraparound Progress
            // duration은 퍼센트 값이므로 NULL로 설정
            EventLevel wrapLevel = evaluateWraparound(m.getWraparoundProgress());
            if (wrapLevel != null) {
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Wraparound_Progress.name(), ResourceType.TABLE.name(),
                        null, wrapLevel.name(), // duration은 NULL (퍼센트 값)
                        String.format("Wraparound 진행률 경고: 현재 %.1f%% (테이블: %s)", safeDouble(m.getWraparoundProgress()), table)));
                log.info("🔍 Vacuum 이벤트 감지: Wraparound_Progress - {} (테이블: {})", wrapLevel, table);
            }

            // Total Table Bloat
            // duration은 바이트 값이므로 NULL로 설정 (바이트는 매우 큰 값일 수 있음)
            EventLevel totalBloatLevel = evaluateTotalTableBloat(m.getBloatBytes());
            log.debug("🔍 Total Table Bloat 평가: table={}, bloatBytes={}, level={}, threshold(WARN={}, CRITICAL={})", 
                    table, m.getBloatBytes(), totalBloatLevel, TOTAL_BLOAT_WARNING, TOTAL_BLOAT_CRITICAL);
            if (totalBloatLevel != null) {
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Total_Table_Bloat.name(), ResourceType.TABLE.name(),
                        null, totalBloatLevel.name(), // duration은 NULL (바이트 값)
                        String.format("테이블 전체 Bloat 경고: 총 낭비 공간 %s (테이블: %s)", humanReadableBytes(m.getBloatBytes()), table)));
                log.info("🔍 Vacuum 이벤트 감지: Total_Table_Bloat - {} (테이블: {})", totalBloatLevel, table);
            }

            // Bloat Percent
            // duration은 퍼센트 값이므로 NULL로 설정
            Double bloatRatioPercent = (m.getBloatRatio() != null) ? m.getBloatRatio() * 100.0 : null;
            EventLevel bloatPctLevel = evaluateBloatPct(m.getBloatRatio());
            log.debug("🔍 Bloat Percent 평가: table={}, bloatRatio={}, bloatRatioPercent={}%, level={}, threshold(WARN={}%, CRITICAL={}%)", 
                    table, m.getBloatRatio(), bloatRatioPercent, bloatPctLevel, BLOAT_PCT_WARNING, BLOAT_PCT_CRITICAL);
            if (bloatPctLevel != null) {
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Bloat_Percent.name(), ResourceType.TABLE.name(),
                        null, bloatPctLevel.name(), // duration은 NULL (퍼센트 값)
                        String.format("테이블 Bloat 비율 경고: 현재 %.2f%% (테이블: %s)", safeDouble(bloatRatioPercent), table)));
                log.info("🔍 Vacuum 이벤트 감지: Bloat_Percent - {} (테이블: {})", bloatPctLevel, table);
            }

            // Dead Tuples (per table)
            // duration은 카운트 값이므로 NULL로 설정
            EventLevel deadTableLevel = evaluateDeadTuplesTable(m.getNDeadTup());
            log.debug("🔍 Dead Tuples 평가: table={}, nDeadTup={}, level={}", table, m.getNDeadTup(), deadTableLevel);
            if (deadTableLevel != null) {
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Dead_Tuples.name(), ResourceType.TABLE.name(),
                        null, deadTableLevel.name(), // duration은 NULL (카운트 값)
                        String.format("테이블 내 Dead Tuples 과다: %,d개 감지 (테이블: %s)", safeLong(m.getNDeadTup()), table)));
                log.info("🔍 Vacuum 이벤트 감지: Dead_Tuples - {} (테이블: {})", deadTableLevel, table);
            }

            // Table Size
            // duration은 바이트 값이므로 NULL로 설정 (바이트는 매우 큰 값일 수 있음)
            EventLevel tableSizeLevel = evaluateTableSize(m.getRelsizeTotalBytes());
            if (tableSizeLevel != null) {
                events.add(buildEvent(m, databaseId, instanceId, databaseName, instanceName,
                        EventType.Table_Size.name(), ResourceType.TABLE.name(),
                        null, tableSizeLevel.name(), // duration은 NULL (바이트 값)
                        String.format("테이블 크기 경고: 현재 크기 %s (테이블: %s)", humanReadableBytes(m.getRelsizeTotalBytes()), table)));
                log.info("🔍 Vacuum 이벤트 감지: Table_Size - {} (테이블: {})", tableSizeLevel, table);
            }
        }

        log.info("✅ VacuumEventDetector: 감지된 이벤트 수 = {} / {}건 메트릭 분석 (instance: {}, database: {})", 
                events.size(), metrics.size(), instanceName, databaseName);
        
        if (events.isEmpty() && !metrics.isEmpty()) {
            log.info("ℹ️ 이벤트가 감지되지 않았습니다. 모든 메트릭이 정상 범위 내에 있거나, " +
                    "필요한 필드 값이 null일 수 있습니다. DEBUG 레벨 로그를 확인하세요.");
        }
        
        return events;
    }

    // ---------------- Evaluation helpers ----------------

    private EventLevel evaluateWorkerUtil(Double pct) {
        if (pct == null) return null;
        if (pct >= WORKER_UTIL_CRITICAL) return EventLevel.CRITICAL;
        if (pct >= WORKER_UTIL_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateBlockersPerHour(Integer cnt) {
        if (cnt == null) return null;
        if (cnt >= BLOCKERS_CRITICAL) return EventLevel.CRITICAL;
        if (cnt >= BLOCKERS_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateTxAge(Long sec) {
        if (sec == null) return null;
        if (sec >= TX_AGE_CRITICAL) return EventLevel.CRITICAL;
        if (sec >= TX_AGE_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateBlockDuration(Long sec) {
        if (sec == null) return null;
        if (sec >= BLOCK_DURATION_CRITICAL) return EventLevel.CRITICAL;
        if (sec >= BLOCK_DURATION_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateWraparound(Double pct) {
        if (pct == null) return null;
        if (pct >= WRAPAROUND_CRITICAL) return EventLevel.CRITICAL;
        if (pct >= WRAPAROUND_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateTotalTableBloat(Long bytes) {
        if (bytes == null) return null;
        if (bytes >= TOTAL_BLOAT_CRITICAL) return EventLevel.CRITICAL;
        if (bytes >= TOTAL_BLOAT_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateBloatPct(Double pct) {
        if (pct == null) return null;
        if (pct >= BLOAT_PCT_CRITICAL) return EventLevel.CRITICAL;
        if (pct >= BLOAT_PCT_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateDeadTuplesTable(Long cnt) {
        if (cnt == null) return null;
        if (cnt >= DEAD_TUPLES_TABLE_CRITICAL) return EventLevel.CRITICAL;
        if (cnt >= DEAD_TUPLES_TABLE_WARNING) return EventLevel.WARN;
        return null;
    }

    private EventLevel evaluateTableSize(Long bytes) {
        if (bytes == null) return null;
        if (bytes >= TABLE_SIZE_CRITICAL) return EventLevel.CRITICAL;
        if (bytes >= TABLE_SIZE_WARNING) return EventLevel.WARN;
        return null;
    }

    // ---------------- Event 빌더 ----------------

    /** 개별 메트릭 이벤트 빌더 */
    private EventLog buildEvent(VacuumRawMetricDto dto, Long databaseId, Long instanceId,
                                String databaseName, String instanceName, String eventType,
                                String resourceType, Double duration, String level, String description) {
        return EventLog.builder()
                .instanceId(instanceId)
                .databaseId(databaseId)
                .instanceName(instanceName)
                .databaseName(databaseName)
                .category(EventCategory.VACUUM.name())
                .eventType(eventType)
                .level(level)
                .userName(null)
                .resourceType(resourceType)
                .detectedAt(OffsetDateTime.now())
                .duration(duration)
                .description(description)
                .build();
    }

    // ---------------- 유틸 ----------------

    private double safeDouble(Double d) { return d == null ? 0.0 : d; }
    private long safeLong(Long l) { return l == null ? 0L : l; }
    private int safeInt(Integer i) { return i == null ? 0 : i; }

    private String humanReadableBytes(Long bytes) {
        if (bytes == null) return "0 B";
        long b = bytes;
        if (b < 1024) return b + " B";
        int exp = (int) (Math.log(b) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", b / Math.pow(1024, exp), pre);
    }

    private String formatSecondsHMS(Long seconds) {
        if (seconds == null) return "0초";
        long s = seconds;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format("%dh %dm %ds", h, m, sec);
        if (m > 0) return String.format("%dm %ds", m, sec);
        return String.format("%ds", sec);
    }
}

