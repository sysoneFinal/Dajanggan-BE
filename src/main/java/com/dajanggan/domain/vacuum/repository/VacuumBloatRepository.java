// 작성자: 김민서
package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumBloatRepository {

    private final VacuumBloatMapper bloatMapper;

    // ========== KPI (집계 테이블) ==========

    public VacuumAgg5mDto getKpiSummary(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return bloatMapper.getBloatKpiSummary(databaseId, instanceId, start, end);
    }

    // ========== 차트 데이터 (집계 테이블) ==========

    public List<VacuumAgg5mDto> getBloatTrend(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return bloatMapper.getBloatTrend(databaseId, instanceId, start, end);
    }

    // ========== Xmin Horizon (Raw 데이터) ==========

    public List<VacuumRawMetricDto> getXminHorizonData(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return bloatMapper.findXminHorizonData(databaseId, instanceId, start, end);
    }

    // ========== 테이블 목록 (Raw 데이터) ==========

    public List<String> getTableList(Long databaseId, Long instanceId) {
        return bloatMapper.findTableList(databaseId, instanceId);
    }

    // ========== 테이블별 상세 (Raw 데이터) ==========

    public VacuumRawMetricDto getLatestTableMetrics(
            Long databaseId, Long instanceId, String tableName) {
        return bloatMapper.findLatestByTable(databaseId, instanceId, tableName);
    }

    public List<VacuumRawMetricDto> getTableBloatTrend(
            Long databaseId, Long instanceId, String tableName,
            OffsetDateTime start, OffsetDateTime end) {
        return bloatMapper.findTableBloatTrend(databaseId, instanceId, tableName, start, end);
    }

    // ========== Index Bloat (Raw 데이터) ==========

    public List<VacuumRawMetricDto> getIndexBloatData(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return bloatMapper.findIndexBloatData(databaseId, instanceId, start, end);
    }

    // ========== 메타데이터 ==========
    // ⚠️ 주의: 메타데이터는 Service 레벨에서 실제 PostgreSQL 인스턴스에 연결하여 조회
    // (MyBatis Mapper는 모니터링 시스템의 메인 DB에 연결되므로 사용하지 않음)
}