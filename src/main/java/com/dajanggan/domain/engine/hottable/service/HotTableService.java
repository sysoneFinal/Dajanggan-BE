package com.dajanggan.domain.engine.hottable.service;

import com.dajanggan.domain.engine.hottable.dto.HotTableDto;
import com.dajanggan.domain.engine.hottable.repository.HotTableMapper;
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
public class HotTableService {

    private final HotTableMapper hotTableMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * HotTable 대시보드 데이터 조회
     * @param databaseId 데이터베이스 ID
     * @return HotTable 대시보드 데이터
     */
    public HotTableDto.DashboardResponse getHotTableDashboard(Long databaseId) {
        log.info("HotTable 대시보드 조회 시작 - databaseId: {}", databaseId);

        // 기본값 설정
        if (databaseId == null) {
            databaseId = 1L; // 기본 데이터베이스 ID
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24); // 최근 24시간

        return HotTableDto.DashboardResponse.builder()
                .topTables(getTopTables(databaseId))
                .tableActivity(getTableActivity(databaseId, startTime, endTime))
                .cacheHitRatio(getCacheHitRatio(databaseId, startTime, endTime))
                .bloatStatus(getBloatStatus(databaseId))
                .vacuumStatus(getVacuumStatus(databaseId))
                .recentStats(getRecentStats(databaseId))
                .build();
    }

    /**
     * Top 테이블 정보 조회
     */
    private HotTableDto.TopTables getTopTables(Long databaseId) {
        List<Map<String, Object>> topBySize = hotTableMapper.selectTopTablesBySize(databaseId);
        List<Map<String, Object>> topByScan = hotTableMapper.selectTopTablesByScan(databaseId);
        List<Map<String, Object>> topByBloat = hotTableMapper.selectTopTablesByBloat(databaseId);

        return HotTableDto.TopTables.builder()
                .topBySize(convertToTableSummary(topBySize))
                .topByScan(convertToTableSummary(topByScan))
                .topByBloat(convertToTableSummary(topByBloat))
                .build();
    }

    /**
     * 테이블 활동 시계열 데이터 조회
     */
    private HotTableDto.TableActivity getTableActivity(Long databaseId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> data = hotTableMapper.selectTableActivityTimeSeries(databaseId, startTime, endTime);

        List<String> categories = new ArrayList<>();
        List<Long> seqScans = new ArrayList<>();
        List<Long> idxScans = new ArrayList<>();
        List<Long> inserts = new ArrayList<>();
        List<Long> updates = new ArrayList<>();
        List<Long> deletes = new ArrayList<>();

        for (Map<String, Object> row : data) {
            categories.add(formatTimestamp(row.get("timestamp")));
            seqScans.add(getLongValue(row.get("seq_scan")));
            idxScans.add(getLongValue(row.get("idx_scan")));
            inserts.add(getLongValue(row.get("n_tup_ins")));
            updates.add(getLongValue(row.get("n_tup_upd")));
            deletes.add(getLongValue(row.get("n_tup_del")));
        }

        return HotTableDto.TableActivity.builder()
                .categories(categories)
                .seqScans(seqScans)
                .idxScans(idxScans)
                .inserts(inserts)
                .updates(updates)
                .deletes(deletes)
                .build();
    }

