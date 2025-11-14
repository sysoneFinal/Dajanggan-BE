package com.dajanggan.domain.system.cpu.service;

import com.dajanggan.domain.system.cpu.domain.CpuAgg;
import com.dajanggan.domain.system.cpu.domain.CpuRaw;
import com.dajanggan.domain.system.cpu.dto.CpuDto;
import com.dajanggan.domain.system.cpu.repository.CpuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CpuService {

    private final CpuMapper cpuMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * CPU 대시보드 데이터 조회
     */
    public CpuDto.DashboardResponse getCpuDashboard(Long instanceId) {
        log.debug("CPU 대시보드 데이터 조회 시작 - instanceId: {}", instanceId);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minusHours(24);

        log.info("데이터 조회 범위: {} ~ {}", startTime, endTime);

        try {
            List<CpuAgg> cpuAggList = cpuMapper.selectCpuAggByTimeRange(instanceId, startTime, endTime);

            if (cpuAggList == null || cpuAggList.isEmpty()) {
                log.warn("CPU 데이터 없음 - instanceId: {}", instanceId);
                return createEmptyDashboard();
            }

            // 프론트엔드 스펙에 맞게 데이터 구성
            CpuDto.CpuUsage cpuUsage = buildCpuUsage(cpuAggList);
            CpuDto.CpuUsageTrend cpuUsageTrend = buildCpuUsageTrend(cpuAggList);
            CpuDto.CpuLoadTypes cpuLoadTypes = buildCpuLoadTypes(cpuAggList);
            CpuDto.IoWaitVsLatency ioWaitVsLatency = buildIoWaitVsLatency(cpuAggList);
            CpuDto.BackendProcessStats backendProcessStats = buildBackendProcessStats(cpuAggList);
            CpuDto.WaitEventDistribution waitEventDistribution = buildWaitEventDistribution(cpuAggList);
            CpuDto.RecentStats recentStats = buildRecentStats(cpuAggList);

            return CpuDto.DashboardResponse.builder()
                    .cpuUsage(cpuUsage)
                    .cpuUsageTrend(cpuUsageTrend)
                    .cpuLoadTypes(cpuLoadTypes)
                    .ioWaitVsLatency(ioWaitVsLatency)
                    .backendProcessStats(backendProcessStats)
                    .waitEventDistribution(waitEventDistribution)
                    .recentStats(recentStats)
                    .build();

        } catch (Exception e) {
            log.error("CPU 대시보드 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("CPU 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * CPU 사용률 (게이지용)
     */
    private CpuDto.CpuUsage buildCpuUsage(List<CpuAgg> cpuAggList) {
        if (cpuAggList.isEmpty()) {
            return CpuDto.CpuUsage.builder()
                    .value(0.0)
                    .description("데이터 없음")
                    .runningQueries(0L)
                    .waitingQueries(0L)
                    .idleConnections(0L)
                    .build();
        }

        // 최근 데이터 사용
        CpuAgg latest = cpuAggList.get(cpuAggList.size() - 1);
        double cpuValue = latest.getAvgTotalCpu() != null ? latest.getAvgTotalCpu() : 0.0;

        String description;
        if (cpuValue < 50) {
            description = "정상";
        } else if (cpuValue < 80) {
            description = "주의";
        } else {
            description = "위험";
        }

        return CpuDto.CpuUsage.builder()
                .value(cpuValue)
                .description(description)
                .runningQueries(latest.getAvgActiveConnections() != null ? latest.getAvgActiveConnections() : 0L)
                .waitingQueries(latest.getAvgWaitingSessions() != null ? latest.getAvgWaitingSessions().longValue() : 0L)
                .idleConnections(0L) // TODO: 실제 idle 연결 수 계산
                .build();
    }

    /**
     * CPU 사용률 트렌드 (간단한 버전)
     */
    private CpuDto.CpuUsageTrend buildCpuUsageTrend(List<CpuAgg> cpuAggList) {
        List<String> categories = cpuAggList.stream()
                .map(agg -> agg.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        List<Double> data = cpuAggList.stream()
                .map(agg -> agg.getAvgTotalCpu() != null ? agg.getAvgTotalCpu() : 0.0)
                .collect(Collectors.toList());

        return CpuDto.CpuUsageTrend.builder()
                .categories(categories)
                .data(data)
                .build();
    }

    /**
     * CPU 부하 유형별 분석
     */
    private CpuDto.CpuLoadTypes buildCpuLoadTypes(List<CpuAgg> cpuAggList) {
        List<String> categories = cpuAggList.stream()
                .map(agg -> agg.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // backendType별로 분류
        // TODO: 실제 데이터베이스에서 백엔드 타입별 CPU 데이터를 가져와야 함
        // 현재는 더미 데이터로 대체
        List<Double> postgresqlBackend = cpuAggList.stream()
                .map(agg -> agg.getAvgUserCpu() != null ? agg.getAvgUserCpu() * 0.6 : 0.0)
                .collect(Collectors.toList());

        List<Double> bgWriter = cpuAggList.stream()
                .map(agg -> agg.getAvgSystemCpu() != null ? agg.getAvgSystemCpu() * 0.3 : 0.0)
                .collect(Collectors.toList());

        List<Double> autoVacuum = cpuAggList.stream()
                .map(agg -> agg.getAvgSystemCpu() != null ? agg.getAvgSystemCpu() * 0.2 : 0.0)
                .collect(Collectors.toList());

        List<Double> checkpoint = cpuAggList.stream()
                .map(agg -> agg.getAvgIoWait() != null ? agg.getAvgIoWait() * 0.1 : 0.0)
                .collect(Collectors.toList());

        return CpuDto.CpuLoadTypes.builder()
                .categories(categories)
                .postgresqlBackend(postgresqlBackend)
                .bgWriter(bgWriter)
                .autoVacuum(autoVacuum)
                .checkpoint(checkpoint)
                .build();
    }

    /**
     * I/O Wait vs 디스크 Latency 상관관계
     */
    private CpuDto.IoWaitVsLatency buildIoWaitVsLatency(List<CpuAgg> cpuAggList) {
        List<CpuDto.Point> normal = new ArrayList<>();
        List<CpuDto.Point> warning = new ArrayList<>();
        List<CpuDto.Point> danger = new ArrayList<>();

        Random random = new Random();

        for (CpuAgg agg : cpuAggList) {
            double ioWait = agg.getAvgIoWait() != null ? agg.getAvgIoWait() : 0.0;
            // 디스크 레이턴시는 임의로 생성 (실제로는 별도 테이블에서 가져와야 함)
            double diskLatency = 5 + random.nextDouble() * 20;

            CpuDto.Point point = CpuDto.Point.builder()
                    .x(ioWait)
                    .y(diskLatency)
                    .build();

            // 임계값에 따라 분류
            if (ioWait < 10 && diskLatency < 15) {
                normal.add(point);
            } else if (ioWait < 20 || diskLatency < 30) {
                warning.add(point);
            } else {
                danger.add(point);
            }
        }

        return CpuDto.IoWaitVsLatency.builder()
                .normal(normal)
                .warning(warning)
                .danger(danger)
                .build();
    }

    /**
     * Backend 프로세스 타입별 분포
     */
    private CpuDto.BackendProcessStats buildBackendProcessStats(List<CpuAgg> cpuAggList) {
        // PostgreSQL 백엔드 타입
        List<String> types = Arrays.asList(
                "client backend",
                "autovacuum worker",
                "background writer",
                "checkpointer",
                "walwriter"
        );

        // TODO: 실제 데이터는 pg_stat_activity에서 가져와야 함
        // 현재는 더미 데이터
        List<Long> activeCount = Arrays.asList(25L, 3L, 1L, 1L, 1L);
        List<Long> idleCount = Arrays.asList(15L, 2L, 0L, 0L, 0L);
        List<Long> totalCount = Arrays.asList(40L, 5L, 1L, 1L, 1L);

        List<String> colors = Arrays.asList(
                "#8E79FF",
                "#77B2FB",
                "#51DAA8",
                "#FEA29B",
                "#FFD66B"
        );

        return CpuDto.BackendProcessStats.builder()
                .types(types)
                .activeCount(activeCount)
                .idleCount(idleCount)
                .totalCount(totalCount)
                .colors(colors)
                .build();
    }

    /**
     * 대기 유형별 비중 변화
     */
    private CpuDto.WaitEventDistribution buildWaitEventDistribution(List<CpuAgg> cpuAggList) {
        List<String> categories = cpuAggList.stream()
                .map(agg -> agg.getCollectedAt().format(TIME_FORMATTER))
                .collect(Collectors.toList());

        // TODO: 실제 대기 이벤트 데이터는 pg_stat_activity의 wait_event_type에서 가져와야 함
        // 현재는 더미 데이터
        List<Double> cpu = new ArrayList<>();
        List<Double> client = new ArrayList<>();
        List<Double> io = new ArrayList<>();
        List<Double> lock = new ArrayList<>();
        List<Double> other = new ArrayList<>();

        Random random = new Random();
        for (int i = 0; i < cpuAggList.size(); i++) {
            double total = 100.0;
            double cpuPct = 40 + random.nextDouble() * 20;
            double clientPct = 20 + random.nextDouble() * 10;
            double ioPct = 15 + random.nextDouble() * 10;
            double lockPct = 5 + random.nextDouble() * 5;
            double otherPct = total - cpuPct - clientPct - ioPct - lockPct;

            cpu.add(cpuPct);
            client.add(clientPct);
            io.add(ioPct);
            lock.add(lockPct);
            other.add(Math.max(0, otherPct));
        }

        return CpuDto.WaitEventDistribution.builder()
                .categories(categories)
                .cpu(cpu)
                .client(client)
                .io(io)
                .lock(lock)
                .other(other)
                .build();
    }

    /**
     * 최근 통계 (프론트엔드 스펙에 맞게)
     */
    private CpuDto.RecentStats buildRecentStats(List<CpuAgg> cpuAggList) {
        if (cpuAggList.isEmpty()) {
            return createEmptyRecentStats();
        }

        CpuAgg latest = cpuAggList.get(cpuAggList.size() - 1);

        CpuDto.LoadAverageStats loadAverage = CpuDto.LoadAverageStats.builder()
                .one(latest.getAvgLoad1() != null ? latest.getAvgLoad1() : 0.0)
                .five(latest.getAvgLoad5() != null ? latest.getAvgLoad5() : 0.0)
                .fifteen(latest.getAvgLoad15() != null ? latest.getAvgLoad15() : 0.0)
                .build();

        // TODO: 실제 idle 연결 수 계산
        long activeConnections = latest.getAvgActiveConnections() != null ? latest.getAvgActiveConnections() : 0L;
        long totalConnections = activeConnections + 10; // 임시

        CpuDto.ConnectionStats connections = CpuDto.ConnectionStats.builder()
                .active(activeConnections)
                .idle(totalConnections - activeConnections)
                .total(totalConnections)
                .build();

        return CpuDto.RecentStats.builder()
                .loadAverage(loadAverage)
                .ioWait(latest.getAvgIoWait() != null ? latest.getAvgIoWait() : 0.0)
                .connections(connections)
                .idleCpu(latest.getAvgIdleCpu() != null ? latest.getAvgIdleCpu() : 0.0)
                .contextSwitches(latest.getTotalContextSwitches() != null ? latest.getTotalContextSwitches() : 0L)
                .postgresqlBackendCpu(latest.getAvgUserCpu() != null ? latest.getAvgUserCpu() : 0.0)
                .build();
    }

    /**
     * CPU 리스트 데이터 조회
     */
    public CpuDto.ListResponse getCpuList(Long instanceId, String timeRange, List<String> statusList) {
        log.debug("CPU 리스트 조회 시작 - instanceId: {}, timeRange: {}, statusList: {}",
                instanceId, timeRange, statusList);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            List<CpuAgg> cpuAggList = cpuMapper.selectCpuListWithFilter(
                    instanceId, startTime, endTime, statusList);

            if (cpuAggList == null || cpuAggList.isEmpty()) {
                log.warn("CPU 리스트 데이터 없음");
                return CpuDto.ListResponse.builder()
                        .data(new ArrayList<>())
                        .total(0L)
                        .build();
            }

            List<CpuDto.ListItem> listItems = cpuAggList.stream()
                    .map(this::convertToListItem)
                    .collect(Collectors.toList());

            return CpuDto.ListResponse.builder()
                    .data(listItems)
                    .total((long) listItems.size())
                    .build();

        } catch (Exception e) {
            log.error("CPU 리스트 조회 중 오류 발생", e);
            throw new RuntimeException("CPU 리스트 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * CpuAgg를 ListItem으로 변환
     */
    private CpuDto.ListItem convertToListItem(CpuAgg agg) {
        return CpuDto.ListItem.builder()
                .id(String.valueOf(agg.getCpuAggId()))
                .time(agg.getCollectedAt().format(TIME_FORMATTER))
                .totalCPU(agg.getAvgTotalCpu())
                .userCPU(agg.getAvgUserCpu())
                .systemCPU(agg.getAvgSystemCpu())
                .idleCPU(agg.getAvgIdleCpu())
                .ioWait(agg.getAvgIoWait())
                .stealCPU(agg.getAvgStealCpu())
                .loadAvg1(agg.getAvgLoad1())
                .loadAvg5(agg.getAvgLoad5())
                .loadAvg15(agg.getAvgLoad15())
                .activeSessions(agg.getAvgActiveConnections())
                .parallelWorkers(agg.getAvgParallelWorkers() != null ? agg.getAvgParallelWorkers().longValue() : 0L)
                .waitingSessions(agg.getAvgWaitingSessions() != null ? agg.getAvgWaitingSessions().longValue() : 0L)
                .workerTime(agg.getAvgWorkerTime())
                .contextSwitches(agg.getTotalContextSwitches())
                .status(agg.getStatus() != null ? agg.getStatus() : "정상")
                .build();
    }

    /**
     * 시간 범위 계산
     */
    private OffsetDateTime calculateStartTime(OffsetDateTime endTime, String timeRange) {
        return switch (timeRange) {
            case "1h" -> endTime.minusHours(1);
            case "6h" -> endTime.minusHours(6);
            case "24h" -> endTime.minusHours(24);
            case "7d" -> endTime.minusDays(7);
            default -> endTime.minusHours(1);
        };
    }

    /**
     * 빈 대시보드 생성
     */
    private CpuDto.DashboardResponse createEmptyDashboard() {
        return CpuDto.DashboardResponse.builder()
                .cpuUsage(CpuDto.CpuUsage.builder()
                        .value(0.0)
                        .description("데이터 없음")
                        .runningQueries(0L)
                        .waitingQueries(0L)
                        .idleConnections(0L)
                        .build())
                .cpuUsageTrend(CpuDto.CpuUsageTrend.builder()
                        .categories(new ArrayList<>())
                        .data(new ArrayList<>())
                        .build())
                .cpuLoadTypes(CpuDto.CpuLoadTypes.builder()
                        .categories(new ArrayList<>())
                        .autoVacuum(new ArrayList<>())
                        .bgWriter(new ArrayList<>())
                        .checkpoint(new ArrayList<>())
                        .postgresqlBackend(new ArrayList<>())
                        .build())
                .ioWaitVsLatency(CpuDto.IoWaitVsLatency.builder()
                        .normal(new ArrayList<>())
                        .warning(new ArrayList<>())
                        .danger(new ArrayList<>())
                        .build())
                .backendProcessStats(CpuDto.BackendProcessStats.builder()
                        .types(new ArrayList<>())
                        .activeCount(new ArrayList<>())
                        .idleCount(new ArrayList<>())
                        .totalCount(new ArrayList<>())
                        .colors(new ArrayList<>())
                        .build())
                .waitEventDistribution(CpuDto.WaitEventDistribution.builder()
                        .categories(new ArrayList<>())
                        .cpu(new ArrayList<>())
                        .client(new ArrayList<>())
                        .io(new ArrayList<>())
                        .lock(new ArrayList<>())
                        .other(new ArrayList<>())
                        .build())
                .recentStats(createEmptyRecentStats())
                .build();
    }

    private CpuDto.RecentStats createEmptyRecentStats() {
        return CpuDto.RecentStats.builder()
                .loadAverage(CpuDto.LoadAverageStats.builder()
                        .one(0.0)
                        .five(0.0)
                        .fifteen(0.0)
                        .build())
                .ioWait(0.0)
                .connections(CpuDto.ConnectionStats.builder()
                        .active(0L)
                        .idle(0L)
                        .total(0L)
                        .build())
                .idleCpu(0.0)
                .contextSwitches(0L)
                .postgresqlBackendCpu(0.0)
                .build();
    }
}