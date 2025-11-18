package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg1mDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumBloatRepository {

    private final VacuumAgg1mMapper agg1mMapper;
    private final VacuumAgg5mMapper agg5mMapper;
    private final VacuumBloatRawMapper rawMapper;

    // ========== KPI (집계 테이블) ==========

    public VacuumAgg5mDto getKpiSummary(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return agg5mMapper.getBloatKpiSummary(databaseId, instanceId, start, end);
    }

    // ========== 차트 데이터 (집계 테이블) ==========

    public List<VacuumAgg5mDto> getBloatTrend(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return agg5mMapper.findByTimeRange(databaseId, instanceId, start, end);
    }

    // ========== Xmin Horizon (Raw 데이터) ==========

    public List<VacuumRawMetricDto> getXminHorizonData(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return rawMapper.findXminHorizonData(databaseId, instanceId, start, end);
    }

    // ========== 테이블 목록 (Raw 데이터) ==========

    public List<String> getTableList(Long databaseId, Long instanceId) {
        return rawMapper.findTableList(databaseId, instanceId);
    }

    // ========== 테이블별 상세 (Raw 데이터) ==========

    public VacuumRawMetricDto getLatestTableMetrics(
            Long databaseId, Long instanceId, String tableName) {
        return rawMapper.findLatestByTable(databaseId, instanceId, tableName);
    }

    public List<VacuumRawMetricDto> getTableBloatTrend(
            Long databaseId, Long instanceId, String tableName,
            OffsetDateTime start, OffsetDateTime end) {
        return rawMapper.findTableBloatTrend(databaseId, instanceId, tableName, start, end);
    }
}