package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.domain.VacuumRawMetrics;
import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
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

    private final VacuumBloatRepository repository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d");

    /**
     * Bloat 대시보드 전체 데이터 조회
     */
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

    /**
     * Xmin Horizon 모니터링 데이터 조회
     */
    public VacuumBloatDto.XminHorizonMonitor getXminHorizonData(
            Long databaseId, OffsetDateTime startTime, OffsetDateTime endTime) {

        List<VacuumRawMetrics> metrics = repository.findXminHorizonData(databaseId, startTime, endTime);

        log.info("Xmin Horizon 조회 결과: {} 건 (databaseId: {})",
                metrics != null ? metrics.size() : 0, databaseId);

        // 24시간 단위로 집계 (00:00 ~ 23:00)
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
                // Xmin Horizon Age 계산 (blocker_xmin_horizon을 시간 단위로 변환)
                double avgXminAge = hourMetrics.stream()
                        .filter(m -> m.getBlockerXminHorizon() != null)
                        .mapToLong(VacuumRawMetrics::getBlockerXminHorizon)
                        .average()
                        .orElse(0.0) / 3600.0; // 초를 시간으로 변환

                // Vacuum 처리 속도 계산 (tuples_deleted / elapsed_seconds)
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

    /**
     * Bloat 트렌드 데이터 조회 (일별)
     */
    public VacuumBloatDto.BloatTrend getBloatTrendData(Long databaseId, int days) {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(days);
        List<VacuumTrendMetrics> metrics = repository.findBloatTrendData(databaseId, startDate);

        log.info("Bloat Trend 조회 결과: {} 건 (databaseId: {}, days: {})",
                metrics != null ? metrics.size() : 0, databaseId, days);

        // 일별로 그룹핑
        Map<String, List<VacuumTrendMetrics>> groupedByDate = metrics.stream()
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(DATE_FORMATTER)));

        List<String> labels = new ArrayList<>();
        List<Double> bloatData = new ArrayList<>();

        // 날짜순으로 정렬하여 데이터 생성
        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());

                    // 해당 날짜의 평균 Bloat (GB 단위)
                    double avgBloat = entry.getValue().stream()
                            .filter(m -> m.getBloatBytes() != null)
                            .mapToLong(VacuumTrendMetrics::getBloatBytes)
                            .average()
                            .orElse(0.0) / (1024.0 * 1024.0 * 1024.0); // Bytes to GB

                    bloatData.add(Math.round(avgBloat * 10.0) / 10.0);
                });

        return VacuumBloatDto.BloatTrend.builder()
                .data(bloatData)
                .labels(labels)
                .build();
    }

    /**
     * Bloat 분포 데이터 조회
     */
    public VacuumBloatDto.BloatDistribution getBloatDistributionData(Long databaseId) {
        OffsetDateTime since = OffsetDateTime.now().minusHours(24);
        List<Map<String, Object>> distribution = repository.findBloatDistribution(databaseId, since);

        log.info("Bloat Distribution 조회 결과: {} 건 (databaseId: {})",
                distribution != null ? distribution.size() : 0, databaseId);

        // 범위 순서대로 정렬
        List<String> orderedLabels = Arrays.asList("0-5%", "5-10%", "10-15%", "15-20%", "20-30%", "30%+");
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

    /**
     * KPI 지표 조회
     */
    public VacuumBloatDto.Kpi getKpiData(Long databaseId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime last24h = now.minusHours(24);
        OffsetDateTime last30d = now.minusDays(30);

        log.info("KPI 조회 시작 - databaseId: {}", databaseId);

        // 1. 총 Bloat 크기
        Long totalBloatBytes = repository.calculateTotalBloat(databaseId, last24h);
        String tableBloat = formatBytes(totalBloatBytes != null ? totalBloatBytes : 0L);

        // 2. Critical 테이블 수 (Bloat Ratio >= 15%)
        Long criticalCount = repository.countCriticalTables(databaseId, last24h);

        // 3. Bloat 증가량 (30일 전과 현재 비교)
        Long bloat30dAgo = repository.calculateTotalBloat(databaseId, last30d);
        Long bloatGrowthBytes = (totalBloatBytes != null ? totalBloatBytes : 0L) -
                (bloat30dAgo != null ? bloat30dAgo : 0L);
        String bloatGrowth = (bloatGrowthBytes >= 0 ? "+" : "") + formatBytes(bloatGrowthBytes);

        log.debug("KPI 결과 - Total: {}, Critical: {}, Growth: {}",
                tableBloat, criticalCount, bloatGrowth);

        return VacuumBloatDto.Kpi.builder()
                .tableBloat(tableBloat)
                .criticalTable(criticalCount != null ? criticalCount.intValue() : 0)
                .bloatGrowth(bloatGrowth)
                .build();
    }

    /**
     * 바이트 단위 포맷팅
     */
    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) {
            return "0B";
        }

        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;

        if (gb >= 1) {
            return String.format("%.1fGB", gb);
        } else if (mb >= 1) {
            return String.format("%.1fMB", mb);
        } else if (kb >= 1) {
            return String.format("%.1fKB", kb);
        } else {
            return bytes + "B";
        }
    }
}