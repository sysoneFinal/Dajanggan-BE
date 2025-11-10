package com.dajanggan.domain.engine.hottable.service;

import com.dajanggan.domain.engine.hottable.dto.HotTableDto;
import com.dajanggan.domain.engine.hottable.repository.HotTableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotTableService {

    private final HotTableMapper hotTableMapper;

    /**
     * instanceId -> databaseId 매핑
     */
    public Long resolveDatabaseId(Long instanceId) {
        return hotTableMapper.selectDatabaseIdByInstanceId(instanceId);
    }

    public HotTableDto.DashboardResponse getDashboard(Long databaseId,
                                                      LocalDateTime startTime,
                                                      LocalDateTime endTime) {

        // 1) 캐시 히트율
        Map<String, Object> cacheHitRow = hotTableMapper.selectTopCacheHitTable(databaseId);
        HotTableDto.CacheHitRatio cacheHitRatio = new HotTableDto.CacheHitRatio();
        if (cacheHitRow != null) {
            cacheHitRatio.setTableName((String) cacheHitRow.getOrDefault("table_name", ""));
            cacheHitRatio.setValue(toDouble(cacheHitRow.get("avg_cache_hit_ratio")));
            cacheHitRatio.setBufferHits(toLong(cacheHitRow.get("blks_hit")));
            cacheHitRatio.setDiskReads(toLong(cacheHitRow.get("blks_read")));
        }

        // 2) vacuum delay trend
        var vacuumRows = hotTableMapper.selectVacuumDelayTimeSeries(databaseId, startTime, endTime);
        HotTableDto.VacuumDelayTrend vacuumDelayTrend = buildVacuumTrend(vacuumRows);

        // 3) dead tuple trend
        var deadRows = hotTableMapper.selectDeadTupleTimeSeries(databaseId, startTime, endTime);
        HotTableDto.DeadTupleTrend deadTupleTrend = buildDeadTrend(deadRows);

        // 4) total dead tuple
        var totalDeadRows = hotTableMapper.selectTotalDeadTupleSeries(databaseId, startTime, endTime);
        HotTableDto.TotalDeadTuple totalDeadTuple = buildTotalDeadTuple(totalDeadRows);

        // 5) top query tables
        var topQueryRows = hotTableMapper.selectTopQueryTables(databaseId, startTime, endTime);
        HotTableDto.TopQueryTables topQueryTables = buildTopQueryTables(topQueryRows);

        // 6) top dml tables
        var topDmlRows = hotTableMapper.selectTopDmlTables(databaseId, startTime, endTime);
        HotTableDto.TopDmlTables topDmlTables = buildTopDmlTables(topDmlRows);

        // 7) recent stats
        Map<String, Object> recentStatsRow = hotTableMapper.selectRecentStats(databaseId);
        HotTableDto.RecentStats recentStats = new HotTableDto.RecentStats();
        if (recentStatsRow != null) {
            recentStats.setHotUpdateRatio(toDouble(recentStatsRow.get("hot_update_ratio")));
            recentStats.setLiveDeadTupleRatio((String) recentStatsRow.getOrDefault("live_dead_tuple_ratio", "0:0"));
            recentStats.setDeadTupleCount(toLong(recentStatsRow.get("dead_tuple_count")));
            recentStats.setSeqScanRatio(toDouble(recentStatsRow.get("seq_scan_ratio")));
            recentStats.setUpdateDeleteRatio(toDouble(recentStatsRow.get("update_delete_ratio")));
            recentStats.setAvgVacuumDelay(toDouble(recentStatsRow.get("avg_vacuum_delay")));
            recentStats.setTotalBloat(toDouble(recentStatsRow.get("total_bloat_gb")));
        }

        HotTableDto.DashboardResponse resp = new HotTableDto.DashboardResponse();
        resp.setCacheHitRatio(cacheHitRatio);
        resp.setVacuumDelayTrend(vacuumDelayTrend);
        resp.setDeadTupleTrend(deadTupleTrend);
        resp.setTotalDeadTuple(totalDeadTuple);
        resp.setTopQueryTables(topQueryTables);
        resp.setTopDmlTables(topDmlTables);
        resp.setRecentStats(recentStats);

        return resp;
    }

    // ===== 내부 빌더 메서드 =====

    private HotTableDto.VacuumDelayTrend buildVacuumTrend(List<Map<String, Object>> rows) {
        HotTableDto.VacuumDelayTrend trend = new HotTableDto.VacuumDelayTrend();

        List<String> categories = rows.stream()
                .map(r -> (String) r.get("time_label"))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        trend.setCategories(categories);

        Map<String, List<Map<String, Object>>> byTable =
                rows.stream().collect(Collectors.groupingBy(r -> (String) r.get("table_name")));

        List<HotTableDto.NamedSeries> seriesList = new ArrayList<>();
        for (var entry : byTable.entrySet()) {
            String tableName = entry.getKey();
            Map<String, Number> timeMap = entry.getValue().stream()
                    .collect(Collectors.toMap(
                            r -> (String) r.get("time_label"),
                            r -> (Number) r.getOrDefault("vacuum_delay_seconds", 0),
                            (a, b) -> a
                    ));

            List<Double> data = categories.stream()
                    .map(c -> timeMap.getOrDefault(c, 0).doubleValue())
                    .collect(Collectors.toList());

            HotTableDto.NamedSeries ns = new HotTableDto.NamedSeries();
            ns.setName(tableName);
            ns.setData(data);
            seriesList.add(ns);
        }

        trend.setTables(seriesList);
        return trend;
    }

    private HotTableDto.DeadTupleTrend buildDeadTrend(List<Map<String, Object>> rows) {
        HotTableDto.DeadTupleTrend trend = new HotTableDto.DeadTupleTrend();

        List<String> categories = rows.stream()
                .map(r -> (String) r.get("time_label"))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        trend.setCategories(categories);

        Map<String, List<Map<String, Object>>> byTable =
                rows.stream().collect(Collectors.groupingBy(r -> (String) r.get("table_name")));

        List<HotTableDto.NamedSeries> seriesList = new ArrayList<>();
        for (var entry : byTable.entrySet()) {
            String tableName = entry.getKey();
            Map<String, Number> timeMap = entry.getValue().stream()
                    .collect(Collectors.toMap(
                            r -> (String) r.get("time_label"),
                            r -> (Number) r.getOrDefault("dead_tuple_count", 0),
                            (a, b) -> a
                    ));

            List<Double> data = categories.stream()
                    .map(c -> timeMap.getOrDefault(c, 0).doubleValue())
                    .collect(Collectors.toList());

            HotTableDto.NamedSeries ns = new HotTableDto.NamedSeries();
            ns.setName(tableName);
            ns.setData(data);
            seriesList.add(ns);
        }

        trend.setTables(seriesList);
        return trend;
    }

    private HotTableDto.TotalDeadTuple buildTotalDeadTuple(List<Map<String, Object>> rows) {
        HotTableDto.TotalDeadTuple total = new HotTableDto.TotalDeadTuple();
        List<String> categories = new ArrayList<>();
        List<Double> data = new ArrayList<>();
        double sum = 0;
        double max = 0;

        for (var row : rows) {
            String label = (String) row.get("time_label");
            double val = toDouble(row.get("total_dead_tuple"));
            categories.add(label);
            data.add(val);
            sum += val;
            max = Math.max(max, val);
        }

        total.setCategories(categories);
        total.setData(data);
        total.setTotal(sum);
        total.setAverage(data.isEmpty() ? 0 : sum / data.size());
        total.setMax(max);

        return total;
    }

    private HotTableDto.TopQueryTables buildTopQueryTables(List<Map<String, Object>> rows) {
        HotTableDto.TopQueryTables top = new HotTableDto.TopQueryTables();
        List<String> names = new ArrayList<>();
        List<Double> seq = new ArrayList<>();
        List<Double> idx = new ArrayList<>();

        for (var r : rows) {
            names.add((String) r.get("table_name"));
            seq.add(toDouble(r.get("total_seq_scan")));
            idx.add(toDouble(r.get("total_idx_scan")));
        }
        top.setTableNames(names);
        top.setSeqScanCounts(seq);
        top.setIndexScanCounts(idx);
        return top;
    }

    private HotTableDto.TopDmlTables buildTopDmlTables(List<Map<String, Object>> rows) {
        HotTableDto.TopDmlTables top = new HotTableDto.TopDmlTables();
        List<String> names = new ArrayList<>();
        List<Double> ins = new ArrayList<>();
        List<Double> upd = new ArrayList<>();
        List<Double> del = new ArrayList<>();

        for (var r : rows) {
            names.add((String) r.get("table_name"));
            ins.add(toDouble(r.get("total_tup_ins")));
            upd.add(toDouble(r.get("total_tup_upd")));
            del.add(toDouble(r.get("total_tup_del")));
        }
        top.setTableNames(names);
        top.setInsertCounts(ins);
        top.setUpdateCounts(upd);
        top.setDeleteCounts(del);
        return top;
    }

    private double toDouble(Object o) {
        if (o == null) return 0;
        return ((Number) o).doubleValue();
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        return ((Number) o).longValue();
    }
}
