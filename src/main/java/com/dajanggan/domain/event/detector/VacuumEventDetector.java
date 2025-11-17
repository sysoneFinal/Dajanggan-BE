//package com.dajanggan.domain.event.detector;
//
//import com.dajanggan.domain.event.dto.EventLog;
//import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto; // 실제 경로에 맞게 수정
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.time.OffsetDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//
//@Slf4j
//@Component
//public class VacuumEventDetector {
//
//    // ========== Thresholds (요청해주신 값들) ==========
//    // Autovacuum Worker Utilization (%)
//    public static final double WORKER_UTIL_WARNING = 60.0;
//    public static final double WORKER_UTIL_CRITICAL = 80.0;
//
//    // Blockers per Hour (count)
//    public static final int BLOCKERS_WARNING = 3;
//    public static final int BLOCKERS_CRITICAL = 5;
//
//    // Transaction Age (seconds)
//    public static final long TX_AGE_WARNING = 1800L;   // 30분
//    public static final long TX_AGE_CRITICAL = 7200L;  // 2시간
//
//    // Block Duration (seconds)
//    public static final long BLOCK_DURATION_WARNING = 300L;   // 5분
//    public static final long BLOCK_DURATION_CRITICAL = 900L;  // 15분
//
//    // Wraparound Progress (%)
//    public static final double WRAPAROUND_WARNING = 50.0;
//    public static final double WRAPAROUND_CRITICAL = 75.0;
//
//    // Total Table Bloat (bytes)
//    public static final long TOTAL_BLOAT_WARNING = 500L * 1024 * 1024;      // 500MB
//    public static final long TOTAL_BLOAT_CRITICAL = 2L * 1024 * 1024 * 1024; // 2GB
//
//    // Bloat % (percentage)
//    public static final double BLOAT_PCT_WARNING = 10.0;
//    public static final double BLOAT_PCT_CRITICAL = 20.0;
//
//    // Dead Tuples per Table (count)
//    public static final long DEAD_TUPLES_TABLE_WARNING = 50_000L;   // 50K
//    public static final long DEAD_TUPLES_TABLE_CRITICAL = 200_000L; // 200K
//
//    // Table Size (bytes) - 재사용: Total bloat 임계치 기준으로 사용(필요시 조정)
//    public static final long TABLE_SIZE_WARNING = TOTAL_BLOAT_WARNING;
//    public static final long TABLE_SIZE_CRITICAL = TOTAL_BLOAT_CRITICAL;
//
//    private enum ThresholdStatus { NORMAL, WARNING, CRITICAL }
//
//    /**
//     * 지정된 지표들만 검사해서 이벤트를 생성합니다.
//     *
//     * @param metrics      테이블/인덱스 단위의 vacuum 지표 리스트
//     * @param databaseId
//     * @param instanceId
//     * @param databaseName
//     * @param instanceName
//     * @return 이벤트 목록
//     */
//    public List<EventLog> detectEvents(List<VacuumRawMetricDto> metrics,
//                                       Long databaseId, Long instanceId,
//                                       String databaseName, String instanceName) {
//        List<EventLog> events = new ArrayList<>();
//        if (metrics == null || metrics.isEmpty()) return events;
//
//        for (VacuumRawMetricDto m : metrics) {
//            String table = Optional.ofNullable(m.getTableName()).orElse("알수없음");
//
//            // Autovacuum Worker Utilization
//            ThresholdStatus workerStatus = evaluateWorkerUtil(m.getAutovacuumWorkerUtilPercent());
//            if (workerStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Autovacuum_Worker_Utilization", "INSTANCE", workerStatus,
//                        String.format("Autovacuum worker 활용도 경고: 현재 %.1f%% (테이블: %s)", safeDouble(m.getAutovacuumWorkerUtilPercent()), table),
//                        m.getAutovacuumWorkerUtilPercent()));
//            }
//
//            // Blockers Per Hour
//            ThresholdStatus blockersStatus = evaluateBlockersPerHour(m.getBlockersPerHour());
//            if (blockersStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Blockers_Per_Hour", "TABLE", blockersStatus,
//                        String.format("과도한 차단(블로커) 감지: %d회/시간 (테이블: %s)", safeInt(m.getBlockersPerHour()), table),
//                        (m.getBlockersPerHour() != null) ? (double) m.getBlockersPerHour() : null));
//            }
//
//            // Transaction Age
//            ThresholdStatus txAgeStatus = evaluateTxAge(m.getTransactionAgeSec());
//            if (txAgeStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Transaction_Age", "SESSION", txAgeStatus,
//                        String.format("장시간 트랜잭션 감지: 트랜잭션 지속시간 %s (테이블: %s)", formatSecondsHMS(m.getTransactionAgeSec()), table),
//                        (m.getTransactionAgeSec() != null) ? m.getTransactionAgeSec().doubleValue() : null));
//            }
//
//            // Block Duration
//            ThresholdStatus blockDurStatus = evaluateBlockDuration(m.getBlockDurationSec());
//            if (blockDurStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Block_Duration", "TABLE", blockDurStatus,
//                        String.format("블록(락) 지속시간 경고: 블록 시간 %s (테이블: %s)", formatSecondsHMS(m.getBlockDurationSec()), table),
//                        (m.getBlockDurationSec() != null) ? m.getBlockDurationSec().doubleValue() : null));
//            }
//
//            // Wraparound Progress
//            ThresholdStatus wrapStatus = evaluateWraparound(m.getWraparoundProgressPct());
//            if (wrapStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Wraparound_Progress", "DATABASE", wrapStatus,
//                        String.format("Wraparound 진행률 경고: 현재 %.1f%% (테이블: %s)", safeDouble(m.getWraparoundProgressPct()), table),
//                        m.getWraparoundProgressPct()));
//            }
//
//            // Total Table Bloat
//            ThresholdStatus totalBloatStatus = evaluateTotalTableBloat(m.getTotalTableBloatBytes());
//            if (totalBloatStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Total_Table_Bloat", "TABLE", totalBloatStatus,
//                        String.format("테이블 전체 Bloat 경고: 총 낭비 공간 %s (테이블: %s)", humanReadableBytes(m.getTotalTableBloatBytes()), table),
//                        (m.getTotalTableBloatBytes() != null) ? (double) m.getTotalTableBloatBytes() : null));
//            }
//
//            // Bloat Percent
//            ThresholdStatus bloatPctStatus = evaluateBloatPct(m.getBloatPercent());
//            if (bloatPctStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Bloat_Percent", "TABLE", bloatPctStatus,
//                        String.format("테이블 Bloat 비율 경고: 현재 %.2f%% (테이블: %s)", safeDouble(m.getBloatPercent()), table),
//                        m.getBloatPercent()));
//            }
//
//            // Dead Tuples (per table)
//            ThresholdStatus deadTableStatus = evaluateDeadTuplesTable(m.getDeadTuplesTable());
//            if (deadTableStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Dead_Tuples", "TABLE", deadTableStatus,
//                        String.format("테이블 내 Dead Tuples 과다: %,d개 감지 (테이블: %s)", safeLong(m.getDeadTuplesTable()), table),
//                        (m.getDeadTuplesTable() != null) ? m.getDeadTuplesTable().doubleValue() : null));
//            }
//
//            // Table Size
//            ThresholdStatus tableSizeStatus = evaluateTableSize(m.getTableSizeBytes());
//            if (tableSizeStatus != ThresholdStatus.NORMAL) {
//                events.add(buildMetricEvent(m, databaseId, instanceId, databaseName, instanceName,
//                        "Table_Size", "TABLE", tableSizeStatus,
//                        String.format("테이블 크기 경고: 현재 크기 %s (테이블: %s)", humanReadableBytes(m.getTableSizeBytes()), table),
//                        (m.getTableSizeBytes() != null) ? (double) m.getTableSizeBytes() : null));
//            }
//        }
//
//        log.info("VacuumEventDetector (간소화): 감지된 이벤트 수 = {}", events.size());
//        return events;
//    }
//
//    // ---------------- Evaluation helpers ----------------
//
//    private ThresholdStatus evaluateWorkerUtil(Double pct) {
//        if (pct == null) return ThresholdStatus.NORMAL;
//        if (pct >= WORKER_UTIL_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (pct >= WORKER_UTIL_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateBlockersPerHour(Integer cnt) {
//        if (cnt == null) return ThresholdStatus.NORMAL;
//        if (cnt >= BLOCKERS_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (cnt >= BLOCKERS_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateTxAge(Long sec) {
//        if (sec == null) return ThresholdStatus.NORMAL;
//        if (sec >= TX_AGE_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (sec >= TX_AGE_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateBlockDuration(Long sec) {
//        if (sec == null) return ThresholdStatus.NORMAL;
//        if (sec >= BLOCK_DURATION_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (sec >= BLOCK_DURATION_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateWraparound(Double pct) {
//        if (pct == null) return ThresholdStatus.NORMAL;
//        if (pct >= WRAPAROUND_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (pct >= WRAPAROUND_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateTotalTableBloat(Long bytes) {
//        if (bytes == null) return ThresholdStatus.NORMAL;
//        if (bytes >= TOTAL_BLOAT_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (bytes >= TOTAL_BLOAT_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateBloatPct(Double pct) {
//        if (pct == null) return ThresholdStatus.NORMAL;
//        if (pct >= BLOAT_PCT_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (pct >= BLOAT_PCT_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateDeadTuplesTable(Long cnt) {
//        if (cnt == null) return ThresholdStatus.NORMAL;
//        if (cnt >= DEAD_TUPLES_TABLE_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (cnt >= DEAD_TUPLES_TABLE_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    private ThresholdStatus evaluateTableSize(Long bytes) {
//        if (bytes == null) return ThresholdStatus.NORMAL;
//        if (bytes >= TABLE_SIZE_CRITICAL) return ThresholdStatus.CRITICAL;
//        if (bytes >= TABLE_SIZE_WARNING) return ThresholdStatus.WARNING;
//        return ThresholdStatus.NORMAL;
//    }
//
//    // ---------------- Event 빌더 (한국어 설명) ----------------
//
//    private EventLog buildMetricEvent(VacuumRawMetricDto dto, Long databaseId, Long instanceId,
//                                      String databaseName, String instanceName,
//                                      String eventType, String resourceType,
//                                      ThresholdStatus status, String description, Double duration) {
//        String level = mapStatusToLevel(status);
//        EventLog.EventLogBuilder builder = EventLog.builder()
//                .instanceId(instanceId)
//                .databaseId(databaseId)
//                .instanceName(instanceName)
//                .databaseName(databaseName)
//                .category("VACUUM")
//                .eventType(eventType)
//                .level(level)
//                .userName(null)
//                .resourceType(resourceType)
//                .detectedAt(OffsetDateTime.now())
//                .description(description);
//
//        if (duration != null) builder.duration(duration);
//        if (dto != null && dto.getTableName() != null) builder.resourceName(dto.getTableName());
//
//        return builder.build();
//    }
//
//    private String mapStatusToLevel(ThresholdStatus status) {
//        switch (status) {
//            case CRITICAL: return "CRITICAL";
//            case WARNING:  return "WARNING";
//            default:       return "INFO";
//        }
//    }
//
//    // ---------------- 유틸 ----------------
//
//    private double safeDouble(Double d) { return d == null ? 0.0 : d; }
//    private long safeLong(Long l) { return l == null ? 0L : l; }
//    private int safeInt(Integer i) { return i == null ? 0 : i; }
//
//    private String humanReadableBytes(Long bytes) {
//        if (bytes == null) return "0 B";
//        long b = bytes;
//        if (b < 1024) return b + " B";
//        int exp = (int) (Math.log(b) / Math.log(1024));
//        String pre = "KMGTPE".charAt(exp - 1) + "";
//        return String.format("%.1f %sB", b / Math.pow(1024, exp), pre);
//    }
//
//    private String formatSecondsHMS(Long seconds) {
//        if (seconds == null) return "0초";
//        long s = seconds;
//        long h = s / 3600;
//        long m = (s % 3600) / 60;
//        long sec = s % 60;
//        if (h > 0) return String.format("%dh %dm %ds", h, m, sec);
//        if (m > 0) return String.format("%dm %ds", m, sec);
//        return String.format("%ds", sec);
//    }
//}
//
