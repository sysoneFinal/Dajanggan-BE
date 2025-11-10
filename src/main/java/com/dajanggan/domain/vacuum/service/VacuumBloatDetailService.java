package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import com.dajanggan.domain.vacuum.dto.VacuumBloatDetailDto;
import com.dajanggan.domain.vacuum.repository.VacuumBloatDetailMapper;
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
public class VacuumBloatDetailService {

    private final VacuumBloatDetailMapper bloatDetailMapper;

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 전체 대시보드 데이터 조회 (한번에 모두)
     */
    public VacuumBloatDetailDto.Response getBloatDetail(Long databaseId, String tableName) {
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
        VacuumTrendMetrics latest = bloatDetailMapper.findLatestMetricsByTable(databaseId, tableName);
        return buildKpi(latest);
    }

    /**
     * Bloat % Trend 조회
     */
    public VacuumBloatDetailDto.BloatTrend getBloatTrend(Long databaseId, String tableName, int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(days);

        List<VacuumTrendMetrics> data = bloatDetailMapper.findBloatTrendByTable(
                databaseId, tableName, startDate, now
        );
        return buildBloatTrend(data);
    }

    /**
     * Dead Tuples Trend 조회
     */
    public VacuumBloatDetailDto.DeadTuplesTrend getDeadTuplesTrend(Long databaseId, String tableName, int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(days);

        List<VacuumTrendMetrics> data = bloatDetailMapper.findDeadTuplesTrendByTable(
                databaseId, tableName, startDate, now
        );
        return buildDeadTuplesTrend(data);
    }

    /**
     * Index Bloat Trend 조회
     */
    public VacuumBloatDetailDto.IndexBloatTrend getIndexBloatTrend(Long databaseId, String tableName, int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(days);

        List<Map<String, Object>> data = bloatDetailMapper.findIndexBloatTrendByTable(
                databaseId, tableName, startDate, now
        );
        return buildIndexBloatTrend(data);
    }

    /**
     * 데이터베이스 내 테이블 목록 조회
     */
    public List<String> getTableList(Long databaseId) {
        return bloatDetailMapper.findTableList(databaseId);
    }

    // ========== Private Helper Methods ==========

    private VacuumBloatDetailDto.Kpi buildKpi(VacuumTrendMetrics latest) {
        if (latest == null) {
            return VacuumBloatDetailDto.Kpi.builder()
                    .bloatPct("0%")
                    .tableSize("0 B")
                    .wastedSpace("0 B")
                    .build();
        }

        String bloatPct = "0%";
        if (latest.getBloatRatio() != null) {
            bloatPct = String.format("%.1f%%", latest.getBloatRatio().doubleValue() * 100);
        }

        String wastedSpace = "0 B";
        if (latest.getBloatBytes() != null) {
            wastedSpace = formatBytes(latest.getBloatBytes());
        }

        // Table Size는 vacuum_raw_metrics의 relsize_total_bytes에서 가져와야 함
        // 여기서는 임시로 계산
        String tableSize = "N/A";

        return VacuumBloatDetailDto.Kpi.builder()
                .bloatPct(bloatPct)
                .tableSize(tableSize)
                .wastedSpace(wastedSpace)
                .build();
    }

    private VacuumBloatDetailDto.BloatTrend buildBloatTrend(List<VacuumTrendMetrics> data) {
        List<Double> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (VacuumTrendMetrics metric : data) {
            if (metric.getBloatRatio() != null) {
                Double bloatRatio = metric.getBloatRatio().doubleValue(); // BigDecimal -> Double
                values.add(bloatRatio * 100); // 퍼센트로 변환
                labels.add(metric.getCollectedAt().format(LABEL_FORMATTER));
            }
        }

        return VacuumBloatDetailDto.BloatTrend.builder()
                .data(values)
                .labels(labels)
                .build();
    }

    private VacuumBloatDetailDto.DeadTuplesTrend buildDeadTuplesTrend(List<VacuumTrendMetrics> data) {
        List<Long> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (VacuumTrendMetrics metric : data) {
            Long deadTuples = metric.getDeadTupleTotal();
            if (deadTuples != null) {
                values.add(deadTuples);
                labels.add(metric.getCollectedAt().format(LABEL_FORMATTER));
            }
        }

        return VacuumBloatDetailDto.DeadTuplesTrend.builder()
                .data(values)
                .labels(labels)
                .build();
    }

    private VacuumBloatDetailDto.IndexBloatTrend buildIndexBloatTrend(List<Map<String, Object>> data) {
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
                    .map(m -> ((LocalDateTime) m.get("collected_at")).format(LABEL_FORMATTER))
                    .collect(Collectors.toList());
        }

        // 각 인덱스별 데이터 시리즈 생성
        for (String indexName : indexNames) {
            List<Double> indexValues = groupedByIndex.get(indexName).stream()
                    .map(m -> {
                        Object ratioObj = m.get("bloat_ratio");
                        if (ratioObj == null) return 0.0;

                        // BigDecimal -> Double 변환
                        if (ratioObj instanceof java.math.BigDecimal) {
                            return ((java.math.BigDecimal) ratioObj).doubleValue() * 100;
                        }
                        return ((Double) ratioObj) * 100;
                    })
                    .collect(Collectors.toList());
            seriesData.add(indexValues);
        }

        return VacuumBloatDetailDto.IndexBloatTrend.builder()
                .data(seriesData)
                .labels(labels)
                .names(indexNames)
                .build();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}