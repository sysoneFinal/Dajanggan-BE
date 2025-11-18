package com.dajanggan.domain.engine.hotindex.service;

import com.dajanggan.domain.engine.hotindex.dto.*;
import com.dajanggan.domain.engine.hotindex.repository.HotIndexMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotIndexService {

    private final HotIndexMapper hotIndexMapper;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * HotIndex 대시보드 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return HotIndex 대시보드 데이터
     */
    public HotIndexDashboardResponse getHotIndexDashboard(Long instanceId, Long databaseId) {
        log.info("HotIndex 대시보드 조회 시작 - instanceId: {}, databaseId: {}", instanceId, databaseId);

        // instanceId와 databaseId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }
        if (databaseId == null) {
            log.error("databaseId가 필수입니다");
            throw new IllegalArgumentException("databaseId는 필수 파라미터입니다");
        }

        // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);

        return HotIndexDashboardResponse.builder()
                .usageDistribution(getUsageDistribution(instanceId, databaseId))
                .topUsage(getTopUsage(instanceId, databaseId))
                .inefficientIndexes(getInefficientIndexes(instanceId, databaseId))
                // 캐시 히트율 (최근 15분)
                .cacheHitRatio(getCacheHitRatio(instanceId, databaseId, endTime.minusMinutes(15), endTime))
                .efficiency(getEfficiency(instanceId, databaseId))
                // 접근 추이 (최근 10분)
                .accessTrend(getAccessTrend(instanceId, databaseId, endTime.minusMinutes(10), endTime))
                // 스캔 속도 (최근 10분)
                .scanSpeed(getScanSpeed(instanceId, databaseId, endTime.minusMinutes(10), endTime))
                .recentStats(getRecentStats(instanceId, databaseId))
                .build();
    }

    /**
     * 인덱스 사용 분포 조회
     */
    private HotIndexDashboardResponse.UsageDistribution getUsageDistribution(Long instanceId, Long databaseId) {
        Map<String, Object> data = hotIndexMapper.selectUsageDistribution(instanceId, databaseId);

        // 데이터가 없을 경우 기본값 반환
        if (data == null || data.isEmpty()) {
            log.warn("UsageDistribution 데이터 없음 - instanceId: {}, databaseId: {}", instanceId, databaseId);
            return HotIndexDashboardResponse.UsageDistribution.builder()
                    .categories(List.of("사용 중", "미사용", "비효율"))
                    .data(List.of(0L, 0L, 0L))
                    .build();
        }

        List<String> categories = List.of("사용 중", "미사용", "비효율");
        List<Long> values = List.of(
                getLongValue(data.get("used_count")),
                getLongValue(data.get("unused_count")),
                getLongValue(data.get("inefficient_count"))
        );

        return HotIndexDashboardResponse.UsageDistribution.builder()
                .categories(categories)
                .data(values)
                .build();
    }

    /**
     * Top 사용 인덱스 조회
     */
    private HotIndexDashboardResponse.TopUsage getTopUsage(Long instanceId, Long databaseId) {
        List<Map<String, Object>> dataList = hotIndexMapper.selectTopUsageIndexes(instanceId, databaseId);

        List<String> categories = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        long total = 0;

        for (Map<String, Object> row : dataList) {
            String indexName = (String) row.get("index_name");
            Long scanCount = getLongValue(row.get("idx_scan"));

            categories.add(indexName);
            data.add(scanCount);
            total += scanCount;
        }

        return HotIndexDashboardResponse.TopUsage.builder()
                .categories(categories)
                .data(data)
                .total(total)
                .build();
    }

    /**
     * 비효율 인덱스 조회
     */
    private HotIndexDashboardResponse.InefficientIndexes getInefficientIndexes(Long instanceId, Long databaseId) {
        List<Map<String, Object>> dataList = hotIndexMapper.selectInefficientIndexes(instanceId, databaseId);

        List<String> categories = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        for (Map<String, Object> row : dataList) {
            String indexName = (String) row.get("index_name");
            Double inefficiency = getDoubleValue(row.get("inefficiency_score"));

            categories.add(indexName);
            data.add(inefficiency);
        }

        return HotIndexDashboardResponse.InefficientIndexes.builder()
                .categories(categories)
                .data(data)
                .total((long) dataList.size())
                .build();
    }

    /**
     * 캐시 히트율 시계열 데이터 조회
     */
    private HotIndexDashboardResponse.CacheHitRatio getCacheHitRatio(Long instanceId, Long databaseId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = hotIndexMapper.selectCacheHitRatioTimeSeries(instanceId, databaseId, startTime, endTime);

        List<String> categories = new ArrayList<>();
        List<Double> ratios = new ArrayList<>();

        for (Map<String, Object> row : dataList) {
            categories.add(formatTimestamp(row.get("timestamp")));
            ratios.add(getDoubleValue(row.get("cache_hit_ratio")));
        }

        Double average = ratios.isEmpty() ? 0.0 : ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Double max = ratios.isEmpty() ? 0.0 : ratios.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        Double min = ratios.isEmpty() ? 0.0 : ratios.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return HotIndexDashboardResponse.CacheHitRatio.builder()
                .categories(categories)
                .data(ratios)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 인덱스 효율성 데이터 조회
     */
    private HotIndexDashboardResponse.Efficiency getEfficiency(Long instanceId, Long databaseId) {
        List<Map<String, Object>> dataList = hotIndexMapper.selectIndexEfficiency(instanceId, databaseId);

        List<String> categories = new ArrayList<>();
        List<HotIndexDashboardResponse.IndexEfficiency> indexes = new ArrayList<>();

        for (Map<String, Object> row : dataList) {
            String indexName = (String) row.get("index_name");
            Long idxScan = getLongValue(row.get("idx_scan"));
            Double efficiency = getDoubleValue(row.get("efficiency"));

            categories.add(indexName);
            indexes.add(HotIndexDashboardResponse.IndexEfficiency.builder()
                    .x(idxScan)
                    .y(efficiency)
                    .name(indexName)
                    .build());
        }

        return HotIndexDashboardResponse.Efficiency.builder()
                .categories(categories)
                .indexes(indexes)
                .build();
    }

    /**
     * 인덱스 접근 추이 조회
     */
    private HotIndexDashboardResponse.AccessTrend getAccessTrend(Long instanceId, Long databaseId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = hotIndexMapper.selectAccessTrendTimeSeries(instanceId, databaseId, startTime, endTime);

        List<String> categories = new ArrayList<>();
        List<Long> reads = new ArrayList<>();
        List<Long> writes = new ArrayList<>();
        long totalReads = 0;
        long totalWrites = 0;

        for (Map<String, Object> row : dataList) {
            categories.add(formatTimestamp(row.get("timestamp")));

            Long readCount = getLongValue(row.get("idx_tup_read"));
            Long writeCount = getLongValue(row.get("idx_tup_fetch")); // 임시로 fetch를 write로 사용

            reads.add(readCount);
            writes.add(writeCount);
            totalReads += readCount;
            totalWrites += writeCount;
        }

        return HotIndexDashboardResponse.AccessTrend.builder()
                .categories(categories)
                .reads(reads)
                .writes(writes)
                .totalReads(totalReads)
                .totalWrites(totalWrites)
                .build();
    }

    /**
     * 인덱스 스캔 속도 추이 조회
     */
    private HotIndexDashboardResponse.ScanSpeed getScanSpeed(Long instanceId, Long databaseId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = hotIndexMapper.selectScanSpeedTimeSeries(instanceId, databaseId, startTime, endTime);

        List<String> categories = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();

        for (Map<String, Object> row : dataList) {
            categories.add(formatTimestamp(row.get("timestamp")));
            speeds.add(getDoubleValue(row.get("avg_scan_time_ms")));
        }

        Double average = speeds.isEmpty() ? 0.0 : speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Double max = speeds.isEmpty() ? 0.0 : speeds.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        Double min = speeds.isEmpty() ? 0.0 : speeds.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return HotIndexDashboardResponse.ScanSpeed.builder()
                .categories(categories)
                .data(speeds)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 최근 통계 조회
     */
    private HotIndexDashboardResponse.RecentStats getRecentStats(Long instanceId, Long databaseId) {
        Map<String, Object> data = hotIndexMapper.selectRecentStats(instanceId, databaseId);

        return HotIndexDashboardResponse.RecentStats.builder()
                .cacheHitRatio(getDoubleValue(data.get("cache_hit_ratio")))
                .avgScanSpeed(getDoubleValue(data.get("avg_scan_speed")))
                .totalReads(getLongValue(data.get("total_reads")))
                .totalWrites(getLongValue(data.get("total_writes")))
                .inefficientCount(getLongValue(data.get("inefficient_count")))
                .build();
    }

    /**
     * HotIndex 리스트 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param timeRange 시간 범위
     * @param statusList 상태 필터 리스트
     * @return HotIndex 리스트 데이터
     */
    public HotIndexListResponse getHotIndexList(Long instanceId, Long databaseId, String timeRange, List<String> statusList) {
        log.info("HotIndex 리스트 조회 시작 - instanceId: {}, databaseId: {}, timeRange: {}, statusList: {}",
                instanceId, databaseId, timeRange, statusList);

        // instanceId와 databaseId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }
        if (databaseId == null) {
            log.error("databaseId가 필수입니다");
            throw new IllegalArgumentException("databaseId는 필수 파라미터입니다");
        }

        // 시간 범위 계산
        // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);
        LocalDateTime startTime = calculateStartTime(endTime, timeRange);

        // 데이터 조회
        List<Map<String, Object>> dataList = hotIndexMapper.selectHotIndexList(
                instanceId, databaseId, startTime, endTime, statusList);

        // DTO 변환
        List<HotIndexListItem> items = dataList.stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());

        return HotIndexListResponse.builder()
                .data(items)
                .total((long) items.size())
                .build();
    }

    /**
     * Map을 ListItem으로 변환
     */
    private HotIndexListItem convertToListItem(Map<String, Object> row) {
        return HotIndexListItem.builder()
                .id(String.valueOf(row.get("hot_index_raw_id")))
                .indexName((String) row.get("index_name"))
                .tableName((String) row.get("table_name"))
                .schemaName((String) row.get("schema_name"))
                .indexType((String) row.get("index_type"))
                .size(formatSize(getLongValue(row.get("index_size"))))
                .idxScan(getLongValue(row.get("idx_scan")))
                .idxTupRead(getLongValue(row.get("idx_tup_read")))
                .idxTupFetch(getLongValue(row.get("idx_tup_fetch")))
                .cacheHit(calculateCacheHitRatio(row))
                .bloatPercent(getDoubleValue(row.get("bloat_percent")))
                .avgScanTime(getDoubleValue(row.get("avg_scan_time_ms")))
                .lastUsed(formatTimestamp(row.get("last_idx_scan")))
                .status((String) row.get("status"))  // DB에서 계산된 status 사용
                .build();
    }

    /**
     * 시작 시간 계산
     */
    private LocalDateTime calculateStartTime(LocalDateTime endTime, String timeRange) {
        return switch (timeRange) {
            case "1h" -> endTime.minusHours(1);
            case "6h" -> endTime.minusHours(6);
            case "24h" -> endTime.minusHours(24);
            case "7d" -> endTime.minusDays(7);
            default -> endTime.minusDays(7);
        };
    }

    /**
     * 크기 포맷팅 (bytes -> MB, GB)
     */
    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";

        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;

        if (gb >= 1) {
            return String.format("%.2f GB", gb);
        } else if (mb >= 1) {
            return String.format("%.2f MB", mb);
        } else if (kb >= 1) {
            return String.format("%.2f KB", kb);
        } else {
            return bytes + " B";
        }
    }

    /**
     * 캐시 히트율 계산
     */
    private Double calculateCacheHitRatio(Map<String, Object> row) {
        Long idxBlksRead = getLongValue(row.get("idx_blks_read"));
        Long idxBlksHit = getLongValue(row.get("idx_blks_hit"));

        if (idxBlksRead + idxBlksHit == 0) {
            return 0.0;
        }

        return (idxBlksHit * 100.0) / (idxBlksRead + idxBlksHit);
    }

    /**
     * 타임스탬프 포맷팅
     */
    private String formatTimestamp(Object timestamp) {
        // HH:mm 형식으로 통일 (Mapper에서 이미 포맷팅된 문자열을 반환할 수 있음)
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof String) {
            return (String) timestamp; // 이미 포맷팅된 문자열
        }
        if (timestamp instanceof LocalDateTime) {
            return ((LocalDateTime) timestamp).format(TIME_FORMATTER);
        }
        return timestamp.toString();
    }

    /**
     * Long 값 안전하게 가져오기
     */
    private Long getLongValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Double 값 안전하게 가져오기
     */
    private Double getDoubleValue(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}