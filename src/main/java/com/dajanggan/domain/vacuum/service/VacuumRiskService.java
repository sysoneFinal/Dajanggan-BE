package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import com.dajanggan.domain.vacuum.repository.VacuumRiskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * VacuumRisk 서비스
 *
 * 주요 책임:
 * - Vacuum 위험도 데이터 조회
 * - Blocker 분석
 * - Wraparound 위험도 조회
 * - Transaction 산포도 생성
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-10  김민서    1. 최초작성
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumRiskService {

    private final VacuumRiskMapper vacuumRiskMapper;

    /**
     * Vacuum 위험도 대시보드 데이터 조회
     *
     * @param databaseId 데이터베이스 ID
     * @param hours 조회 시간 (시간 단위)
     * @return 위험도 대시보드 응답
     */
    public VacuumRiskDto.Response getRiskData(Long databaseId, int hours) {
        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusHours(Math.max(hours, 1));

        log.info("Risk Dashboard 조회: databaseId={}, hours={}", databaseId, hours);

        // 데이터 조회
        var blockers = getBlockersPerHour(databaseId, start, end);
        var bloatTop = getTopBloatTables(databaseId, 3, start, end);
        var blockersDtl = getVacuumBlockers(databaseId, start, end);
        var wrap = getWraparoundProgress(databaseId, start, end);

        // Blockers 차트
        var blockersChart = new VacuumRiskDto.ChartDto();
        blockersChart.setLabels(blockers.stream()
                .map(VacuumRiskDto.BlockersPerHourRaw::getHourLabel).toList());
        blockersChart.setData(List.of(blockers.stream()
                .map(VacuumRiskDto.BlockersPerHourRaw::getBlockersCount)
                .map(n -> (Number) n).toList()));

        // Wraparound 차트
        var wrapChart = new VacuumRiskDto.ChartDto();
        wrapChart.setLabels(wrap.stream()
                .map(r -> "DB " + r.getDatabaseId()).toList());
        wrapChart.setData(List.of(wrap.stream()
                .map(VacuumRiskDto.WraparoundProgressRaw::getWraparoundProgressPct)
                .map(n -> (Number) n).toList()));

        // Bloat 테이블
        var bloatRows = bloatTop.stream().map(r -> {
            var row = new VacuumRiskDto.TopBloatTableDto();
            row.setTable(r.getTableName());
            row.setBloat(formatPercent(r.getBloatRatio()));
            row.setDeadTuple(formatNumber(r.getDeadTuples()));
            return row;
        }).toList();

        // Blocker 상세
        var vbRows = blockersDtl.stream().map(v -> {
            var row = new VacuumRiskDto.VacuumBlockerDto();
            row.setTable(v.getTableName());
            row.setPid(String.valueOf(v.getPid()));
            row.setLockType(v.getLockType());
            row.setTxAge(formatDuration(v.getTransactionAge()));
            row.setBlocked_seconds(formatDuration(v.getBlockDuration()));
            row.setStatus(v.getQueryState());
            return row;
        }).toList();

        // Transaction Scatter
        var scatter = getTransactionScatter(databaseId, start, end);

        // 응답 생성
        var resp = new VacuumRiskDto.Response();
        resp.setBlockers(blockersChart);
        resp.setWraparound(wrapChart);
        resp.setBloat(bloatRows);
        resp.setVacuumblockers(vbRows);
        resp.setTransactionScatter(scatter);
        return resp;
    }

    /**
     * 시간대별 Blocker 수 조회
     */
    public List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(
            Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var window = calculateWindow(start, end);
        return vacuumRiskMapper.getBlockersPerHour(dbId, window[0], window[1]);
    }

    /**
     * 상위 Bloat 테이블 조회
     */
    public List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(
            Long dbId, Integer limit, OffsetDateTime start, OffsetDateTime end) {
        var window = calculateWindow(start, end);
        int lim = (limit == null || limit <= 0) ? 10 : limit;
        return vacuumRiskMapper.getTopBloatTables(dbId, lim, window[0], window[1]);
    }

    /**
     * Vacuum Blocker 상세 조회
     */
    public List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers(
            Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var window = calculateWindow(start, end);
        return vacuumRiskMapper.getVacuumBlockers(dbId, window[0], window[1]);
    }

    /**
     * Wraparound 진행률 조회
     */
    public List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress(
            Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var window = calculateWindow(start, end);
        return vacuumRiskMapper.getWraparoundProgress(dbId, window[0], window[1]);
    }

    /**
     * Transaction Age 산포도 데이터 생성
     */
    public VacuumRiskDto.ScatterDto getTransactionScatter(
            Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var window = calculateWindow(start, end);
        var list = vacuumRiskMapper.getVacuumBlockers(dbId, window[0], window[1]);

        var points = new ArrayList<List<Long>>(list.size());
        for (var v : list) {
            if (v.getTransactionAge() == null) continue;
            points.add(List.of(
                    v.getTransactionAge(),
                    v.getBlockDuration() == null ? 0L : v.getBlockDuration()
            ));
        }

        var dto = new VacuumRiskDto.ScatterDto();
        dto.setLabels(List.of("txAgeSec", "blockedSec"));
        dto.setData(points);
        return dto;
    }

    // ========================================================================
    // Private Helper 메서드
    // ========================================================================

    /**
     * 시간 윈도우 계산
     */
    private OffsetDateTime[] calculateWindow(OffsetDateTime start, OffsetDateTime end) {
        OffsetDateTime e = (end != null) ? end : OffsetDateTime.now();
        OffsetDateTime s = (start != null) ? start : e.minusHours(24);
        return new OffsetDateTime[]{s, e};
    }

    /**
     * 초 단위를 읽기 쉬운 형식으로 변환
     */
    private String formatDuration(Long sec) {
        if (sec == null || sec <= 0) return "0s";

        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;

        if (h > 0) return String.format("%dh %dm", h, m);
        if (m > 0) return (s > 0) ? String.format("%dm %ds", m, s) : String.format("%dm", m);
        return String.format("%ds", s);
    }

    /**
     * 비율을 퍼센트로 변환
     */
    private String formatPercent(Double ratio) {
        if (ratio == null) return "0.0%";
        return String.format("%.1f%%", ratio * 100.0);
    }

    /**
     * 숫자를 K/M/B 단위로 변환
     */
    private String formatNumber(Long n) {
        if (n == null) return "0";

        double v = n;
        if (v >= 1_000_000_000) return String.format("%.1fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fK", v / 1_000);
        return String.valueOf(n);
    }
}
