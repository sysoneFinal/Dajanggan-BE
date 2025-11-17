package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.domain.VacuumRawMetrics;
import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.repository.VacuumBloatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumBloatService {

    private final VacuumBloatMapper vacuumBloatMapper;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d");

    /**
     * ✅ 시간 범위에 따라 집계 테이블 선택
     */
    private String selectAggregationTable(int days) {
        if (days <= 1) {
            return "vacuum_metrics_agg_1m";
        } else if (days <= 7) {
            return "vacuum_metrics_agg_5m";
        } else {
            return "vacuum_metrics_agg_5m";
        }
    }

    public VacuumBloatDto.Response getDashboardData(Long databaseId) {
        OffsetDateTime now = OffsetDateTime.now();

        log.info("Bloat Dashboard 조회 시작 - databaseId: {}", databaseId);

        return VacuumBloatDto.Response.builder()
                .xminHorizonMonitor(getXminHorizonData(databaseId, now.minusDays(7), now))
                .bloatTrend(getBloatTrendData(databaseId, 30))
                .bloatDistribution(getBloatDistributionData(databaseId))
                .kpi(getKpiData(databaseId))
                .build();
    }

    public VacuumBloatDto.XminHorizonMonitor getXminHorizonData(
            Long databaseId, OffsetDateTime startTime, OffsetDateTime endTime) {

        // Raw 데이터는 vacuum_raw_metrics 사용
        List<VacuumRawMetrics> metrics = vacuumBloatMapper.findXminHorizonData(databaseId, startTime, endTime);

        log.info("Xmin Horizon 조회 결과: {} 건", metrics != null ? metrics.size() : 0);

        Map<Integer, List<VacuumRawMetrics>> groupedByHour = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getCollectedAt().getHour()));

        List<String> labels = new ArrayList<>();
        List<Double> xminHorizonAges = new ArrayList<>();
        List<Double> vacuumProcessingSpeeds = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            labels.add(String.format("%02d:00", hour));

            List<VacuumRawMetrics> hourMetrics = groupedByHour.getOrDefault(hour, Collections.emptyList());

            if (hourMetrics.isEmpty()) {
                xminHorizonAges.add(0.0);
                vacuumProcessingSpeeds.add(0.0);
            } else {
                double avgXminAge = hourMetrics.stream()
                        .filter(m -> m.getBlockerXminHorizon() != null)
                        .mapToLong(VacuumRawMetrics::getBlockerXminHorizon)
                        .average()
                        .orElse(0.0) / 3600.0;

                double avgSpeed = hourMetrics.stream()
                        .filter(m -> m.getTuplesDeleted() != null &&
                                m.getElapsedSeconds() != null &&
                                m.getElapsedSeconds() > 0)
                        .mapToDouble(m -> (double) m.getTuplesDeleted() / m.getElapsedSeconds())
                        .average()
                        .orElse(0.0);

                xminHorizonAges.add(Math.round(avgXminAge * 100.0) / 100.0);
                vacuumProcessingSpeeds.add((double) Math.round(avgSpeed));
            }
        }

        return VacuumBloatDto.XminHorizonMonitor.builder()
                .data(Arrays.asList(xminHorizonAges, vacuumProcessingSpeeds))
                .labels(labels)
                .build();
    }

    public VacuumBloatDto.BloatTrend getBloatTrendData(Long databaseId, int days) {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(days);

        // ✅ 집계 테이블 선택
        String aggTable = selectAggregationTable(days);
        log.info("✅ Bloat Trend - aggTable: {}, days: {}", aggTable, days);

        List<VacuumTrendMetrics> metrics = vacuumBloatMapper.findBloatTrendData(databaseId, startDate, aggTable);

        log.info("Bloat Trend 조회 결과: {} 건", metrics != null ? metrics.size() : 0);

        if (metrics == null || metrics.isEmpty()) {
            return VacuumBloatDto.BloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(new ArrayList<>())
                    .build();
        }

        Map<String, List<VacuumTrendMetrics>> groupedByDate = metrics.stream()
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(DATE_FORMATTER)));

        List<String> labels = new ArrayList<>();
        List<Double> bloatData = new ArrayList<>();

        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());
                    double avgBloat = entry.getValue().stream()
                            .filter(m -> m.getBloatBytes() != null)
                            .mapToLong(VacuumTrendMetrics::getBloatBytes)
                            .average()
                            .orElse(0.0) / (1024.0 * 1024.0 * 1024.0);
                    bloatData.add(Math.round(avgBloat * 10.0) / 10.0);
                });

        return VacuumBloatDto.BloatTrend.builder()
                .data(bloatData)
                .labels(labels)
                .build();
    }

    public VacuumBloatDto.BloatDistribution getBloatDistributionData(Long databaseId) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(7);

        // ✅ 집계 테이블 선택
        String aggTable = selectAggregationTable(7);

        List<Map<String, Object>> distribution = vacuumBloatMapper.findBloatDistribution(databaseId, since, aggTable);

        log.info("Bloat Distribution 조회 결과: {} 건", distribution != null ? distribution.size() : 0);

        List<String> orderedLabels = Arrays.asList("0-5%", "5-10%", "10-15%", "15-20%", "20-30%", "30%+");

        if (distribution == null || distribution.isEmpty()) {
            return VacuumBloatDto.BloatDistribution.builder()
                    .data(Arrays.asList(0, 0, 0, 0, 0, 0))
                    .labels(orderedLabels)
                    .build();
        }

        Map<String, Integer> distributionMap = distribution.stream()
                .collect(Collectors.toMap(
                        obj -> (String) obj.get("range"),
                        obj -> ((Number) obj.get("count")).intValue()
                ));

        List<Integer> data = orderedLabels.stream()
                .map(label -> distributionMap.getOrDefault(label, 0))
                .collect(Collectors.toList());

        return VacuumBloatDto.BloatDistribution.builder()
                .data(data)
                .labels(orderedLabels)
                .build();
    }

    public VacuumBloatDto.Kpi getKpiData(Long databaseId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime last7d = now.minusDays(7);
        OffsetDateTime last30d = now.minusDays(30);

        // ✅ 집계 테이블 선택
        String aggTable7d = selectAggregationTable(7);
        String aggTable30d = selectAggregationTable(30);

        log.info("KPI 조회 시작 - databaseId: {}", databaseId);

        Long totalBloatBytes = vacuumBloatMapper.calculateTotalBloat(databaseId, last7d, aggTable7d);
        String tableBloat = formatBytes(totalBloatBytes != null ? totalBloatBytes : 0L);

        Long criticalCount = vacuumBloatMapper.countCriticalTables(databaseId, last7d, aggTable7d);
        int criticalTableCount = criticalCount != null ? criticalCount.intValue() : 0;

        Long bloat30dAgo = vacuumBloatMapper.calculateTotalBloat(databaseId, last30d, aggTable30d);
        Long bloatNow = totalBloatBytes;

        Long bloatGrowthBytes = (bloatNow != null ? bloatNow : 0L) -
                (bloat30dAgo != null ? bloat30dAgo : 0L);

        String bloatGrowth;
        if (bloatGrowthBytes == 0) {
            bloatGrowth = "0B";
        } else if (bloatGrowthBytes > 0) {
            bloatGrowth = "+" + formatBytes(bloatGrowthBytes);
        } else {
            bloatGrowth = "-" + formatBytes(Math.abs(bloatGrowthBytes));
        }

        log.info("KPI 계산 완료 - Total: {}, Critical: {}, Growth: {}",
                tableBloat, criticalTableCount, bloatGrowth);

        return VacuumBloatDto.Kpi.builder()
                .tableBloat(tableBloat)
                .criticalTable(criticalTableCount)
                .bloatGrowth(bloatGrowth)
                .build();
    }

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0B";
        long absBytes = Math.abs(bytes);
        boolean isNegative = bytes < 0;

        double kb = absBytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;

        String result;
        if (gb >= 1) {
            result = String.format("%.1fGB", gb);
        } else if (mb >= 1) {
            result = String.format("%.1fMB", mb);
        } else if (kb >= 1) {
            result = String.format("%.1fKB", kb);
        } else {
            result = absBytes + "B";
        }

        return isNegative ? "-" + result : result;
    }
}