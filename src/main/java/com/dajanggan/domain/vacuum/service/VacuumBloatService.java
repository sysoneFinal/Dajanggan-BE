package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import com.dajanggan.domain.vacuum.repository.VacuumBloatRepository;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final DatabaseRepository databaseRepository;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d");

    /**
     * 대시보드 전체 데이터 조회
     * ✅ 집계 테이블 (vacuum_metrics_agg_5m) 사용 - 빠름!
     */
    public VacuumBloatDto.Response getDashboardData(Long databaseId, Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();

        log.info("📊 Bloat Dashboard 조회 시작 - databaseId: {}, instanceId: {}",
                databaseId, instanceId);

        try {
            VacuumBloatDto.XminHorizonMonitor xminData = getXminHorizonData(databaseId, instanceId, now.minusDays(7), now);
            log.info("📊 Xmin Horizon 데이터: labels={}, data rows={}", 
                    xminData.getLabels().size(), 
                    xminData.getData() != null ? xminData.getData().size() : 0);

            VacuumBloatDto.BloatTrend trendData = getBloatTrendData(databaseId, instanceId, 30);
            log.info("📊 Bloat Trend 데이터: labels={}, data points={}", 
                    trendData.getLabels().size(), 
                    trendData.getData().size());

            VacuumBloatDto.IndexBloatTrend indexBloatTrendData = getIndexBloatTrendData(databaseId, instanceId, 30);
            log.info("📊 Index Bloat Trend 데이터: labels={}, data points={}", 
                    indexBloatTrendData.getLabels().size(), 
                    indexBloatTrendData.getData().size());

            VacuumBloatDto.BloatDistribution distData = getBloatDistributionData(databaseId, instanceId);
            log.info("📊 Bloat Distribution 데이터: labels={}, data points={}", 
                    distData.getLabels().size(), 
                    distData.getData().size());

            VacuumBloatDto.Kpi kpiData = getKpiData(databaseId, instanceId);
            log.info("📊 KPI 데이터: tableBloat={}, criticalTable={}, bloatGrowth={}", 
                    kpiData.getTableBloat(), 
                    kpiData.getCriticalTable(), 
                    kpiData.getBloatGrowth());

            return VacuumBloatDto.Response.builder()
                    .xminHorizonMonitor(xminData)
                    .bloatTrend(trendData)
                    .indexBloatTrend(indexBloatTrendData)
                    .bloatDistribution(distData)
                    .kpi(kpiData)
                    .build();
        } catch (Exception e) {
            log.error("📊 Bloat Dashboard 조회 실패 - databaseId: {}, instanceId: {}", 
                    databaseId, instanceId, e);
            throw e;
        }
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
                // blocker_xmin_horizon 값 확인
                List<Long> xminValues = hourMetrics.stream()
                        .filter(m -> m.getBlockerXminHorizon() != null)
                        .map(VacuumRawMetricDto::getBlockerXminHorizon)
                        .collect(Collectors.toList());
                
                if (xminValues.isEmpty()) {
                    log.warn("📊 시간 {}: blocker_xmin_horizon이 NULL인 데이터만 있음 (전체 {} 건)", 
                            hour, hourMetrics.size());
                    xminHorizonAges.add(0.0);
                } else {
                    double avgXminAgeSeconds = xminValues.stream()
                            .mapToLong(Long::longValue)
                            .average()
                            .orElse(0.0);
                    
                    // 초 단위를 분 단위로 변환
                    double avgXminAgeMinutes = avgXminAgeSeconds / 60.0;
                    // 소수점 2자리까지 표시
                    double roundedAge = Math.round(avgXminAgeMinutes * 100.0) / 100.0;
                    
                    // 샘플 데이터 로그 (첫 시간대만)
                    if (hour == 0) {
                        log.info("📊 시간 {}: 유효 데이터 {} 건, blocker_xmin_horizon 샘플: {}, 평균: {} 초 ({} 분), 반올림: {} 분", 
                                hour, xminValues.size(), 
                                xminValues.stream().limit(5).collect(Collectors.toList()),
                                avgXminAgeSeconds, avgXminAgeMinutes, roundedAge);
                    }
                    
                    xminHorizonAges.add(roundedAge);
                }

                double avgSpeed = hourMetrics.stream()
                        .filter(m -> m.getTuplesDeleted() != null &&
                                m.getElapsedSeconds() != null &&
                                m.getElapsedSeconds() > 0)
                        .mapToDouble(m -> m.getTuplesDeleted().doubleValue() / m.getElapsedSeconds())
                        .average()
                        .orElse(0.0);

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
     * 
     * 데이터 소스: vacuum_metrics_agg_5m 테이블의 total_bloat_bytes
     * - 이 데이터는 집계 배치(VacuumAgg5mAggregator)가 실행되어야 생성됩니다
     * - vacuum_metrics_agg_1m → vacuum_metrics_agg_5m로 집계됩니다
     */
    public VacuumBloatDto.BloatTrend getBloatTrendData(Long databaseId, Long instanceId, int days) {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(days);
        OffsetDateTime endDate = OffsetDateTime.now();

        log.info("📊 Bloat Trend 조회 시작 - databaseId: {}, instanceId: {}, days: {}, startDate: {}, endDate: {}",
                databaseId, instanceId, days, startDate, endDate);

        // ✅ 5분 집계에서 조회
        List<VacuumAgg5mDto> metrics = bloatRepository.getBloatTrend(
                databaseId, instanceId, startDate, endDate);

        log.info("📊 Bloat Trend 조회 결과 (vacuum_metrics_agg_5m): {} 건", metrics != null ? metrics.size() : 0);

        if (metrics == null || metrics.isEmpty()) {
            log.warn("📊 Bloat Trend 데이터 없음 - vacuum_metrics_agg_5m 테이블에 데이터가 없습니다. " +
                    "집계 배치가 실행되었는지 확인하세요. databaseId: {}, instanceId: {}", 
                    databaseId, instanceId);
            return VacuumBloatDto.BloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(new ArrayList<>())
                    .build();
        }

        // total_bloat_bytes가 NULL이 아닌 데이터만 필터링
        List<VacuumAgg5mDto> validMetrics = metrics.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getCollectedAt() != null)
                .filter(m -> m.getTotalBloatBytes() != null && m.getTotalBloatBytes() > 0)
                .collect(Collectors.toList());

        log.info("📊 Bloat Trend 유효 데이터: {} 건 (total_bloat_bytes > 0)", validMetrics.size());

        if (validMetrics.isEmpty()) {
            log.warn("📊 Bloat Trend 유효 데이터 없음 - total_bloat_bytes가 모두 NULL이거나 0입니다. " +
                    "databaseId: {}, instanceId: {}", databaseId, instanceId);
            return VacuumBloatDto.BloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(new ArrayList<>())
                    .build();
        }

        // 날짜별 그룹화 (일별 평균)
        Map<String, List<VacuumAgg5mDto>> groupedByDate = validMetrics.stream()
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(DATE_FORMATTER)));

        List<String> labels = new ArrayList<>();
        List<Double> bloatData = new ArrayList<>();

        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());

                    // ✅ 하루 평균 total_bloat_bytes (이미 집계된 값) → GB 변환
                    // totalBloatBytes > 0인 데이터만 평균 계산에 포함
                    List<Long> validBytes = entry.getValue().stream()
                            .filter(m -> m.getTotalBloatBytes() != null && m.getTotalBloatBytes() > 0)
                            .map(VacuumAgg5mDto::getTotalBloatBytes)
                            .collect(Collectors.toList());

                    if (validBytes.isEmpty()) {
                        log.warn("📊 날짜 {}에 유효한 total_bloat_bytes 데이터가 없습니다. 전체 데이터: {} 건", 
                                entry.getKey(), entry.getValue().size());
                        bloatData.add(0.0);
                    } else {
                        double avgBloatBytes = validBytes.stream()
                                .mapToLong(Long::longValue)
                                .average()
                                .orElse(0.0);
                        
                        // MB 단위로 변환 (GB는 너무 작아서 0으로 반올림될 수 있음)
                        double avgBloatMB = avgBloatBytes / (1024.0 * 1024.0);
                        // GB로 변환 (더 정밀한 반올림)
                        double avgBloatGB = avgBloatMB / 1024.0;
                        // 소수점 2자리까지 표시
                        double roundedBloat = Math.round(avgBloatGB * 100.0) / 100.0;
                        
                        log.info("📊 날짜 {}: 유효 데이터 {} 건, 평균 {} bytes ({} MB, {} GB), 반올림: {} GB", 
                                entry.getKey(), validBytes.size(), 
                                (long)avgBloatBytes, Math.round(avgBloatMB * 10.0) / 10.0, avgBloatGB, roundedBloat);
                        
                        bloatData.add(roundedBloat);
                    }
                });

        log.info("📊 Bloat Trend 최종 결과: {} 일 데이터, labels: {}, data: {}", 
                labels.size(), labels, bloatData);

        return VacuumBloatDto.BloatTrend.builder()
                .data(bloatData)
                .labels(labels)
                .build();
    }

    /**
     * Index Bloat Trend (30일 추이)
     * ✅ 집계 테이블 사용 - 빠른 조회!
     * 
     * 데이터 소스: vacuum_metrics_agg_5m 테이블의 total_index_bloat_bytes
     * - 이 데이터는 집계 배치(VacuumAgg5mAggregator)가 실행되어야 생성됩니다
     * - vacuum_metrics_agg_1m → vacuum_metrics_agg_5m로 집계됩니다
     */
    public VacuumBloatDto.IndexBloatTrend getIndexBloatTrendData(Long databaseId, Long instanceId, int days) {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(days);
        OffsetDateTime endDate = OffsetDateTime.now();

        log.info("📊 Index Bloat Trend 조회 시작 - databaseId: {}, instanceId: {}, days: {}, startDate: {}, endDate: {}",
                databaseId, instanceId, days, startDate, endDate);

        // ✅ 5분 집계에서 조회
        List<VacuumAgg5mDto> metrics = bloatRepository.getBloatTrend(
                databaseId, instanceId, startDate, endDate);

        log.info("📊 Index Bloat Trend 조회 결과 (vacuum_metrics_agg_5m): {} 건", metrics != null ? metrics.size() : 0);

        if (metrics == null || metrics.isEmpty()) {
            log.warn("📊 Index Bloat Trend 데이터 없음 - vacuum_metrics_agg_5m 테이블에 데이터가 없습니다. " +
                    "집계 배치가 실행되었는지 확인하세요. databaseId: {}, instanceId: {}", 
                    databaseId, instanceId);
            return VacuumBloatDto.IndexBloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(new ArrayList<>())
                    .build();
        }

        // total_index_bloat_bytes가 NULL이 아닌 데이터만 필터링
        List<VacuumAgg5mDto> validMetrics = metrics.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getCollectedAt() != null)
                .filter(m -> m.getTotalIndexBloatBytes() != null && m.getTotalIndexBloatBytes() > 0)
                .collect(Collectors.toList());

        log.info("📊 Index Bloat Trend 유효 데이터: {} 건 (total_index_bloat_bytes > 0)", validMetrics.size());

        if (validMetrics.isEmpty()) {
            log.warn("📊 Index Bloat Trend 유효 데이터 없음 - total_index_bloat_bytes가 모두 NULL이거나 0입니다. " +
                    "databaseId: {}, instanceId: {}", databaseId, instanceId);
            return VacuumBloatDto.IndexBloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(new ArrayList<>())
                    .build();
        }

        // 날짜별 그룹화 (일별 평균)
        Map<String, List<VacuumAgg5mDto>> groupedByDate = validMetrics.stream()
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(DATE_FORMATTER)));

        List<String> labels = new ArrayList<>();
        List<Double> indexBloatData = new ArrayList<>();

        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());

                    // ✅ 하루 평균 total_index_bloat_bytes → GB 변환
                    // totalIndexBloatBytes > 0인 데이터만 평균 계산에 포함
                    List<Long> validBytes = entry.getValue().stream()
                            .filter(m -> m.getTotalIndexBloatBytes() != null && m.getTotalIndexBloatBytes() > 0)
                            .map(VacuumAgg5mDto::getTotalIndexBloatBytes)
                            .collect(Collectors.toList());

                    if (validBytes.isEmpty()) {
                        log.warn("📊 날짜 {}에 유효한 total_index_bloat_bytes 데이터가 없습니다. 전체 데이터: {} 건", 
                                entry.getKey(), entry.getValue().size());
                        indexBloatData.add(0.0);
                    } else {
                        double avgIndexBloatBytes = validBytes.stream()
                                .mapToLong(Long::longValue)
                                .average()
                                .orElse(0.0);
                        
                        // GB 단위로 변환
                        double avgIndexBloatGB = avgIndexBloatBytes / (1024.0 * 1024.0 * 1024.0);
                        // 소수점 2자리까지 표시
                        double roundedBloat = Math.round(avgIndexBloatGB * 100.0) / 100.0;
                        
                        log.info("📊 날짜 {}: Index Bloat 유효 데이터 {} 건, 평균 {} bytes ({} GB), 반올림: {} GB", 
                                entry.getKey(), validBytes.size(), 
                                (long)avgIndexBloatBytes, avgIndexBloatGB, roundedBloat);
                        
                        indexBloatData.add(roundedBloat);
                    }
                });

        log.info("📊 Index Bloat Trend 최종 결과: {} 일 데이터, labels: {}, data: {}", 
                labels.size(), labels, indexBloatData);

        return VacuumBloatDto.IndexBloatTrend.builder()
                .data(indexBloatData)
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
        OffsetDateTime last14d = now.minusDays(14);

        log.info("📊 KPI 조회 시작 - databaseId: {}, instanceId: {}", databaseId, instanceId);

        // ✅ 5분 집계에서 KPI 요약 조회
        // current: 최근 7일 (7일 전 ~ 현재)
        // past: 7일 전부터 7일간 (7일 전 ~ 14일 전) - 7일 전 대비 증가량 계산
        VacuumAgg5mDto current = bloatRepository.getKpiSummary(databaseId, instanceId, last7d, now);
        VacuumAgg5mDto past = bloatRepository.getKpiSummary(databaseId, instanceId, last14d, last7d);

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

        // ✅ 메타데이터 조회 (실제 PostgreSQL 인스턴스에 연결하여 조회)
        Long totalDatabaseSizeBytes = getTotalDatabaseSizeFromInstance(databaseId, instanceId);
        Integer totalTableCount = getTotalTableCountFromInstance(databaseId, instanceId);

        // ✅ Severity 계산
        String tableBloatSeverity = calculateTableBloatSeverity(currentBloatBytes, totalDatabaseSizeBytes);
        String criticalTableSeverity = calculateCriticalTableSeverity(criticalCount, totalTableCount);
        String bloatGrowthSeverity = calculateBloatGrowthSeverity(bloatGrowthBytes, currentBloatBytes);

        log.info("📊 KPI 계산 완료 - Total: {}, Critical: {}, Growth: {}",
                tableBloat, criticalCount, bloatGrowth);
        log.info("📊 Severity - TableBloat: {}, CriticalTable: {}, Growth: {}",
                tableBloatSeverity, criticalTableSeverity, bloatGrowthSeverity);

        return VacuumBloatDto.Kpi.builder()
                .tableBloat(tableBloat)
                .criticalTable(criticalCount)
                .bloatGrowth(bloatGrowth)
                .tableBloatSeverity(tableBloatSeverity)
                .criticalTableSeverity(criticalTableSeverity)
                .bloatGrowthSeverity(bloatGrowthSeverity)
                .totalDatabaseSizeBytes(totalDatabaseSizeBytes)
                .totalTableCount(totalTableCount)
                .build();
    }

    /**
     * Table Bloat Severity 계산
     * - 전체 DB 크기 대비 비율로 판단
     * - 10-20%: WARNING, 30% 이상: CRITICAL
     */
    private String calculateTableBloatSeverity(Long bloatBytes, Long totalDatabaseSizeBytes) {
        if (totalDatabaseSizeBytes == null || totalDatabaseSizeBytes == 0) {
            return "NORMAL"; // DB 크기를 알 수 없으면 NORMAL
        }

        double ratio = (double) bloatBytes / totalDatabaseSizeBytes * 100;

        if (ratio >= 30.0) {
            return "CRITICAL";
        } else if (ratio >= 10.0) {
            return "WARNING";
        } else {
            return "NORMAL";
        }
    }

    /**
     * Critical Table Severity 계산
     * - 전체 테이블 수 대비 비율로 판단
     * - 10% 이상: WARNING
     */
    private String calculateCriticalTableSeverity(Integer criticalCount, Integer totalTableCount) {
        if (totalTableCount == null || totalTableCount == 0) {
            return "NORMAL"; // 테이블 수를 알 수 없으면 NORMAL
        }

        double ratio = (double) criticalCount / totalTableCount * 100;

        if (ratio >= 10.0) {
            return "WARNING";
        } else {
            return "NORMAL";
        }
    }

    /**
     * Bloat Growth Severity 계산
     * - 증가 추세가 지속적으로 우상향하면 WARNING
     * - 현재는 단순히 증가량만 체크 (향후 추세 분석 추가 가능)
     */
    private String calculateBloatGrowthSeverity(Long growthBytes, Long currentBloatBytes) {
        if (growthBytes == null || currentBloatBytes == null || currentBloatBytes == 0) {
            return "NORMAL";
        }

        // 증가량이 현재 Bloat의 10% 이상이면 WARNING
        double growthRatio = (double) growthBytes / currentBloatBytes * 100;

        if (growthRatio >= 10.0) {
            return "WARNING";
        } else {
            return "NORMAL";
        }
    }

    /**
     * 실제 PostgreSQL 인스턴스에 연결하여 전체 DB 크기 조회
     */
    private Long getTotalDatabaseSizeFromInstance(Long databaseId, Long instanceId) {
        try {
            Database database = databaseRepository.findById(databaseId);
            if (database == null) {
                log.warn("Database not found: databaseId={}", databaseId);
                return null;
            }

            Instance instance = instanceRepository.findById(instanceId)
                    .orElse(null);
            if (instance == null) {
                log.warn("Instance not found: instanceId={}", instanceId);
                return null;
            }

            JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());
            String sql = "SELECT pg_database_size(current_database()) AS total_database_size_bytes";
            
            Long size = jdbc.queryForObject(sql, Long.class);
            log.debug("📊 전체 DB 크기 조회: {} bytes ({}:{}:{})", 
                    size, instance.getHost(), instance.getPort(), database.getDatabaseName());
            return size;

        } catch (Exception e) {
            log.error("❌ 전체 DB 크기 조회 실패: databaseId={}, instanceId={}", 
                    databaseId, instanceId, e);
            return null;
        }
    }

    /**
     * 실제 PostgreSQL 인스턴스에 연결하여 전체 테이블 수 조회
     */
    private Integer getTotalTableCountFromInstance(Long databaseId, Long instanceId) {
        try {
            Database database = databaseRepository.findById(databaseId);
            if (database == null) {
                log.warn("Database not found: databaseId={}", databaseId);
                return null;
            }

            Instance instance = instanceRepository.findById(instanceId)
                    .orElse(null);
            if (instance == null) {
                log.warn("Instance not found: instanceId={}", instanceId);
                return null;
            }

            JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());
            String sql = """
                SELECT COUNT(*) AS total_table_count
                FROM pg_stat_user_tables
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                """;
            
            Integer count = jdbc.queryForObject(sql, Integer.class);
            log.debug("📊 전체 테이블 수 조회: {} 개 ({}:{}:{})", 
                    count, instance.getHost(), instance.getPort(), database.getDatabaseName());
            return count;

        } catch (Exception e) {
            log.error("❌ 전체 테이블 수 조회 실패: databaseId={}, instanceId={}", 
                    databaseId, instanceId, e);
            return null;
        }
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