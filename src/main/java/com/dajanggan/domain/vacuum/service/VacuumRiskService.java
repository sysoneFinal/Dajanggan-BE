package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import com.dajanggan.domain.vacuum.repository.VacuumRiskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumRiskService {

    private final VacuumRiskRepository repo;

    /* ------- window 기본값: 최근 24h ------- */
    private OffsetDateTime[] window(OffsetDateTime start, OffsetDateTime end) {
        var e = (end != null) ? end : OffsetDateTime.now();
        var s = (start != null) ? start : e.minusHours(24);
        return new OffsetDateTime[]{s, e};
    }

    /* ------- 집계(Dashboard) ------- */
    public VacuumRiskDto.Response getRiskData(Long databaseId, int hours) {
        var e = OffsetDateTime.now();
        var s = e.minusHours(Math.max(hours, 1));

        var blockers = getBlockersPerHour(databaseId, s, e);
        var bloatTop = getTopBloatTables(databaseId, 3, s, e);
        var blockersDtl = getVacuumBlockers(databaseId, s, e);
        var wrap = getWraparoundProgress(databaseId, s, e);

        // Blockers per hour → ChartDto
        var blockersChart = new VacuumRiskDto.ChartDto();
        blockersChart.setLabels(blockers.stream().map(VacuumRiskDto.BlockersPerHourRaw::getHourLabel).toList());
        blockersChart.setData(List.of(blockers.stream().map(VacuumRiskDto.BlockersPerHourRaw::getBlockersCount).map(n -> (Number) n).toList()));

        // Wraparound → ChartDto
        var wrapChart = new VacuumRiskDto.ChartDto();
        wrapChart.setLabels(wrap.stream().map(r -> "DB " + r.getDatabaseId()).toList());
        wrapChart.setData(List.of(wrap.stream().map(VacuumRiskDto.WraparoundProgressRaw::getWraparoundProgressPct).map(n -> (Number) n).toList()));

        // Top bloat rows (포맷)
        var bloatRows = bloatTop.stream().map(r -> {
            var row = new VacuumRiskDto.TopBloatTableDto();
            row.setTable(r.getTableName());
            row.setBloat(percent(r.getBloatRatio()));
            row.setDeadTuple(formatK(r.getDeadTuples()));
            return row;
        }).toList();

        // Vacuum blockers rows (포맷)
        var vbRows = blockersDtl.stream().map(v -> {
            var row = new VacuumRiskDto.VacuumBlockerDto();
            row.setTable(v.getTableName());
            row.setPid(String.valueOf(v.getPid()));
            row.setLockType(v.getLockType());
            row.setTxAge(humanSec(v.getTransactionAge()));
            row.setBlocked_seconds(humanSec(v.getBlockDuration()));
            row.setStatus(v.getQueryState());
            return row;
        }).toList();

        // (선택) 산포도
        var scatter = getTransactionScatter(databaseId, s, e);

        var resp = new VacuumRiskDto.Response();
        resp.setBlockers(blockersChart);
        resp.setWraparound(wrapChart);
        resp.setBloat(bloatRows);
        resp.setVacuumblockers(vbRows);
        resp.setTransactionScatter(scatter);
        return resp;
    }

    /* ------- 개별 API ------- */

    public List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var w = window(start, end);
        return repo.getBlockersPerHour(dbId, w[0], w[1]);
    }

    public List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(Long dbId, Integer limit, OffsetDateTime start, OffsetDateTime end) {
        var w = window(start, end);
        int lim = (limit == null || limit <= 0) ? 10 : limit;
        return repo.getTopBloatTables(dbId, lim, w[0], w[1]);
    }

    public List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers(Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var w = window(start, end);
        return repo.getVacuumBlockers(dbId, w[0], w[1]);
    }

    public List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress(Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var w = window(start, end);
        return repo.getWraparoundProgress(dbId, w[0], w[1]);
    }

    /** 산포도: x=txAgeSec, y=blockedSec */
    public VacuumRiskDto.ScatterDto getTransactionScatter(Long dbId, OffsetDateTime start, OffsetDateTime end) {
        var w = window(start, end);
        var list = repo.getVacuumBlockers(dbId, w[0], w[1]);

        var points = new ArrayList<List<Long>>(list.size());
        for (var v : list) {
            if (v.getTransactionAge() == null || v.getBlockDuration() == null) continue;
            points.add(List.of(v.getTransactionAge(), v.getBlockDuration()));
        }

        var dto = new VacuumRiskDto.ScatterDto();
        dto.setLabels(List.of("txAgeSec", "blockedSec"));
        dto.setData(points);
        return dto;
    }

    /* ------- helpers ------- */
    private static String humanSec(Long sec) {
        if (sec == null || sec <= 0) return "0s";
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return String.format("%dh %dm", h, m);
        if (m > 0) return (s > 0) ? String.format("%dm %ds", m, s) : String.format("%dm", m);
        return String.format("%ds", s);
    }

    private static String percent(Double ratio) {
        if (ratio == null) return "0.0%";
        return String.format("%.1f%%", ratio * 100.0);
    }

    private static String formatK(Long n) {
        if (n == null) return "0";
        double v = n;
        if (v >= 1_000_000_000) return String.format("%.1fB", v / 1_000_000_000);
        if (v >= 1_000_000)     return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000)         return String.format("%.1fK", v / 1_000);
        return String.valueOf(n);
    }
}
