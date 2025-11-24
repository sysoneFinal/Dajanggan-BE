package com.dajanggan.domain.system.cpu.service;

import com.dajanggan.domain.system.cpu.domain.CpuAgg;
import com.dajanggan.domain.system.cpu.domain.CpuAgg5m;
import com.dajanggan.domain.system.cpu.dto.CpuDto;
import com.dajanggan.domain.system.cpu.dto.CpuListRequest;
import com.dajanggan.domain.system.cpu.repository.CpuMapper;
import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 데이터 흐름:
 * 1. 실시간 CPU: SSE로 직접 전송 (OsMetricSseService)
 * 2. PostgreSQL 메트릭: cpu_agg_1m (1분), cpu_agg_5m (5분)
 * 3. OS 메트릭 집계: os_metric_agg (1분)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CpuService {

    private final CpuMapper cpuMapper;
    private final OsMetricMapper osMetricMapper;
    private final OsMetricRedisService osMetricRedisService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * CPU 대시보드 전체 데이터 조회 (PDF 명세 기반)
     */
    public CpuDto.DashboardResponse getCpuDashboard(Long instanceId) {
        log.info("========== CPU 대시보드 데이터 조회 시작: instanceId={} ==========", instanceId);
        long startTime = System.currentTimeMillis();

        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수입니다");
        }

        try {
            // 위젯 5개 구성
            CpuDto.Widgets widgets = buildWidgets(instanceId);

            // 차트 9개 구성
            CpuDto.Charts charts = buildCharts(instanceId);

            long endTime = System.currentTimeMillis();
            log.info("========== CPU 대시보드 데이터 조회 완료: instanceId={}, 소요시간={}ms ==========",
                    instanceId, (endTime - startTime));

            return CpuDto.DashboardResponse.builder()
                    .widgets(widgets)
                    .charts(charts)
                    .build();

        } catch (Exception e) {
            log.error("CPU 대시보드 조회 오류", e);
            throw new RuntimeException("CPU 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    // ========================================
    // 위젯 빌더 (5개)
    // ========================================

    private CpuDto.Widgets buildWidgets(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime fifteenMinutesAgo = now.minusMinutes(15);

        // PostgreSQL 메트릭 (최근 15분)
        List<CpuAgg> recentCpu;
        try {
            recentCpu = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, fifteenMinutesAgo, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                recentCpu = new ArrayList<>();
            } else {
                throw e;
            }
        }
        // 최신 데이터는 첫 번째 요소 (DESC 정렬이므로)
        CpuAgg latestCpu = recentCpu.isEmpty() ? null : recentCpu.get(0);
        // 15분 전 데이터는 마지막 요소
        CpuAgg previousCpu = recentCpu.size() > 1 ? recentCpu.get(recentCpu.size() - 1) : null;

        // OS CPU 사용률 위젯은 SSE 실시간 데이터만 사용하므로 백엔드 API에서는 빈 값 반환
        // 프론트엔드에서 SSE로 받은 실시간 데이터를 사용

        return CpuDto.Widgets.builder()
                .osCpuUsage(buildOsCpuUsageWidget())  // SSE 실시간만 사용
                .postgresqlTps(buildPostgresqlTpsWidget(latestCpu, previousCpu))
                .errorRate(buildErrorRateWidget(latestCpu))
                .backendProcesses(buildBackendProcessesWidget(latestCpu))
                .loadAverage(buildLoadAverageWidget(instanceId))
                .build();
    }

    /**
     * 위젯1: OS CPU 사용률
     * 
     * 주의: 이 위젯은 SSE 실시간 데이터만 사용합니다.
     * 백엔드 API에서는 빈 값을 반환하며, 프론트엔드에서 SSE로 받은 실시간 데이터를 사용합니다.
     */
    private CpuDto.OsCpuUsageWidget buildOsCpuUsageWidget() {
        // SSE 실시간 데이터만 사용하므로 백엔드 API에서는 빈 값 반환
        // 프론트엔드에서 SSE로 받은 실시간 데이터로 위젯을 업데이트합니다.
        return CpuDto.OsCpuUsageWidget.builder()
                .current(0.0)
                .trend(0.0)
                .status("정상")
                .build();
    }

    /**
     * 위젯2: PostgreSQL TPS
     */
    private CpuDto.PostgresqlTpsWidget buildPostgresqlTpsWidget(CpuAgg latest, CpuAgg previous) {
        if (latest == null) {
            return CpuDto.PostgresqlTpsWidget.builder()
                    .current(0)
                    .trend(0)
                    .status("정상")
                    .build();
        }

        Integer current = safe(latest.getXactCommitRate()).intValue();
        Integer trend = previous != null ?
                current - safe(previous.getXactCommitRate()).intValue() : 0;
        String status = "정상"; // TPS는 일반적으로 높을수록 좋으므로 정상으로 표시

        return CpuDto.PostgresqlTpsWidget.builder()
                .current(current)
                .trend(trend)
                .status(status)
                .build();
    }

    /**
     * 위젯3: 에러율
     * 
     * 에러율 계산: (Rollback TPS / (Commit TPS + Rollback TPS)) * 100
     * - 정상: 에러율 < 1%
     * - 주의: 1% <= 에러율 < 5%
     * - 위험: 에러율 >= 5%
     */
    private CpuDto.ErrorRateWidget buildErrorRateWidget(CpuAgg latest) {
        if (latest == null) {
            return CpuDto.ErrorRateWidget.builder()
                    .rollbackTps(0)
                    .errorRate(0.0)
                    .status("정상")
                    .build();
        }

        // Double 타입으로 직접 계산하여 정밀도 유지
        Double rollbackTpsDouble = safe(latest.getXactRollbackRate());
        Double commitTpsDouble = safe(latest.getXactCommitRate());
        
        Integer rollbackTps = rollbackTpsDouble.intValue();
        Integer commitTps = commitTpsDouble.intValue();
        
        // 에러율 계산: (Rollback TPS / (Commit TPS + Rollback TPS)) * 100
        Double totalTps = commitTpsDouble + rollbackTpsDouble;
        Double errorRate = totalTps > 0 ? (rollbackTpsDouble / totalTps) * 100.0 : 0.0;
        
        // 상태 판정
        String status = errorRate < 1.0 ? "정상" : errorRate < 5.0 ? "주의" : "위험";

        return CpuDto.ErrorRateWidget.builder()
                .rollbackTps(rollbackTps)
                .errorRate(errorRate)
                .status(status)
                .build();
    }

    /**
     * 위젯4: Backend 프로세스
     */
    private CpuDto.BackendProcessesWidget buildBackendProcessesWidget(CpuAgg latest) {
        if (latest == null) {
            return CpuDto.BackendProcessesWidget.builder()
                    .clientBackend(0)
                    .autovacuum(0)
                    .parallelWorker(0)
                    .build();
        }

        return CpuDto.BackendProcessesWidget.builder()
                .clientBackend(safe(latest.getAvgClientBackend()).intValue())
                .autovacuum(safe(latest.getAvgAutovacuumWorker()).intValue())
                .parallelWorker(safe(latest.getAvgParallelWorker()).intValue())
                .build();
    }

    /**
     * 위젯5: Load Average
     * 
     * 주의: 이 위젯은 SSE 실시간 데이터만 사용합니다.
     * 백엔드 API에서는 빈 값을 반환하며, 프론트엔드에서 SSE로 받은 실시간 데이터를 사용합니다.
     * 
     * Redis에 저장된 Load Average 형식: {"1m":0.01,"5m":0.1,"15m":0.07}
     * SSE로 전송되는 형식: [1m, 5m, 15m] 리스트
     */
    private CpuDto.LoadAverageWidget buildLoadAverageWidget(Long instanceId) {
        // SSE 실시간 데이터만 사용하므로 백엔드 API에서는 빈 값 반환
        // 프론트엔드에서 SSE로 받은 실시간 데이터로 위젯을 업데이트합니다.
        return createEmptyLoadAverageWidget();
    }

    private CpuDto.LoadAverageWidget createEmptyLoadAverageWidget() {
        return CpuDto.LoadAverageWidget.builder()
                .load1m(0.0)
                .load5m(0.0)
                .load15m(0.0)
                .cpuCoreCount(8)
                .build();
    }

    private Double toDouble(Object obj) {
        if (obj == null) {
            return 0.0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    // ========================================
    // 차트 빌더 (9개)
    // ========================================

    private CpuDto.Charts buildCharts(Long instanceId) {
        // 모든 차트를 병렬로 조회
        CompletableFuture<CpuDto.OsCpuUsageTrend10m> osCpuUsageTrend10mFuture =
                CompletableFuture.supplyAsync(() -> buildOsCpuUsageTrend1h(instanceId));

        CompletableFuture<CpuDto.PostgresqlTpsTrend10m> postgresqlTpsTrend10mFuture =
                CompletableFuture.supplyAsync(() -> buildPostgresqlTpsTrend1h(instanceId));

        CompletableFuture<CpuDto.PostgresqlActiveConnections10m> postgresqlActiveConnections10mFuture =
                CompletableFuture.supplyAsync(() -> buildPostgresqlActiveConnections1h(instanceId));

        CompletableFuture<CpuDto.LoadAverageTrend15m> loadAverageTrend15mFuture =
                CompletableFuture.supplyAsync(() -> buildLoadAverageTrend24h(instanceId));

        CompletableFuture<CpuDto.ConnectionStatus1h> connectionStatus1hFuture =
                CompletableFuture.supplyAsync(() -> buildConnectionStatus1h(instanceId));

        CompletableFuture<CpuDto.TpsDailyTrend24h> tpsDailyTrend24hFuture =
                CompletableFuture.supplyAsync(() -> buildTpsDailyTrend1h(instanceId));

        CompletableFuture<CpuDto.WaitEventDistribution15m> waitEventDistribution15mFuture =
                CompletableFuture.supplyAsync(() -> buildWaitEventDistribution1h(instanceId));

        CompletableFuture<CpuDto.BackendTypeTrend24h> backendTypeTrend24hFuture =
                CompletableFuture.supplyAsync(() -> buildBackendTypeTrend1h(instanceId));

        CompletableFuture<CpuDto.ErrorRateTrend15m> errorRateTrend15mFuture =
                CompletableFuture.supplyAsync(() -> buildErrorRateTrend24h(instanceId));

        // 모든 작업이 완료될 때까지 대기
        CompletableFuture.allOf(
                osCpuUsageTrend10mFuture,
                postgresqlTpsTrend10mFuture,
                postgresqlActiveConnections10mFuture,
                loadAverageTrend15mFuture,
                connectionStatus1hFuture,
                tpsDailyTrend24hFuture,
                waitEventDistribution15mFuture,
                backendTypeTrend24hFuture,
                errorRateTrend15mFuture
        ).join();

        // 결과 조합
        return CpuDto.Charts.builder()
                .osCpuUsageTrend10m(osCpuUsageTrend10mFuture.join())
                .postgresqlTpsTrend10m(postgresqlTpsTrend10mFuture.join())
                .postgresqlActiveConnections10m(postgresqlActiveConnections10mFuture.join())
                .loadAverageTrend15m(loadAverageTrend15mFuture.join())
                .connectionStatus1h(connectionStatus1hFuture.join())
                .tpsDailyTrend24h(tpsDailyTrend24hFuture.join())
                .waitEventDistribution15m(waitEventDistribution15mFuture.join())
                .backendTypeTrend24h(backendTypeTrend24hFuture.join())
                .errorRateTrend15m(errorRateTrend15mFuture.join())
                .build();
    }

    /**
     * 차트1: OS CPU 사용률 추이 (1시간)
     * 최근 60개 데이터 포인트로 제한
     */
    private CpuDto.OsCpuUsageTrend10m buildOsCpuUsageTrend1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusHours(1);

        List<OsMetricAgg> osMetrics;
        try {
            osMetrics = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", start, endTime);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("os_metric_agg 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                osMetrics = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (osMetrics == null || osMetrics.isEmpty()) {
            return CpuDto.OsCpuUsageTrend10m.builder()
                    .categories(new ArrayList<>())
                    .data(new ArrayList<>())
                    .build();
        }

        // 최근 60개 데이터 포인트로 제한
        if (osMetrics.size() > 60) {
            osMetrics = osMetrics.subList(osMetrics.size() - 60, osMetrics.size());
        }

        List<String> categories = osMetrics.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        List<Double> data = osMetrics.stream()
                .map(m -> safe(m.getAvgValue()))
                .collect(Collectors.toList());

        return CpuDto.OsCpuUsageTrend10m.builder()
                .categories(categories)
                .data(data)
                .build();
    }

    /**
     * 차트2: PostgreSQL TPS 추이 (최근 15분)
     * 최근 15개 데이터 포인트로 제한 (1분 간격)
     * 
     * 데이터 범위: 1분 간격 × 15개 = 15분
     */
    private CpuDto.PostgresqlTpsTrend10m buildPostgresqlTpsTrend1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusMinutes(15);

        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList == null || cpuList.isEmpty()) {
            return CpuDto.PostgresqlTpsTrend10m.builder()
                    .categories(new ArrayList<>())
                    .commitTps(new ArrayList<>())
                    .rollbackTps(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        List<String> categories = cpuList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        List<Integer> commitTps = cpuList.stream()
                .map(m -> safe(m.getXactCommitRate()).intValue())
                .collect(Collectors.toList());

        List<Integer> rollbackTps = cpuList.stream()
                .map(m -> safe(m.getXactRollbackRate()).intValue())
                .collect(Collectors.toList());

        return CpuDto.PostgresqlTpsTrend10m.builder()
                .categories(categories)
                .commitTps(commitTps)
                .rollbackTps(rollbackTps)
                .build();
    }

    /**
     * 차트3: PostgreSQL 활성 연결 (최근 15분)
     * cpu_agg_1m 테이블에서 avg_active_connections만 사용
     * OS CPU 사용률은 실시간 차트로 별도 제공되므로 제거
     * 
     * 데이터 범위: 1분 간격 × 15개 = 15분
     */
    private CpuDto.PostgresqlActiveConnections10m buildPostgresqlActiveConnections1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusMinutes(15);

        // 1분 집계 데이터 사용 (최근 15개 = 15분)
        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            log.warn("차트3: cpu_agg_1m 데이터가 없습니다. instanceId={}", instanceId);
            return CpuDto.PostgresqlActiveConnections10m.builder()
                    .categories(new ArrayList<>())
                    .osCpuUsage(new ArrayList<>()) // 호환성을 위해 유지
                    .activeConnections(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        // 시간 형식: HH:mm
        List<String> categories = cpuList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // 활성 연결 수만 추출 (OS CPU는 제거)
        List<Integer> activeConnections = cpuList.stream()
                .map(m -> m.getAvgActiveConnections() != null ? m.getAvgActiveConnections().intValue() : 0)
                .collect(Collectors.toList());

        // OS CPU는 빈 리스트로 설정 (실시간 차트에서 별도 제공)
        List<Double> osCpuUsage = new ArrayList<>(Collections.nCopies(cpuList.size(), 0.0));

        return CpuDto.PostgresqlActiveConnections10m.builder()
                .categories(categories)
                .osCpuUsage(osCpuUsage) // 호환성을 위해 유지하되 빈 값
                .activeConnections(activeConnections)
                .build();
    }

    /**
     * 차트4: Load Average 추이 (24시간)
     * os_metric_agg 테이블에서 실제 OS Load Average 데이터 사용
     * - load1m: 1분 평균 Load Average
     * - load5m: 5분 평균 Load Average
     * - load15m: 15분 평균 Load Average
     * 
     * 데이터 범위: 5분 간격 × 288개 = 24시간
     * 실무 기준: 24시간 추이는 288개 데이터 포인트가 표준
     */
    private CpuDto.LoadAverageTrend15m buildLoadAverageTrend24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusHours(24);

        // os_metric_agg에서 Load Average 데이터 조회
        List<OsMetricAgg> loadAvg1mList;
        List<OsMetricAgg> loadAvg5mList;
        List<OsMetricAgg> loadAvg15mList;
        
        try {
            // Load Average 1분
            loadAvg1mList = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "LOAD_AVG_1M", start, endTime);
            // Load Average 5분
            loadAvg5mList = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "LOAD_AVG_5M", start, endTime);
            // Load Average 15분
            loadAvg15mList = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "LOAD_AVG_15M", start, endTime);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("os_metric_agg 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                loadAvg1mList = new ArrayList<>();
                loadAvg5mList = new ArrayList<>();
                loadAvg15mList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        // 데이터가 없으면 빈 응답 반환
        if (loadAvg1mList.isEmpty() && loadAvg5mList.isEmpty() && loadAvg15mList.isEmpty()) {
            return CpuDto.LoadAverageTrend15m.builder()
                    .categories(new ArrayList<>())
                    .load1m(new ArrayList<>())
                    .load5m(new ArrayList<>())
                    .load15m(new ArrayList<>())
                    .cpuCoreCount(getCpuCoreCount(instanceId))
                    .build();
        }

        // 5분 간격으로 샘플링 (24시간 = 288개 데이터 포인트)
        // os_metric_agg는 1분 간격이므로 5분마다 하나씩 추출
        List<OsMetricAgg> sampled1m = sampleEvery5Minutes(loadAvg1mList, 288);
        List<OsMetricAgg> sampled5m = sampleEvery5Minutes(loadAvg5mList, 288);
        List<OsMetricAgg> sampled15m = sampleEvery5Minutes(loadAvg15mList, 288);

        // 가장 데이터가 많은 리스트를 기준으로 categories 생성
        List<OsMetricAgg> referenceList = sampled1m.size() >= sampled5m.size() && sampled1m.size() >= sampled15m.size() 
            ? sampled1m 
            : sampled5m.size() >= sampled15m.size() ? sampled5m : sampled15m;

        List<String> categories = referenceList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // Load Average 데이터 추출
        List<Double> load1m = sampled1m.stream()
                .map(m -> safe(m.getAvgValue()))
                .collect(Collectors.toList());

        List<Double> load5m = sampled5m.stream()
                .map(m -> safe(m.getAvgValue()))
                .collect(Collectors.toList());

        List<Double> load15m = sampled15m.stream()
                .map(m -> safe(m.getAvgValue()))
                .collect(Collectors.toList());

        // 리스트 크기 맞추기 (가장 긴 리스트 기준)
        int maxSize = Math.max(load1m.size(), Math.max(load5m.size(), load15m.size()));
        while (load1m.size() < maxSize) load1m.add(0.0);
        while (load5m.size() < maxSize) load5m.add(0.0);
        while (load15m.size() < maxSize) load15m.add(0.0);

        return CpuDto.LoadAverageTrend15m.builder()
                .categories(categories)
                .load1m(load1m)
                .load5m(load5m)
                .load15m(load15m)
                .cpuCoreCount(getCpuCoreCount(instanceId))
                .build();
    }

    /**
     * 1분 간격 데이터를 5분 간격으로 샘플링
     * 실무 표준: 24시간 차트는 5분 간격 288개 데이터 포인트 사용
     */
    private List<OsMetricAgg> sampleEvery5Minutes(List<OsMetricAgg> data, int maxPoints) {
        if (data.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 5분마다 하나씩 샘플링
        List<OsMetricAgg> sampled = new ArrayList<>();
        for (int i = 0; i < data.size(); i += 5) {
            sampled.add(data.get(i));
            if (sampled.size() >= maxPoints) {
                break;
            }
        }
        
        // 최근 데이터로 제한
        if (sampled.size() > maxPoints) {
            return sampled.subList(sampled.size() - maxPoints, sampled.size());
        }
        
        return sampled;
    }

    /**
     * CPU 코어 수 조회 (Redis에서)
     */
    private int getCpuCoreCount(Long instanceId) {
        try {
            RedisOsMetricData latestCpu = osMetricRedisService.getLatestMetric(instanceId, "CPU");

            if (latestCpu != null && latestCpu.getDetails() != null) {
                Object perCoreUsageObj = latestCpu.getDetails().get("perCoreUsage");
                if (perCoreUsageObj instanceof List) {
                    return ((List<?>) perCoreUsageObj).size();
                }
            }
        } catch (Exception e) {
            log.debug("CPU 코어 수 조회 실패, 기본값 사용: instanceId={}", instanceId);
        }
        return 8; // 기본값
    }

    /**
     * 차트5: 연결 상태 분포 (최근 15분)
     * cpu_agg_1m 테이블에서 avg_active_connections, avg_idle_connections, avg_idle_in_transaction 사용
     * 
     * 데이터 범위: 1분 간격 × 15개 = 15분
     */
    private CpuDto.ConnectionStatus1h buildConnectionStatus1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusMinutes(15);

        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            return CpuDto.ConnectionStatus1h.builder()
                    .categories(new ArrayList<>())
                    .active(new ArrayList<>())
                    .idle(new ArrayList<>())
                    .idleInTx(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        // 시간 형식: HH:mm
        List<String> categories = cpuList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // Active Connections
        List<Integer> active = cpuList.stream()
                .map(m -> safe(m.getAvgActiveConnections()).intValue())
                .collect(Collectors.toList());

        // Idle Connections
        List<Integer> idle = cpuList.stream()
                .map(m -> safe(m.getAvgIdleConnections()).intValue())
                .collect(Collectors.toList());

        // Idle in Transaction
        List<Integer> idleInTx = cpuList.stream()
                .map(m -> m.getAvgIdleInTransaction() != null ? m.getAvgIdleInTransaction().intValue() : 0)
                .collect(Collectors.toList());

        return CpuDto.ConnectionStatus1h.builder()
                .categories(categories)
                .active(active)
                .idle(idle)
                .idleInTx(idleInTx)
                .build();
    }

    /**
     * 차트6: TPS 일일 추이 (최근 15분)
     * cpu_agg_1m 테이블에서 xact_commit_rate, xact_rollback_rate 사용
     * 
     * 데이터 범위: 1분 간격 × 15개 = 15분
     */
    private CpuDto.TpsDailyTrend24h buildTpsDailyTrend1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusMinutes(15);

        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            return CpuDto.TpsDailyTrend24h.builder()
                    .categories(new ArrayList<>())
                    .commitTps(new ArrayList<>())
                    .rollbackTps(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        // 시간 형식: HH:mm
        List<String> categories = cpuList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // Commit TPS: xact_commit_rate 사용
        List<Integer> commitTps = cpuList.stream()
                .map(m -> safe(m.getXactCommitRate()).intValue())
                .collect(Collectors.toList());

        // Rollback TPS: xact_rollback_rate 사용
        List<Integer> rollbackTps = cpuList.stream()
                .map(m -> safe(m.getXactRollbackRate()).intValue())
                .collect(Collectors.toList());

        return CpuDto.TpsDailyTrend24h.builder()
                .categories(categories)
                .commitTps(commitTps)
                .rollbackTps(rollbackTps)
                .build();
    }

    /**
     * 차트7: Wait Event 유형별 분포 (최근 15분)
     * cpu_agg_1m 테이블에서 avg_waiting_for_lock, avg_waiting_for_io, avg_wait_event_client,
     * avg_wait_event_activity, avg_wait_event_lwlock, avg_wait_event_bufferpin + avg_wait_event_timeout + avg_wait_event_ipc 사용
     * 
     * 데이터 범위: 1분 간격 × 15개 = 15분
     */
    private CpuDto.WaitEventDistribution15m buildWaitEventDistribution1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusMinutes(15);

        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            return CpuDto.WaitEventDistribution15m.builder()
                    .categories(new ArrayList<>())
                    .lock(new ArrayList<>())
                    .io(new ArrayList<>())
                    .client(new ArrayList<>())
                    .activity(new ArrayList<>())
                    .lwlock(new ArrayList<>())
                    .other(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        // 시간 형식: HH:mm
        List<String> categories = cpuList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // CpuAgg에 있는 실제 Wait Event 데이터 사용
        // Lock: avg_waiting_for_lock
        List<Integer> lock = cpuList.stream()
                .map(m -> safe(m.getAvgWaitingForLock()).intValue())
                .collect(Collectors.toList());

        // IO: avg_waiting_for_io
        List<Integer> io = cpuList.stream()
                .map(m -> safe(m.getAvgWaitingForIo()).intValue())
                .collect(Collectors.toList());

        // Client: avg_wait_event_client
        List<Integer> client = cpuList.stream()
                .map(m -> safe(m.getAvgWaitEventClient()).intValue())
                .collect(Collectors.toList());

        // Activity: avg_wait_event_activity
        List<Integer> activity = cpuList.stream()
                .map(m -> safe(m.getAvgWaitEventActivity()).intValue())
                .collect(Collectors.toList());

        // LWLock: avg_wait_event_lwlock
        List<Integer> lwlock = cpuList.stream()
                .map(m -> safe(m.getAvgWaitEventLwlock()).intValue())
                .collect(Collectors.toList());

        // Other: avg_wait_event_bufferpin + avg_wait_event_timeout + avg_wait_event_ipc
        List<Integer> other = cpuList.stream()
                .map(m -> safe(m.getAvgWaitEventBufferpin()).intValue() +
                        safe(m.getAvgWaitEventTimeout()).intValue() +
                        safe(m.getAvgWaitEventIpc()).intValue())
                .collect(Collectors.toList());

        return CpuDto.WaitEventDistribution15m.builder()
                .categories(categories)
                .lock(lock)
                .io(io)
                .client(client)
                .activity(activity)
                .lwlock(lwlock)
                .other(other)
                .build();
    }

    /**
     * 차트8: Backend 유형별 추이 (최근 15분)
     * cpu_agg_1m 테이블에서 avg_client_backend, avg_autovacuum_worker, avg_parallel_worker, avg_background_worker 사용
     * 
     * 데이터 범위: 1분 간격 × 15개 = 15분
     */
    private CpuDto.BackendTypeTrend24h buildBackendTypeTrend1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusMinutes(15);

        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            return CpuDto.BackendTypeTrend24h.builder()
                    .categories(new ArrayList<>())
                    .client(new ArrayList<>())
                    .autovacuum(new ArrayList<>())
                    .parallel(new ArrayList<>())
                    .background(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        // 시간 형식: HH:mm
        List<String> categories = cpuList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // Client Backend: avg_client_backend
        List<Integer> client = cpuList.stream()
                .map(m -> safe(m.getAvgClientBackend()).intValue())
                .collect(Collectors.toList());

        // Autovacuum Worker: avg_autovacuum_worker
        List<Integer> autovacuum = cpuList.stream()
                .map(m -> safe(m.getAvgAutovacuumWorker()).intValue())
                .collect(Collectors.toList());

        // Parallel Worker: avg_parallel_worker
        List<Integer> parallel = cpuList.stream()
                .map(m -> safe(m.getAvgParallelWorker()).intValue())
                .collect(Collectors.toList());

        // Background Worker: avg_background_worker
        List<Integer> background = cpuList.stream()
                .map(m -> safe(m.getAvgBackgroundWorker()).intValue())
                .collect(Collectors.toList());

        return CpuDto.BackendTypeTrend24h.builder()
                .categories(categories)
                .client(client)
                .autovacuum(autovacuum)
                .parallel(parallel)
                .background(background)
                .build();
    }

    /**
     * 차트9: 에러율 추이 (최근 15분)
     * cpu_agg_1m 테이블에서 (xact_rollback_rate / (xact_commit_rate + xact_rollback_rate)) * 100로 에러율 계산
     * 
     * 데이터 범위: 1분 간격 × 15개 = 15분
     */
    private CpuDto.ErrorRateTrend15m buildErrorRateTrend24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
        OffsetDateTime start = now.minusMinutes(15);

        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, endTime, 15);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            return CpuDto.ErrorRateTrend15m.builder()
                    .categories(new ArrayList<>())
                    .data(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        // 시간 형식: HH:mm
        List<String> categories = cpuList.stream()
                .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // 에러율 계산: (xact_rollback_rate / (xact_commit_rate + xact_rollback_rate)) * 100
        // 
        // 계산 방식:
        // - xact_commit_rate: 초당 커밋 트랜잭션 수 (TPS)
        // - xact_rollback_rate: 초당 롤백 트랜잭션 수
        // - 전체 TPS = xact_commit_rate + xact_rollback_rate
        // - 에러율 = (롤백 TPS / 전체 TPS) * 100
        List<Double> data = cpuList.stream()
                .map(m -> {
                    Double commitRate = safe(m.getXactCommitRate());
                    Double rollbackRate = safe(m.getXactRollbackRate());
                    Double totalTps = commitRate + rollbackRate;

                    // 에러율 계산: (롤백 TPS / 전체 TPS) * 100
                    // 0으로 나누기 방지
                    if (totalTps > 0) {
                        return (rollbackRate / totalTps) * 100.0;
                    }
                    return 0.0;
                })
                .collect(Collectors.toList());

        return CpuDto.ErrorRateTrend15m.builder()
                .categories(categories)
                .data(data)
                .build();
    }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * CPU 리스트 조회 (페이징 지원)
     */
    public CpuDto.ListResponse getCpuList(Long instanceId, CpuListRequest request) {
        log.debug("CPU 리스트 조회 - instanceId: {}, request: {}", instanceId, request);

        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수입니다");
        }

        try {
            // 페이징 파라미터 설정 (기본값: page=0, size=20)
            int page = request.getPage() != null ? request.getPage() : 0;
            int size = request.getSize() != null ? request.getSize() : 20;
            if (page < 0) page = 0;
            if (size < 1) size = 20;
            if (size > 100) size = 100; // 최대 100개로 제한
            int offset = page * size;

            OffsetDateTime endTime = OffsetDateTime.now();
            OffsetDateTime startTime = calculateStartTime(endTime, request.getTimeRange());

            // 상태 필터 파싱
            List<String> statusList = null;
            if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
                statusList = Arrays.stream(request.getStatus().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            // 총 개수 조회
            Long totalCount;
            try {
                totalCount = cpuMapper.countCpuListWithFilter(instanceId, startTime, endTime, statusList);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                    log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                    totalCount = 0L;
                } else {
                    throw e;
                }
            }

            // 페이징된 데이터 조회
            List<CpuAgg> cpuList;
            try {
                cpuList = cpuMapper.selectCpuListWithFilterAndPaging(instanceId, startTime, endTime, statusList, offset, size);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                    log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                    cpuList = new ArrayList<>();
                } else {
                    throw e;
                }
            }

            // OS 메트릭 (필요한 경우에만 조회 - 성능 최적화)
            List<OsMetricAgg> osMetrics = new ArrayList<>();
            if (!cpuList.isEmpty()) {
                try {
                    // CPU 리스트의 시간 범위에 맞춰 OS 메트릭 조회
                    OffsetDateTime osStartTime = cpuList.stream()
                            .map(CpuAgg::getCollectedAt)
                            .min(OffsetDateTime::compareTo)
                            .orElse(startTime);
                    OffsetDateTime osEndTime = cpuList.stream()
                            .map(CpuAgg::getCollectedAt)
                            .max(OffsetDateTime::compareTo)
                            .orElse(endTime);
                    
                    osMetrics = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", osStartTime, osEndTime);
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                        log.warn("os_metric_agg 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                    } else {
                        log.warn("OS 메트릭 조회 실패 (계속 진행): {}", e.getMessage());
                    }
                }
            }

            List<CpuDto.CpuListItem> items = buildCpuListItems(cpuList, osMetrics, request.getStatus());

            // 총 페이지 수 계산
            int totalPages = (int) Math.ceil((double) totalCount / size);

            return CpuDto.ListResponse.builder()
                    .data(items)
                    .total(totalCount.intValue())
                    .page(page)
                    .size(size)
                    .totalPages(totalPages)
                    .build();

        } catch (Exception e) {
            log.error("CPU 리스트 조회 오류", e);
            throw new RuntimeException("CPU 리스트 조회 실패: " + e.getMessage(), e);
        }
    }

    // ========================================
    // Widget & Chart Builders
    // ========================================

    /**
     * CPU 게이지 위젯 (PostgreSQL 데이터)
     */
    private CpuDto.CpuUsageWidget buildCpuUsageWidget(Long instanceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
            List<CpuAgg> recent = cpuMapper.selectCpuAggByTimeRange(
                    instanceId, now.minusMinutes(1), endTime);

            if (recent == null || recent.isEmpty()) {
                return CpuDto.CpuUsageWidget.builder()
                        .value(0.0)
                        .description("데이터 없음")
                        .runningQueries(0)
                        .waitingQueries(0)
                        .idleConnections(0)
                        .build();
            }

            CpuAgg latest = recent.get(recent.size() - 1);

            return CpuDto.CpuUsageWidget.builder()
                    .value(70.0) // 임시 값 (프론트엔드에서 SSE로 실시간 업데이트)
                    .description("정상 범위")
                    .runningQueries(safe(latest.getAvgClientBackend()).intValue())
                    .waitingQueries(safe(latest.getAvgWaitingSessions()).intValue())
                    .idleConnections(safe(latest.getAvgIdleConnections()).intValue())
                    .build();

        } catch (Exception e) {
            log.error("CPU 게이지 빌드 오류", e);
            return CpuDto.CpuUsageWidget.builder().value(0.0).description("오류").build();
        }
    }

    /**
     * CPU 사용률 1분 추이 (60개 데이터 포인트)
     */
    private CpuDto.CpuUsageTrend buildCpuUsageTrend(Long instanceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime start = now.minusMinutes(60);

            List<OsMetricAgg> osMetrics;
            try {
                OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
                osMetrics = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", start, endTime);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                    log.warn("os_metric_agg 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                    osMetrics = new ArrayList<>();
                } else {
                    throw e;
                }
            }

            if (osMetrics == null || osMetrics.isEmpty()) {
                return CpuDto.CpuUsageTrend.builder()
                        .categories(new ArrayList<>())
                        .data(new ArrayList<>())
                        .build();
            }

            List<String> categories = osMetrics.stream()
                    .map(m -> m.getCollectedAt().format(TIME_FORMATTER))
                    .collect(Collectors.toList());

            List<Double> data = osMetrics.stream()
                    .map(m -> safe(m.getAvgValue()))
                    .collect(Collectors.toList());

            return CpuDto.CpuUsageTrend.builder()
                    .categories(categories)
                    .data(data)
                    .build();

        } catch (Exception e) {
            log.error("CPU 추이 빌드 오류", e);
            return CpuDto.CpuUsageTrend.builder()
                    .categories(new ArrayList<>())
                    .data(new ArrayList<>())
                    .build();
        }
    }

    /**
     * CPU 부하 유형별 분석 (24시간, 5분 집계)
     */
    private CpuDto.CpuLoadTypes buildCpuLoadTypes(Long instanceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime start = now.minusHours(24);

            OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
            List<CpuAgg5m> cpuAgg5mList = cpuMapper.selectCpuAgg5mByTimeRange(instanceId, start, endTime);

            if (cpuAgg5mList == null || cpuAgg5mList.isEmpty()) {
                return createEmptyCpuLoadTypes();
            }

            List<String> categories = cpuAgg5mList.stream()
                    .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                    .collect(Collectors.toList());

            // Backend별 CPU 계산 (현재는 연결 수 기반 임시 데이터)
            List<Double> postgresqlBackend = cpuAgg5mList.stream()
                    .map(m -> safe(m.getAvgActiveConnections().doubleValue()) * 0.3)
                    .collect(Collectors.toList());

            List<Double> bgWriter = cpuAgg5mList.stream()
                    .map(m -> 5.0) // 임시
                    .collect(Collectors.toList());

            List<Double> autoVacuum = cpuAgg5mList.stream()
                    .map(m -> 3.0) // 임시
                    .collect(Collectors.toList());

            List<Double> checkpoint = cpuAgg5mList.stream()
                    .map(m -> 2.0) // 임시
                    .collect(Collectors.toList());

            return CpuDto.CpuLoadTypes.builder()
                    .categories(categories)
                    .postgresqlBackend(postgresqlBackend)
                    .bgWriter(bgWriter)
                    .autoVacuum(autoVacuum)
                    .checkpoint(checkpoint)
                    .build();

        } catch (Exception e) {
            log.error("CPU 부하 유형 빌드 오류", e);
            return createEmptyCpuLoadTypes();
        }
    }

    /**
     * I/O Wait vs Disk Latency 상관관계
     */
    private CpuDto.IoWaitVsLatency buildIoWaitVsLatency(Long instanceId) {
        try {
            // 임시 샘플 데이터
            List<CpuDto.Point> normal = Arrays.asList(
                    CpuDto.Point.builder().x(2.0).y(5.0).build(),
                    CpuDto.Point.builder().x(3.0).y(7.0).build(),
                    CpuDto.Point.builder().x(4.0).y(6.0).build()
            );

            List<CpuDto.Point> warning = Arrays.asList(
                    CpuDto.Point.builder().x(8.0).y(15.0).build(),
                    CpuDto.Point.builder().x(10.0).y(18.0).build()
            );

            List<CpuDto.Point> danger = Arrays.asList(
                    CpuDto.Point.builder().x(20.0).y(35.0).build(),
                    CpuDto.Point.builder().x(25.0).y(40.0).build()
            );

            return CpuDto.IoWaitVsLatency.builder()
                    .normal(normal)
                    .warning(warning)
                    .danger(danger)
                    .build();

        } catch (Exception e) {
            log.error("I/O Wait vs Latency 빌드 오류", e);
            return CpuDto.IoWaitVsLatency.builder()
                    .normal(new ArrayList<>())
                    .warning(new ArrayList<>())
                    .danger(new ArrayList<>())
                    .build();
        }
    }

    /**
     * Backend 프로세스 타입별 분포
     */
    private CpuDto.BackendProcessStats buildBackendProcessStats(Long instanceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
            List<CpuAgg> recent = cpuMapper.selectCpuAggByTimeRange(
                    instanceId, now.minusMinutes(1), endTime);

            if (recent == null || recent.isEmpty()) {
                return createEmptyBackendProcessStats();
            }

            CpuAgg latest = recent.get(recent.size() - 1);

            List<String> types = Arrays.asList("Client", "Autovacuum", "Parallel", "Background");

            int clientActive = safe(latest.getAvgClientBackend()).intValue();
            int autovacuumActive = safe(latest.getAvgAutovacuumWorker()).intValue();
            int parallelActive = safe(latest.getAvgParallelWorker()).intValue();
            int backgroundActive = 2; // 임시

            List<Integer> activeCount = Arrays.asList(clientActive, autovacuumActive, parallelActive, backgroundActive);
            List<Integer> idleCount = Arrays.asList(5, 0, 0, 0); // 임시
            List<Integer> totalCount = Arrays.asList(
                    clientActive + 5,
                    autovacuumActive,
                    parallelActive,
                    backgroundActive
            );

            List<String> colors = Arrays.asList("#8E79FF", "#77B2FB", "#51DAA8", "#FEA29B");

            return CpuDto.BackendProcessStats.builder()
                    .types(types)
                    .activeCount(activeCount)
                    .idleCount(idleCount)
                    .totalCount(totalCount)
                    .colors(colors)
                    .build();

        } catch (Exception e) {
            log.error("Backend 프로세스 통계 빌드 오류", e);
            return createEmptyBackendProcessStats();
        }
    }

    /**
     * 대기 유형별 비중 변화
     */
    private CpuDto.WaitEventDistribution buildWaitEventDistribution(Long instanceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime start = now.minusHours(24);

            OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가
            List<CpuAgg5m> cpuAgg5mList = cpuMapper.selectCpuAgg5mByTimeRange(instanceId, start, endTime);

            if (cpuAgg5mList == null || cpuAgg5mList.isEmpty()) {
                return createEmptyWaitEventDistribution();
            }

            List<String> categories = cpuAgg5mList.stream()
                    .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                    .collect(Collectors.toList());

            // Wait Event 비율 계산 (현재는 임시 데이터)
            List<Double> cpu = cpuAgg5mList.stream()
                    .map(m -> 40.0) // 임시
                    .collect(Collectors.toList());

            List<Double> client = cpuAgg5mList.stream()
                    .map(m -> 25.0) // 임시
                    .collect(Collectors.toList());

            List<Double> io = cpuAgg5mList.stream()
                    .map(m -> 20.0) // 임시
                    .collect(Collectors.toList());

            List<Double> lock = cpuAgg5mList.stream()
                    .map(m -> 10.0) // 임시
                    .collect(Collectors.toList());

            List<Double> other = cpuAgg5mList.stream()
                    .map(m -> 5.0) // 임시
                    .collect(Collectors.toList());

            return CpuDto.WaitEventDistribution.builder()
                    .categories(categories)
                    .cpu(cpu)
                    .client(client)
                    .io(io)
                    .lock(lock)
                    .other(other)
                    .build();

        } catch (Exception e) {
            log.error("Wait Event 분포 빌드 오류", e);
            return createEmptyWaitEventDistribution();
        }
    }

    /**
     * 최근 통계 (사이드바)
     */
    private CpuDto.RecentStats buildRecentStats(Long instanceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime endTime = now.plusMinutes(1); // 최신 데이터 포함을 위해 1분 추가

            // PostgreSQL 메트릭
            List<CpuAgg> cpuRecent = cpuMapper.selectCpuAggByTimeRange(
                    instanceId, now.minusMinutes(1), endTime);

            // OS 메트릭
            List<OsMetricAgg> osRecent = osMetricMapper.findAggByInstanceAndPeriod(
                    instanceId, "CPU", now.minusMinutes(1), endTime);

            if ((cpuRecent == null || cpuRecent.isEmpty()) &&
                    (osRecent == null || osRecent.isEmpty())) {
                return createEmptyRecentStats();
            }

            CpuAgg cpuLatest = cpuRecent != null && !cpuRecent.isEmpty() ?
                    cpuRecent.get(cpuRecent.size() - 1) : null;

            CpuDto.LoadAverage loadAverage = CpuDto.LoadAverage.builder()
                    .one(1.5)
                    .five(1.8)
                    .fifteen(2.0)
                    .build();

            CpuDto.Connections connections = CpuDto.Connections.builder()
                    .active(cpuLatest != null ? safe(cpuLatest.getAvgActiveConnections()).intValue() : 0)
                    .idle(cpuLatest != null ? safe(cpuLatest.getAvgIdleConnections()).intValue() : 0)
                    .total(cpuLatest != null ? safe(cpuLatest.getAvgTotalConnections()).intValue() : 0)
                    .build();

            return CpuDto.RecentStats.builder()
                    .loadAverage(loadAverage)
                    .ioWait(5.0)
                    .connections(connections)
                    .idleCpu(30.0)
                    .contextSwitches(15000L)
                    .postgresqlBackendCpu(45.0)
                    .build();

        } catch (Exception e) {
            log.error("최근 통계 빌드 오류", e);
            return createEmptyRecentStats();
        }
    }

    // ========================================
    // List Builders
    // ========================================

    private List<CpuDto.CpuListItem> buildCpuListItems(
            List<CpuAgg> cpuList,
            List<OsMetricAgg> osMetrics,
            String statusFilter) {

        if (cpuList == null || cpuList.isEmpty()) {
            return new ArrayList<>();
        }

        // OS 메트릭을 Map으로 변환 (시간 기준 매칭)
        Map<OffsetDateTime, OsMetricAgg> osMap = new HashMap<>();
        if (osMetrics != null) {
            for (OsMetricAgg os : osMetrics) {
                osMap.put(os.getCollectedAt(), os);
            }
        }

        List<CpuDto.CpuListItem> items = new ArrayList<>();

        for (CpuAgg cpu : cpuList) {
            OsMetricAgg os = findNearestOs(cpu.getCollectedAt(), osMap);

            String status = "정상"; // 임시

            // 상태 필터링
            if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.contains(status)) {
                continue;
            }

            items.add(CpuDto.CpuListItem.builder()
                    .id(UUID.randomUUID().toString())
                    .time(cpu.getCollectedAt().format(DATETIME_FORMATTER))
                    .totalCPU(os != null ? safe(os.getAvgValue()) : 0.0)
                    .userCPU(os != null ? safe(os.getAvgValue()) * 0.6 : 0.0)
                    .systemCPU(os != null ? safe(os.getAvgValue()) * 0.3 : 0.0)
                    .idleCPU(os != null ? 100.0 - safe(os.getAvgValue()) : 100.0)
                    .ioWait(5.0)
                    .stealCPU(0.0)
                    .loadAvg1(1.5)
                    .loadAvg5(1.8)
                    .loadAvg15(2.0)
                    .activeSessions(safe(cpu.getAvgActiveConnections()).intValue())
                    .parallelWorkers(safe(cpu.getAvgParallelWorker()).intValue())
                    .waitingSessions(safe(cpu.getAvgWaitingSessions()).intValue())
                    .workerTime(0.0)
                    .contextSwitches(15000L)
                    .status(status)
                    .build());
        }

        return items;
    }

    // ========================================
    // Empty Builders
    // ========================================

    private CpuDto.CpuLoadTypes createEmptyCpuLoadTypes() {
        return CpuDto.CpuLoadTypes.builder()
                .categories(new ArrayList<>())
                .postgresqlBackend(new ArrayList<>())
                .bgWriter(new ArrayList<>())
                .autoVacuum(new ArrayList<>())
                .checkpoint(new ArrayList<>())
                .build();
    }

    private CpuDto.BackendProcessStats createEmptyBackendProcessStats() {
        return CpuDto.BackendProcessStats.builder()
                .types(new ArrayList<>())
                .activeCount(new ArrayList<>())
                .idleCount(new ArrayList<>())
                .totalCount(new ArrayList<>())
                .colors(new ArrayList<>())
                .build();
    }

    private CpuDto.WaitEventDistribution createEmptyWaitEventDistribution() {
        return CpuDto.WaitEventDistribution.builder()
                .categories(new ArrayList<>())
                .cpu(new ArrayList<>())
                .client(new ArrayList<>())
                .io(new ArrayList<>())
                .lock(new ArrayList<>())
                .other(new ArrayList<>())
                .build();
    }

    private CpuDto.RecentStats createEmptyRecentStats() {
        return CpuDto.RecentStats.builder()
                .loadAverage(CpuDto.LoadAverage.builder().one(0.0).five(0.0).fifteen(0.0).build())
                .ioWait(0.0)
                .connections(CpuDto.Connections.builder().active(0).idle(0).total(0).build())
                .idleCpu(0.0)
                .contextSwitches(0L)
                .postgresqlBackendCpu(0.0)
                .build();
    }

    // ========================================
    // Utilities
    // ========================================

    private Double safe(Double value) {
        return value != null ? value : 0.0;
    }

    private OffsetDateTime calculateStartTime(OffsetDateTime endTime, String timeRange) {
        if (timeRange == null || timeRange.isEmpty()) {
            timeRange = "7d";
        }

        return switch (timeRange) {
            case "1h" -> endTime.minusHours(1);
            case "6h" -> endTime.minusHours(6);
            case "24h" -> endTime.minusHours(24);
            case "7d" -> endTime.minusDays(7);
            default -> endTime.minusDays(7);
        };
    }

    private OsMetricAgg findNearestOs(OffsetDateTime targetTime, Map<OffsetDateTime, OsMetricAgg> osMap) {
        if (osMap.isEmpty()) {
            return null;
        }

        // 정확히 일치
        if (osMap.containsKey(targetTime)) {
            return osMap.get(targetTime);
        }

        // 가장 가까운 시간 (3분 이내)
        return osMap.entrySet().stream()
                .filter(entry -> Math.abs(targetTime.until(entry.getKey(),
                        java.time.temporal.ChronoUnit.MINUTES)) < 3)
                .min(Comparator.comparingLong(entry ->
                        Math.abs(targetTime.until(entry.getKey(),
                                java.time.temporal.ChronoUnit.SECONDS))))
                .map(Map.Entry::getValue)
                .orElse(null);
    }
}