    /**
     * 캐시 히트율 시계열 데이터 조회
     */
    private HotTableDto.CacheHitRatio getCacheHitRatio(Long databaseId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> data = hotTableMapper.selectCacheHitRatioTimeSeries(databaseId, startTime, endTime);

        List<String> categories = new ArrayList<>();
        List<Double> ratios = new ArrayList<>();

        for (Map<String, Object> row : data) {
            categories.add(formatTimestamp(row.get("timestamp")));
            ratios.add(getDoubleValue(row.get("cache_hit_ratio")));
        }

        Double average = ratios.isEmpty() ? 0.0 : ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Double max = ratios.isEmpty() ? 0.0 : ratios.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        Double min = ratios.isEmpty() ? 0.0 : ratios.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return HotTableDto.CacheHitRatio.builder()
                .categories(categories)
                .data(ratios)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * Bloat 상태 조회
     */
    private HotTableDto.BloatStatus getBloatStatus(Long databaseId) {
        List<Map<String, Object>> data = hotTableMapper.selectBloatStatus(databaseId);

        List<String> categories = new ArrayList<>();
        List<Double> bloatData = new ArrayList<>();
        long normalCount = 0;
        long warningCount = 0;
        long criticalCount = 0;

        for (Map<String, Object> row : data) {
            String tableName = (String) row.get("table_name");
            Double bloatPercent = getDoubleValue(row.get("bloat_percent"));

            categories.add(tableName);
            bloatData.add(bloatPercent);

            if (bloatPercent < 15) {
                normalCount++;
            } else if (bloatPercent < 30) {
                warningCount++;
            } else {
                criticalCount++;
            }
        }

        return HotTableDto.BloatStatus.builder()
                .categories(categories)
                .data(bloatData)
                .normalCount(normalCount)
                .warningCount(warningCount)
                .criticalCount(criticalCount)
                .build();
    }

    /**
     * Vacuum 상태 조회
     */
    private HotTableDto.VacuumStatus getVacuumStatus(Long databaseId) {
        List<Map<String, Object>> data = hotTableMapper.selectVacuumStatus(databaseId);

        List<String> categories = new ArrayList<>();
        List<Long> delaySeconds = new ArrayList<>();

        for (Map<String, Object> row : data) {
            categories.add((String) row.get("table_name"));
            delaySeconds.add(getLongValue(row.get("vacuum_delay_seconds")));
        }

        Long avgDelaySeconds = delaySeconds.isEmpty() ? 0L :
                delaySeconds.stream().mapToLong(Long::longValue).sum() / delaySeconds.size();
        Long maxDelaySeconds = delaySeconds.isEmpty() ? 0L :
                delaySeconds.stream().mapToLong(Long::longValue).max().orElse(0L);

        return HotTableDto.VacuumStatus.builder()
                .categories(categories)
                .delaySeconds(delaySeconds)
                .avgDelaySeconds(avgDelaySeconds)
                .maxDelaySeconds(maxDelaySeconds)
                .build();
    }

    /**
     * 최근 통계 조회
     */
    private HotTableDto.RecentStats getRecentStats(Long databaseId) {
        Map<String, Object> data = hotTableMapper.selectRecentStats(databaseId);

        return HotTableDto.RecentStats.builder()
                .totalTables(getLongValue(data.get("total_tables")))
                .activeTables(getLongValue(data.get("active_tables")))
                .avgCacheHitRatio(getDoubleValue(data.get("avg_cache_hit_ratio")))
                .totalSeqScans(getLongValue(data.get("total_seq_scans")))
                .totalIdxScans(getLongValue(data.get("total_idx_scans")))
                .highBloatTables(getLongValue(data.get("high_bloat_tables")))
                .build();
    }

    /**
     * HotTable 리스트 데이터 조회
     * @param databaseId 데이터베이스 ID
     * @param timeRange 시간 범위
     * @param statusList 상태 필터 리스트
     * @return HotTable 리스트 데이터
     */
    public HotTableDto.ListResponse getHotTableList(Long databaseId, String timeRange, List<String> statusList) {
        log.info("HotTable 리스트 조회 시작 - databaseId: {}, timeRange: {}, statusList: {}",
                databaseId, timeRange, statusList);

        // 기본값 설정
        if (databaseId == null) {
            databaseId = 1L;
        }

        // 시간 범위 계산
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeRange);

        // 데이터 조회
        List<Map<String, Object>> dataList = hotTableMapper.selectHotTableList(
                databaseId, startTime, endTime, statusList);

        // DTO 변환
        List<HotTableDto.ListItem> items = dataList.stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());

        return HotTableDto.ListResponse.builder()
                .data(items)
                .total((long) items.size())
                .build();
    }

    /**
     * Map을 TableSummary로 변환
     */
    private List<HotTableDto.TableSummary> convertToTableSummary(List<Map<String, Object>> dataList) {
        return dataList.stream()
                .map(row -> HotTableDto.TableSummary.builder()
                        .schemaName((String) row.get("schema_name"))
                        .tableName((String) row.get("table_name"))
                        .value(getLongValue(row.get("value")))
                        .percentage(getDoubleValue(row.get("percentage")))
                        .status((String) row.get("status"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Map을 ListItem으로 변환
     */
    private HotTableDto.ListItem convertToListItem(Map<String, Object> row) {
        return HotTableDto.ListItem.builder()
                .id(String.valueOf(row.get("hot_table_raw_id")))
                .tableName((String) row.get("table_name"))
                .schemaName((String) row.get("schema_name"))
                .size(formatSize(getLongValue(row.get("table_size"))))
                .seqScan(getLongValue(row.get("seq_scan")))
                .seqTupRead(getLongValue(row.get("seq_tup_read")))
                .idxScan(getLongValue(row.get("idx_scan")))
                .idxTupFetch(getLongValue(row.get("idx_tup_fetch")))
                .nTupIns(getLongValue(row.get("n_tup_ins")))
                .nTupUpd(getLongValue(row.get("n_tup_upd")))
                .nTupDel(getLongValue(row.get("n_tup_del")))
                .nTupHotUpd(getLongValue(row.get("n_tup_hot_upd")))
                .nLiveTup(getLongValue(row.get("n_live_tup")))
                .nDeadTup(getLongValue(row.get("n_dead_tup")))
                .bloatPercent(getDoubleValue(row.get("bloat_percent")))
                .lastVacuum(formatTimestamp(row.get("last_vacuum")))
                .lastAutoVacuum(formatTimestamp(row.get("last_autovacuum")))
                .cacheHit(calculateCacheHitRatio(row))
                .status(determineStatus(row))
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
        Long heapBlksRead = getLongValue(row.get("heap_blks_read"));
        Long heapBlksHit = getLongValue(row.get("heap_blks_hit"));

        if (heapBlksRead + heapBlksHit == 0) {
            return 0.0;
        }

        return (heapBlksHit * 100.0) / (heapBlksRead + heapBlksHit);
    }

    /**
     * 상태 결정
     */
    private String determineStatus(Map<String, Object> row) {
        Double bloatPercent = getDoubleValue(row.get("bloat_percent"));
        Double cacheHitRatio = calculateCacheHitRatio(row);

        if (bloatPercent >= 30 || cacheHitRatio < 80) {
            return "위험";
        } else if (bloatPercent >= 15 || cacheHitRatio < 90) {
            return "주의";
        } else {
            return "정상";
        }
    }

    /**
     * 타임스탬프 포맷팅
     */
    private String formatTimestamp(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof LocalDateTime) {
            return ((LocalDateTime) timestamp).format(FORMATTER);
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