package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDetailDto;
import com.dajanggan.domain.vacuum.repository.VacuumBloatDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacuumBloatDetailService {

    private final VacuumBloatDetailMapper bloatDetailMapper;

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 전체 대시보드 데이터 조회 (한번에 모두)
     */
    public VacuumBloatDetailDto.Response getBloatDetail(Long databaseId, String tableName) {
        log.info("🔍 Fetching bloat detail: databaseId={}, tableName={}", databaseId, tableName);

        return VacuumBloatDetailDto.Response.builder()
                .kpi(getKpi(databaseId, tableName))
                .bloatTrend(getBloatTrend(databaseId, tableName, 30))
                .deadTuplesTrend(getDeadTuplesTrend(databaseId, tableName, 30))
                .indexBloatTrend(getIndexBloatTrend(databaseId, tableName, 30))
                .build();
    }

    /**
     * KPI 데이터만 조회
     */
    public VacuumBloatDetailDto.Kpi getKpi(Long databaseId, String tableName) {
        Map<String, Object> latest = bloatDetailMapper.findLatestMetricsByTable(databaseId, tableName);
        return buildKpi(latest);
    }

    /**
     * Bloat % Trend 조회
     */
    public VacuumBloatDetailDto.BloatTrend getBloatTrend(Long databaseId, String tableName, int days) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(days);

        List<Map<String, Object>> data = bloatDetailMapper.findBloatTrendByTable(
                databaseId, tableName, startDate, now
        );
        return buildBloatTrend(data);
    }

    /**
     * Dead Tuples Trend 조회
     */
    public VacuumBloatDetailDto.DeadTuplesTrend getDeadTuplesTrend(Long databaseId, String tableName, int days) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(days);

        List<Map<String, Object>> data = bloatDetailMapper.findDeadTuplesTrendByTable(
                databaseId, tableName, startDate, now
        );
        return buildDeadTuplesTrend(data);
    }

    /**
     * Index Bloat Trend 조회
     */
    public VacuumBloatDetailDto.IndexBloatTrend getIndexBloatTrend(Long databaseId, String tableName, int days) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(days);

        List<Map<String, Object>> data = bloatDetailMapper.findIndexBloatTrendByTable(
                databaseId, tableName, startDate, now
        );
        return buildIndexBloatTrend(data);
    }

    /**
     * 데이터베이스 내 테이블 목록 조회
     */
    public List<String> getTableList(Long databaseId) {
        log.info("🔍 Fetching table list for databaseId={}", databaseId);
        List<String> tables = bloatDetailMapper.findTableList(databaseId);
        log.info("✅ Found {} tables", tables.size());
        return tables;
    }

    // ========== Private Helper Methods ==========

    private VacuumBloatDetailDto.Kpi buildKpi(Map<String, Object> latest) {
        if (latest == null || latest.isEmpty()) {
            log.warn("⚠️ No latest metrics found, returning default KPI");
            return VacuumBloatDetailDto.Kpi.builder()
                    .bloatPct("0%")
                    .tableSize("0 B")
                    .wastedSpace("0 B")
                    .build();
        }

        // Bloat Percentage
        String bloatPct = "0%";
        Object bloatRatioObj = latest.get("bloat_ratio");
        if (bloatRatioObj != null) {
            double ratio = convertToDouble(bloatRatioObj);
            bloatPct = String.format("%.1f%%", ratio * 100);
        }

        // Table Size
        String tableSize = "0 B";
        Object tableSizeObj = latest.get("table_size");
        if (tableSizeObj != null) {
            long sizeBytes = convertToLong(tableSizeObj);
            tableSize = formatBytes(sizeBytes);
        }

        // Wasted Space (Bloat Bytes)
        String wastedSpace = "0 B";
        Object bloatBytesObj = latest.get("bloat_bytes");
        if (bloatBytesObj != null) {
            long bloatBytes = convertToLong(bloatBytesObj);
            wastedSpace = formatBytes(bloatBytes);
        }

        log.info("📊 KPI: bloatPct={}, tableSize={}, wastedSpace={}", bloatPct, tableSize, wastedSpace);

        return VacuumBloatDetailDto.Kpi.builder()
                .bloatPct(bloatPct)
                .tableSize(tableSize)
                .wastedSpace(wastedSpace)
                .build();
    }

    private VacuumBloatDetailDto.BloatTrend buildBloatTrend(List<Map<String, Object>> data) {
        List<Double> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (Map<String, Object> row : data) {
            Object bloatRatioObj = row.get("bloat_ratio");
            Object collectedAtObj = row.get("collected_at");

            if (bloatRatioObj != null && collectedAtObj != null) {
                double ratio = convertToDouble(bloatRatioObj);
                values.add(ratio * 100); // 퍼센트로 변환

                OffsetDateTime collectedAt = convertToOffsetDateTime(collectedAtObj);
                if (collectedAt != null) {
                    labels.add(collectedAt.format(LABEL_FORMATTER));
                } else {
                    labels.add("");
                }
            }
        }

        log.info("📈 Bloat Trend: {} data points", values.size());

        return VacuumBloatDetailDto.BloatTrend.builder()
                .data(values)
                .labels(labels)
                .build();
    }

    private VacuumBloatDetailDto.DeadTuplesTrend buildDeadTuplesTrend(List<Map<String, Object>> data) {
        List<Long> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (Map<String, Object> row : data) {
            Object deadTuplesObj = row.get("dead_tuple_total");
            Object collectedAtObj = row.get("collected_at");

            if (deadTuplesObj != null && collectedAtObj != null) {
                long deadTuples = convertToLong(deadTuplesObj);
                values.add(deadTuples);

                OffsetDateTime collectedAt = convertToOffsetDateTime(collectedAtObj);
                if (collectedAt != null) {
                    labels.add(collectedAt.format(LABEL_FORMATTER));
                } else {
                    labels.add("");
                }
            }
        }

        log.info("📈 Dead Tuples Trend: {} data points", values.size());

        return VacuumBloatDetailDto.DeadTuplesTrend.builder()
                .data(values)
                .labels(labels)
                .build();
    }

    private VacuumBloatDetailDto.IndexBloatTrend buildIndexBloatTrend(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            log.info("📈 Index Bloat Trend: No data (feature not implemented yet)");
            return VacuumBloatDetailDto.IndexBloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(new ArrayList<>())
                    .names(new ArrayList<>())
                    .build();
        }

        // 인덱스별로 그룹화
        Map<String, List<Map<String, Object>>> groupedByIndex = data.stream()
                .collect(Collectors.groupingBy(m -> (String) m.get("index_name")));

        List<String> indexNames = new ArrayList<>(groupedByIndex.keySet());
        List<List<Double>> seriesData = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // 첫 번째 인덱스의 시간 레이블 추출
        if (!indexNames.isEmpty()) {
            List<Map<String, Object>> firstIndexData = groupedByIndex.get(indexNames.get(0));
            labels = firstIndexData.stream()
                    .map(m -> {
                        OffsetDateTime dt = convertToOffsetDateTime(m.get("collected_at"));
                        return dt != null ? dt.format(LABEL_FORMATTER) : "";
                    })
                    .collect(Collectors.toList());
        }

        // 각 인덱스별 데이터 시리즈 생성
        for (String indexName : indexNames) {
            List<Double> indexValues = groupedByIndex.get(indexName).stream()
                    .map(m -> {
                        Object ratioObj = m.get("bloat_ratio");
                        if (ratioObj == null) return 0.0;
                        return convertToDouble(ratioObj) * 100;
                    })
                    .collect(Collectors.toList());
            seriesData.add(indexValues);
        }

        log.info("📈 Index Bloat Trend: {} indexes, {} data points", indexNames.size(), labels.size());

        return VacuumBloatDetailDto.IndexBloatTrend.builder()
                .data(seriesData)
                .labels(labels)
                .names(indexNames)
                .build();
    }

    // ========== Type Conversion Helpers ==========

    private double convertToDouble(Object obj) {
        if (obj instanceof BigDecimal) {
            return ((BigDecimal) obj).doubleValue();
        } else if (obj instanceof Double) {
            return (Double) obj;
        } else if (obj instanceof Float) {
            return ((Float) obj).doubleValue();
        } else if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return 0.0;
    }

    private long convertToLong(Object obj) {
        if (obj instanceof BigDecimal) {
            return ((BigDecimal) obj).longValue();
        } else if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return 0L;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * DB에서 온 객체를 OffsetDateTime으로 안전하게 변환합니다.
     * - java.sql.Timestamp -> OffsetDateTime (시스템 기본 시간대)
     * - java.time.OffsetDateTime -> 그대로 반환
     * - java.time.LocalDateTime -> 시스템 기본 오프셋으로 변환
     * - java.time.Instant -> 시스템 기본 오프셋으로 변환
     */
    private OffsetDateTime convertToOffsetDateTime(Object obj) {
        if (obj == null) return null;

        ZoneId zone = ZoneId.systemDefault();

        if (obj instanceof OffsetDateTime) {
            return (OffsetDateTime) obj;
        } else if (obj instanceof Timestamp) {
            Instant instant = ((Timestamp) obj).toInstant();
            return OffsetDateTime.ofInstant(instant, zone);
        } else if (obj instanceof Instant) {
            return OffsetDateTime.ofInstant((Instant) obj, zone);
        } else if (obj instanceof LocalDateTime) {
            LocalDateTime ldt = (LocalDateTime) obj;
            return ldt.atZone(zone).toOffsetDateTime();
        } else if (obj instanceof java.util.Date) {
            Instant instant = ((java.util.Date) obj).toInstant();
            return OffsetDateTime.ofInstant(instant, zone);
        }

        // 지원하지 않는 타입일 경우 null 반환
        log.warn("Unsupported collected_at type: {}", obj.getClass().getName());
        return null;
    }
}
