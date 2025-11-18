package com.dajanggan.domain.system.cpu.service;

import com.dajanggan.domain.system.cpu.domain.CpuAgg;
import com.dajanggan.domain.system.cpu.domain.CpuAgg5m;
import com.dajanggan.domain.system.cpu.dto.CpuDto;
import com.dajanggan.domain.system.cpu.repository.CpuMapper;
import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
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
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime oneMinuteAgo = now.minusMinutes(1);
        OffsetDateTime twoMinutesAgo = now.minusMinutes(2);

        // PostgreSQL 메트릭 (최신 2분)
        List<CpuAgg> recentCpu;
        try {
            recentCpu = cpuMapper.selectCpuAggByTimeRange(instanceId, twoMinutesAgo, now);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                recentCpu = new ArrayList<>();
            } else {
                throw e;
            }
        }
        CpuAgg latestCpu = recentCpu.isEmpty() ? null : recentCpu.get(recentCpu.size() - 1);
        CpuAgg previousCpu = recentCpu.size() > 1 ? recentCpu.get(recentCpu.size() - 2) : null;

        // OS 메트릭 (최신 2분)
        List<OsMetricAgg> recentOs;
        try {
            recentOs = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", twoMinutesAgo, now);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("os_metric_agg 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                recentOs = new ArrayList<>();
            } else {
                throw e;
            }
        }
        OsMetricAgg latestOs = recentOs.isEmpty() ? null : recentOs.get(recentOs.size() - 1);
        OsMetricAgg previousOs = recentOs.size() > 1 ? recentOs.get(recentOs.size() - 2) : null;

        return CpuDto.Widgets.builder()
                .osCpuUsage(buildOsCpuUsageWidget(latestOs, previousOs))
                .postgresqlTps(buildPostgresqlTpsWidget(latestCpu, previousCpu))
                .errorRate(buildErrorRateWidget(latestCpu))
                .backendProcesses(buildBackendProcessesWidget(latestCpu))
                .loadAverage(buildLoadAverageWidget(instanceId))
                .build();
    }

    /**
     * 위젯1: OS CPU 사용률
     */
    private CpuDto.OsCpuUsageWidget buildOsCpuUsageWidget(OsMetricAgg latest, OsMetricAgg previous) {
        if (latest == null) {
            return CpuDto.OsCpuUsageWidget.builder()
                    .current(0.0)
                    .trend(0.0)
                    .status("정상")
                    .build();
        }

        Double current = safe(latest.getAvgValue());
        Double trend = previous != null ? current - safe(previous.getAvgValue()) : 0.0;
        String status = current < 70 ? "정상" : current < 90 ? "주의" : "위험";

        return CpuDto.OsCpuUsageWidget.builder()
                .current(current)
                .trend(trend)
                .status(status)
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
     */
    private CpuDto.ErrorRateWidget buildErrorRateWidget(CpuAgg latest) {
        if (latest == null) {
            return CpuDto.ErrorRateWidget.builder()
                    .rollbackTps(0)
                    .errorRate(0.0)
                    .status("정상")
                    .build();
        }

        Integer rollbackTps = safe(latest.getXactRollbackRate()).intValue();
        Integer commitTps = safe(latest.getXactCommitRate()).intValue();
        Double errorRate = (commitTps + rollbackTps) > 0 ?
                (rollbackTps.doubleValue() / (commitTps + rollbackTps)) * 100 : 0.0;
        String status = errorRate < 1 ? "정상" : errorRate < 5 ? "주의" : "위험";

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
     * Redis에서 실시간 loadAverage 데이터 조회
     * 프론트엔드 SSE에서도 실시간 업데이트됨
     */
    private CpuDto.LoadAverageWidget buildLoadAverageWidget(Long instanceId) {
        try {
            // Redis에서 최신 CPU 메트릭 조회
            RedisOsMetricData latestCpu = osMetricRedisService.getLatestMetric(instanceId, "CPU");

            if (latestCpu == null || latestCpu.getDetails() == null) {
                log.debug("Redis에서 CPU 메트릭을 찾을 수 없음: instanceId={}", instanceId);
                return createEmptyLoadAverageWidget();
            }

            Map<String, Object> details = latestCpu.getDetails();

            // loadAverage 추출 (맵 구조: {one: 1.5, five: 1.8, fifteen: 2.0})
            Object loadAvgObj = details.get("loadAverage");
            Double load1m = 0.0;
            Double load5m = 0.0;
            Double load15m = 0.0;

            if (loadAvgObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> loadAvgMap = (Map<String, Object>) loadAvgObj;
                load1m = toDouble(loadAvgMap.get("one"));
                load5m = toDouble(loadAvgMap.get("five"));
                load15m = toDouble(loadAvgMap.get("fifteen"));
            }

            // CPU 코어 수 추출 (perCoreUsage 배열 길이)
            int cpuCoreCount = 8; // 기본값
            Object perCoreUsageObj = details.get("perCoreUsage");
            if (perCoreUsageObj instanceof List) {
                cpuCoreCount = ((List<?>) perCoreUsageObj).size();
            }

            return CpuDto.LoadAverageWidget.builder()
                    .load1m(load1m)
                    .load5m(load5m)
                    .load15m(load15m)
                    .cpuCoreCount(cpuCoreCount)
                    .build();

        } catch (Exception e) {
            log.error("Load Average 위젯 빌드 오류: instanceId={}", instanceId, e);
            return createEmptyLoadAverageWidget();
        }
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
                CompletableFuture.supplyAsync(() -> buildOsCpuVsActiveConnections24h(instanceId));

        CompletableFuture<CpuDto.LoadAverageTrend15m> loadAverageTrend15mFuture =
                CompletableFuture.supplyAsync(() -> buildLoadAverageTrend24h(instanceId));

        CompletableFuture<CpuDto.ConnectionStatus1h> connectionStatus1hFuture =
                CompletableFuture.supplyAsync(() -> buildConnectionStatus24h(instanceId));

        CompletableFuture<CpuDto.TpsDailyTrend24h> tpsDailyTrend24hFuture =
                CompletableFuture.supplyAsync(() -> buildTpsDailyTrend24h(instanceId));

        CompletableFuture<CpuDto.WaitEventDistribution15m> waitEventDistribution15mFuture =
                CompletableFuture.supplyAsync(() -> buildWaitEventDistribution24h(instanceId));

        CompletableFuture<CpuDto.BackendTypeTrend24h> backendTypeTrend24hFuture =
                CompletableFuture.supplyAsync(() -> buildBackendTypeTrend24h(instanceId));

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
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(1);

        List<OsMetricAgg> osMetrics;
        try {
            osMetrics = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", start, now);
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
     * 차트2: PostgreSQL TPS 추이 (1시간)
     * 최근 60개 데이터 포인트로 제한
     */
    private CpuDto.PostgresqlTpsTrend10m buildPostgresqlTpsTrend1h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(1);

        List<CpuAgg> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAggByTimeRangeWithLimit(instanceId, start, now, 60);
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
     * 차트3: OS CPU vs PostgreSQL 활성 연결 (24시간)
     * 최근 200개 데이터 포인트로 제한
     */
    private CpuDto.PostgresqlActiveConnections10m buildOsCpuVsActiveConnections24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(24);

        // 5분 집계 데이터 사용 (최근 200개)
        List<CpuAgg5m> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAgg5mByTimeRangeWithLimit(instanceId, start, now, 200);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_5m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            log.warn("차트3: cpu_agg_5m 데이터가 없습니다. instanceId={}", instanceId);
            return CpuDto.PostgresqlActiveConnections10m.builder()
                    .categories(new ArrayList<>())
                    .osCpuUsage(new ArrayList<>())
                    .activeConnections(new ArrayList<>())
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        List<String> categories = cpuList.stream()
                .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                .collect(Collectors.toList());

        // OS 메트릭 (1분 집계에서 5분마다 샘플링)
        List<OsMetricAgg> osMetrics;
        try {
            osMetrics = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", start, now);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("os_metric_agg 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                osMetrics = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (osMetrics.isEmpty()) {
            log.warn("차트3: os_metric_agg 데이터가 없습니다. instanceId={}", instanceId);
            // OS CPU 데이터가 없으면 0으로 채움
            List<Double> zeroCpu = new ArrayList<>(Collections.nCopies(cpuList.size(), 0.0));
            List<Integer> activeConnections = cpuList.stream()
                    .map(m -> safe(m.getAvgActiveConnections()).intValue())
                    .collect(Collectors.toList());

            return CpuDto.PostgresqlActiveConnections10m.builder()
                    .categories(categories)
                    .osCpuUsage(zeroCpu)
                    .activeConnections(activeConnections)
                    .build();
        }

        // 5분 간격으로 필터링
        List<OsMetricAgg> osFiltered = osMetrics.stream()
                .filter(m -> m.getCollectedAt().getMinute() % 5 == 0)
                .collect(Collectors.toList());

        // 시간별 OS CPU 맵 생성
        Map<String, Double> osCpuByTime = osFiltered.stream()
                .collect(Collectors.toMap(
                        m -> m.getCollectedAt().format(DATETIME_FORMATTER),
                        m -> safe(m.getAvgValue()),
                        (a, b) -> a // 중복 시 첫 번째 값 사용
                ));

        // CPU 데이터와 OS CPU 데이터를 시간 기준으로 매칭
        List<Double> osCpuUsage = cpuList.stream()
                .map(cpu -> {
                    String timeKey = cpu.getTimeBucket().format(DATETIME_FORMATTER);
                    return osCpuByTime.getOrDefault(timeKey, 0.0);
                })
                .collect(Collectors.toList());

        List<Integer> activeConnections = cpuList.stream()
                .map(m -> safe(m.getAvgActiveConnections()).intValue())
                .collect(Collectors.toList());

        return CpuDto.PostgresqlActiveConnections10m.builder()
                .categories(categories)
                .osCpuUsage(osCpuUsage)
                .activeConnections(activeConnections)
                .build();
    }

    /**
     * 차트4: Load Average 추이 (24시간)
     * Load Average 데이터 대신 Total Backend 프로세스 추이 사용
     * - load1m: Total Backend 프로세스 수 (Client + Autovacuum + Parallel + Background)
     * - load5m: Active Connections (활성 연결 수)
     * - load15m: Waiting Sessions (대기 중인 세션 수)
     * 최근 200개 데이터 포인트로 제한
     */
    private CpuDto.LoadAverageTrend15m buildLoadAverageTrend24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(24);

        List<CpuAgg5m> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAgg5mByTimeRangeWithLimit(instanceId, start, now, 200);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_5m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                cpuList = new ArrayList<>();
            } else {
                throw e;
            }
        }

        if (cpuList.isEmpty()) {
            return CpuDto.LoadAverageTrend15m.builder()
                    .categories(new ArrayList<>())
                    .load1m(new ArrayList<>())
                    .load5m(new ArrayList<>())
                    .load15m(new ArrayList<>())
                    .cpuCoreCount(getCpuCoreCount(instanceId))
                    .build();
        }

        // DESC로 조회했으므로 역순으로 정렬
        Collections.reverse(cpuList);

        List<String> categories = cpuList.stream()
                .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                .collect(Collectors.toList());

        // Total Backend 프로세스 수 (Client + Autovacuum + Parallel + Background)
        List<Double> totalBackends = cpuList.stream()
                .map(m -> safe(m.getAvgClientBackend()) +
                        safe(m.getAvgAutovacuumWorker()) +
                        safe(m.getAvgParallelWorker()) +
                        safe(m.getAvgBackgroundWorker()))
                .collect(Collectors.toList());

        // Active Connections
        List<Double> activeConnections = cpuList.stream()
                .map(m -> safe(m.getAvgActiveConnections()))
                .collect(Collectors.toList());

        // Waiting Sessions
        List<Double> waitingSessions = cpuList.stream()
                .map(m -> safe(m.getAvgWaitingSessions()))
                .collect(Collectors.toList());

        return CpuDto.LoadAverageTrend15m.builder()
                .categories(categories)
                .load1m(totalBackends)
                .load5m(activeConnections)
                .load15m(waitingSessions)
                .cpuCoreCount(getCpuCoreCount(instanceId))
                .build();
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
     * 차트5: 연결 상태 분포 (24시간)
     * 최근 200개 데이터 포인트로 제한
     */
    private CpuDto.ConnectionStatus1h buildConnectionStatus24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(24);

        List<CpuAgg5m> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAgg5mByTimeRangeWithLimit(instanceId, start, now, 200);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_5m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
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

        List<String> categories = cpuList.stream()
                .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                .collect(Collectors.toList());

        List<Integer> active = cpuList.stream()
                .map(m -> safe(m.getAvgActiveConnections().doubleValue()).intValue())
                .collect(Collectors.toList());

        List<Integer> idle = cpuList.stream()
                .map(m -> safe(m.getAvgIdleConnections().doubleValue()).intValue())
                .collect(Collectors.toList());

        List<Integer> idleInTx = cpuList.stream()
                .map(m -> safe(m.getAvgIdleInTransaction()).intValue())
                .collect(Collectors.toList());

        return CpuDto.ConnectionStatus1h.builder()
                .categories(categories)
                .active(active)
                .idle(idle)
                .idleInTx(idleInTx)
                .build();
    }

    /**
     * 차트6: TPS 일일 추이 (24시간)
     * 최근 200개 데이터 포인트로 제한
     */
    private CpuDto.TpsDailyTrend24h buildTpsDailyTrend24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(24);

        List<CpuAgg5m> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAgg5mByTimeRangeWithLimit(instanceId, start, now, 200);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_5m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
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

        List<String> categories = cpuList.stream()
                .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                .collect(Collectors.toList());

        List<Integer> commitTps = cpuList.stream()
                .map(m -> {
                    // avgXactCommitRate가 DB에 없으므로 totalXactCommit을 recordCount로 나눠서 계산
                    Long total = m.getTotalXactCommit() != null ? m.getTotalXactCommit() : 0L;
                    Long count = m.getRecordCount() != null ? m.getRecordCount() : 1L;
                    return (int) (total / Math.max(count, 1));
                })
                .collect(Collectors.toList());

        List<Integer> rollbackTps = cpuList.stream()
                .map(m -> {
                    Long total = m.getTotalXactRollback() != null ? m.getTotalXactRollback() : 0L;
                    Long count = m.getRecordCount() != null ? m.getRecordCount() : 1L;
                    return (int) (total / Math.max(count, 1));
                })
                .collect(Collectors.toList());

        return CpuDto.TpsDailyTrend24h.builder()
                .categories(categories)
                .commitTps(commitTps)
                .rollbackTps(rollbackTps)
                .build();
    }

    /**
     * 차트7: Wait Event 유형별 분포 (24시간)
     * 최근 200개 데이터 포인트로 제한
     */
    private CpuDto.WaitEventDistribution15m buildWaitEventDistribution24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(24);

        List<CpuAgg5m> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAgg5mByTimeRangeWithLimit(instanceId, start, now, 200);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_5m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
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

        List<String> categories = cpuList.stream()
                .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                .collect(Collectors.toList());

        // CpuAgg5m에 있는 실제 Wait Event 데이터 사용
        List<Integer> lock = cpuList.stream()
                .map(m -> safe(m.getAvgWaitingForLock()).intValue())
                .collect(Collectors.toList());

        List<Integer> io = cpuList.stream()
                .map(m -> safe(m.getAvgWaitingForIo()).intValue())
                .collect(Collectors.toList());

        List<Integer> client = cpuList.stream()
                .map(m -> safe(m.getAvgWaitEventClient()).intValue())
                .collect(Collectors.toList());

        List<Integer> activity = cpuList.stream()
                .map(m -> safe(m.getAvgWaitEventActivity()).intValue())
                .collect(Collectors.toList());

        List<Integer> lwlock = cpuList.stream()
                .map(m -> safe(m.getAvgWaitEventLwlock()).intValue())
                .collect(Collectors.toList());

        // 기타 = Bufferpin + Timeout + IPC
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
     * 차트8: Backend 유형별 추이 (24시간)
     * 최근 200개 데이터 포인트로 제한
     */
    private CpuDto.BackendTypeTrend24h buildBackendTypeTrend24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(24);

        List<CpuAgg5m> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAgg5mByTimeRangeWithLimit(instanceId, start, now, 200);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_5m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
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

        List<String> categories = cpuList.stream()
                .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                .collect(Collectors.toList());

        List<Integer> client = cpuList.stream()
                .map(m -> safe(m.getAvgClientBackend().doubleValue()).intValue())
                .collect(Collectors.toList());

        List<Integer> autovacuum = cpuList.stream()
                .map(m -> safe(m.getAvgAutovacuumWorker().doubleValue()).intValue())
                .collect(Collectors.toList());

        List<Integer> parallel = cpuList.stream()
                .map(m -> safe(m.getAvgParallelWorker().doubleValue()).intValue())
                .collect(Collectors.toList());

        List<Integer> background = cpuList.stream()
                .map(m -> safe(m.getAvgBackgroundWorker().doubleValue()).intValue())
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
     * 차트9: 에러율 추이 (24시간)
     * 최근 200개 데이터 포인트로 제한
     */
    private CpuDto.ErrorRateTrend15m buildErrorRateTrend24h(Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(24);

        List<CpuAgg5m> cpuList;
        try {
            cpuList = cpuMapper.selectCpuAgg5mByTimeRangeWithLimit(instanceId, start, now, 200);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("cpu_agg_5m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
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

        List<String> categories = cpuList.stream()
                .map(m -> m.getTimeBucket().format(DATETIME_FORMATTER))
                .collect(Collectors.toList());

        List<Double> data = cpuList.stream()
                .map(m -> {
                    // avgXactCommitRate가 DB에 없으므로 total 값으로 계산
                    Long totalCommit = m.getTotalXactCommit() != null ? m.getTotalXactCommit() : 0L;
                    Long totalRollback = m.getTotalXactRollback() != null ? m.getTotalXactRollback() : 0L;
                    Long count = m.getRecordCount() != null ? m.getRecordCount() : 1L;

                    double commit = totalCommit.doubleValue() / Math.max(count, 1);
                    double rollback = totalRollback.doubleValue() / Math.max(count, 1);
                    double total = commit + rollback;

                    return total > 0 ? (rollback / total) * 100 : 0.0;
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
     * CPU 리스트 조회
     */
    public CpuDto.ListResponse getCpuList(Long instanceId, CpuDto.ListRequest request) {
        log.debug("CPU 리스트 조회 - instanceId: {}, request: {}", instanceId, request);

        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수입니다");
        }

        try {
            OffsetDateTime endTime = OffsetDateTime.now();
            OffsetDateTime startTime = calculateStartTime(endTime, request.getTimeRange());

            // PostgreSQL 메트릭
            List<CpuAgg> cpuList;
            try {
                cpuList = cpuMapper.selectCpuAggByTimeRange(instanceId, startTime, endTime);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                    log.warn("cpu_agg_1m 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                    cpuList = new ArrayList<>();
                } else {
                    throw e;
                }
            }

            // OS 메트릭
            List<OsMetricAgg> osMetrics;
            try {
                osMetrics = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", startTime, endTime);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                    log.warn("os_metric_agg 테이블이 존재하지 않습니다. 빈 데이터를 반환합니다. instanceId={}", instanceId);
                    osMetrics = new ArrayList<>();
                } else {
                    throw e;
                }
            }

            List<CpuDto.CpuListItem> items = buildCpuListItems(cpuList, osMetrics, request.getStatus());

            return CpuDto.ListResponse.builder()
                    .data(items)
                    .total(items.size())
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
            OffsetDateTime now = OffsetDateTime.now();
            List<CpuAgg> recent = cpuMapper.selectCpuAggByTimeRange(
                    instanceId, now.minusMinutes(1), now);

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
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime start = now.minusMinutes(60);

            List<OsMetricAgg> osMetrics;
            try {
                osMetrics = osMetricMapper.findAggByInstanceAndPeriod(instanceId, "CPU", start, now);
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
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime start = now.minusHours(24);

            List<CpuAgg5m> cpuAgg5mList = cpuMapper.selectCpuAgg5mByTimeRange(instanceId, start, now);

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
            OffsetDateTime now = OffsetDateTime.now();
            List<CpuAgg> recent = cpuMapper.selectCpuAggByTimeRange(
                    instanceId, now.minusMinutes(1), now);

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
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime start = now.minusHours(24);

            List<CpuAgg5m> cpuAgg5mList = cpuMapper.selectCpuAgg5mByTimeRange(instanceId, start, now);

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
            OffsetDateTime now = OffsetDateTime.now();

            // PostgreSQL 메트릭
            List<CpuAgg> cpuRecent = cpuMapper.selectCpuAggByTimeRange(
                    instanceId, now.minusMinutes(1), now);

            // OS 메트릭
            List<OsMetricAgg> osRecent = osMetricMapper.findAggByInstanceAndPeriod(
                    instanceId, "CPU", now.minusMinutes(1), now);

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
