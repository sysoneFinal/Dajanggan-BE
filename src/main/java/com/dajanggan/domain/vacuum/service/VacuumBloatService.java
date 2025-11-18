package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import com.dajanggan.domain.vacuum.repository.VacuumBloatRepository;
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

    private final VacuumBloatRepository bloatRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d");

    /**
     * 대시보드 전체 데이터 조회
     * ✅ 집계 테이블 (vacuum_metrics_agg_5m) 사용 - 빠름!
     */
    public VacuumBloatDto.Response getDashboardData(Long databaseId, Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();

        log.info("📊 Bloat Dashboard 조회 - databaseId: {}, instanceId: {}",
                databaseId, instanceId);

        return VacuumBloatDto.Response.builder()
                .xminHorizonMonitor(getXminHorizonData(databaseId, instanceId, now.minusDays(7), now))
                .bloatTrend(getBloatTrendData(databaseId, instanceId, 30))
                .bloatDistribution(getBloatDistributionData(databaseId, instanceId))
                .kpi(getKpiData(databaseId, instanceId))
                .build();
    }

    /**
     * Xmin Horizon Monitor
     * ✅ Raw 데이터 사용 - 실시간 상세 정보 필요
     */
    public VacuumBloatDto.XminHorizonMonitor getXminHorizonData(
            Long databaseId, Long instanceId,
            OffsetDateTime startTime, OffsetDateTime endTime) {

        List<VacuumRawMetricDto> metrics = bloatRepository.getXminHorizonData(
                databaseId, instanceId, startTime, endTime);

        log.info("📊 Xmin Horizon 조회 결과: {} 건", metrics != null ? metrics.size() : 0);

        if (metrics == null || metrics.isEmpty()) {
            List<String> labels = new ArrayList<>();
            List<Double> emptyData = new ArrayList<>();

            for (int hour = 0; hour < 24; hour++) {
                labels.add(String.format("%02d:00", hour));
                emptyData.add(0.0);
            }

            return VacuumBloatDto.XminHorizonMonitor.builder()
                    .data(Arrays.asList(emptyData, emptyData))
                    .labels(labels)
                    .build();
        }

        // 시간별 그룹화
        Map<Integer, List<VacuumRawMetricDto>> groupedByHour = metrics.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getCollectedAt() != null)
                .collect(Collectors.groupingBy(m -> m.getCollectedAt().getHour()));

        List<String> labels = new ArrayList<>();
        List<Double> xminHorizonAges = new ArrayList<>();
        List<Double> vacuumProcessingSpeeds = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            labels.add(String.format("%02d:00", hour));

            List<VacuumRawMetricDto> hourMetrics = groupedByHour.getOrDefault(hour, Collections.emptyList());

            if (hourMetrics.isEmpty()) {
                xminHorizonAges.add(0.0);
                vacuumProcessingSpeeds.add(0.0);
            } else {
                double avgXminAge = hourMetrics.stream()
                        .filter(m -> m.getBlockerXminHorizon() != null)
                        .mapToLong(VacuumRawMetricDto::getBlockerXminHorizon)
                        .average()
                        .orElse(0.0) / 3600.0;

                double avgSpeed = hourMetrics.stream()
                        .filter(m -> m.getTuplesDeleted() != null &&
                                m.getElapsedSeconds() != null &&
                                m.getElapsedSeconds() > 0)
                        .mapToDouble(m -> m.getTuplesDeleted().doubleValue() / m.getElapsedSeconds())
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

    /**
     * Bloat Trend (30일 추이)
     * ✅ 집계 테이블 사용 - 빠른 조회!
     */
    public VacuumBloatDto.BloatTrend getBloatTrendData(Long databaseId, Long instanceId, int days) {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(days);
        OffsetDateTime endDate = OffsetDateTime.now();

        // ✅ 5분 집계에서 조회
        List<VacuumAgg5mDto> metrics = bloatRepository.getBloatTrend(
                databaseId, instanceId, startDate, endDate);

        log.info("📊 Bloat Trend 조회 결과 (집계): {} 건", metrics != null ? metrics.size() : 0);

        if (metrics == null || metrics.isEmpty()) {
            return VacuumBloatDto.BloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(new ArrayList<>())
                    .build();
        }

        // 날짜별 그룹화 (일별 평균)
        Map<String, List<VacuumAgg5mDto>> groupedByDate = metrics.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getCollectedAt() != null)
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(DATE_FORMATTER)));

        List<String> labels = new ArrayList<>();
        List<Double> bloatData = new ArrayList<>();

        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());

                    // ✅ 하루 평균 total_bloat_bytes (이미 집계된 값)
                    double avgBloat = entry.getValue().stream()
                            .filter(Objects::nonNull)
                            .filter(m -> m.getTotalBloatBytes() != null)
                            .mapToLong(VacuumAgg5mDto::getTotalBloatBytes)
                            .average()
                            .orElse(0.0) / (1024.0 * 1024.0 * 1024.0);

                    bloatData.add(Math.round(avgBloat * 10.0) / 10.0);
                });

        return VacuumBloatDto.BloatTrend.builder()
                .data(bloatData)
                .labels(labels)
                .build();
    }

    /**
     * Bloat Distribution (분포도)
     * ✅ 집계 테이블 사용 - avg_bloat_ratio 활용
     */
    public VacuumBloatDto.BloatDistribution getBloatDistributionData(Long databaseId, Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime since = now.minusDays(1);

        // ✅ 5분 집계에서 조회
        List<VacuumAgg5mDto> metrics = bloatRepository.getBloatTrend(
                databaseId, instanceId, since, now);

        log.info("📊 Bloat Distribution 조회 결과 (집계): {} 건", metrics != null ? metrics.size() : 0);

        List<String> orderedLabels = Arrays.asList("0-5%", "5-10%", "10-15%", "15-20%", "20-30%", "30%+");

        if (metrics == null || metrics.isEmpty()) {
            return VacuumBloatDto.BloatDistribution.builder()
                    .data(Arrays.asList(0, 0, 0, 0, 0, 0))
                    .labels(orderedLabels)
                    .build();
        }

        Map<String, Integer> distributionMap = new HashMap<>();

        // ✅ avg_bloat_ratio를 사용 (이미 집계된 평균 비율)
        metrics.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getAvgBloatRatio() != null)
                .forEach(m -> {
                    double ratio = m.getAvgBloatRatio() * 100;
                    String range;

                    if (ratio < 5) range = "0-5%";
                    else if (ratio < 10) range = "5-10%";
                    else if (ratio < 15) range = "10-15%";
                    else if (ratio < 20) range = "15-20%";
                    else if (ratio < 30) range = "20-30%";
                    else range = "30%+";

                    distributionMap.merge(range, 1, Integer::sum);
                });

        List<Integer> data = orderedLabels.stream()
                .map(label -> distributionMap.getOrDefault(label, 0))
                .collect(Collectors.toList());

        return VacuumBloatDto.BloatDistribution.builder()
                .data(data)
                .labels(orderedLabels)
                .build();
    }

    /**
     * KPI 데이터 (요약 지표)
     * ✅ 집계 테이블 사용 - total_bloat_bytes, critical_bloat_tables 활용
     */
    public VacuumBloatDto.Kpi getKpiData(Long databaseId, Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime last7d = now.minusDays(7);
        OffsetDateTime last30d = now.minusDays(30);

        log.info("📊 KPI 조회 시작 - databaseId: {}, instanceId: {}", databaseId, instanceId);

        // ✅ 5분 집계에서 KPI 요약 조회
        VacuumAgg5mDto current = bloatRepository.getKpiSummary(databaseId, instanceId, last7d, now);
        VacuumAgg5mDto past = bloatRepository.getKpiSummary(databaseId, instanceId, last30d, last30d.plusDays(7));

        // ✅ total_bloat_bytes 사용 (전체 DB의 Bloat 합계)
        Long currentBloatBytes = (current != null && current.getTotalBloatBytes() != null)
                ? current.getTotalBloatBytes()
                : 0L;
        String tableBloat = formatBytes(currentBloatBytes);

        // ✅ critical_bloat_tables 사용 (위험 테이블 수)
        Integer criticalCount = (current != null && current.getCriticalBloatTables() != null)
                ? current.getCriticalBloatTables()
                : 0;

        // Bloat 증가량 계산
        Long pastBloatBytes = (past != null && past.getTotalBloatBytes() != null)
                ? past.getTotalBloatBytes()
                : 0L;
        Long bloatGrowthBytes = currentBloatBytes - pastBloatBytes;

        String bloatGrowth;
        if (bloatGrowthBytes == 0) {
            bloatGrowth = "0B";
        } else if (bloatGrowthBytes > 0) {
            bloatGrowth = "+" + formatBytes(bloatGrowthBytes);
        } else {
            bloatGrowth = "-" + formatBytes(Math.abs(bloatGrowthBytes));
        }

        log.info("📊 KPI 계산 완료 - Total: {}, Critical: {}, Growth: {}",
                tableBloat, criticalCount, bloatGrowth);

        return VacuumBloatDto.Kpi.builder()
                .tableBloat(tableBloat)
                .criticalTable(criticalCount)
                .bloatGrowth(bloatGrowth)
                .build();
    }

    // ========== 유틸리티 메서드 ==========

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0B";
        long absBytes = Math.abs(bytes);

        double gb = absBytes / (1024.0 * 1024.0 * 1024.0);
        double mb = absBytes / (1024.0 * 1024.0);
        double kb = absBytes / 1024.0;

        if (gb >= 1) return String.format("%.1fGB", gb);
        if (mb >= 1) return String.format("%.1fMB", mb);
        if (kb >= 1) return String.format("%.1fKB", kb);
        return absBytes + "B";
    }
}