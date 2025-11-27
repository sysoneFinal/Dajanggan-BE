// 작성자: 김민서
package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg1mDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumMaintenanceRepository {

    private final VacuumRawMapper rawMapper;
    private final VacuumAgg1mMapper agg1mRepo;
    private final VacuumAgg5mMapper agg5mRepo;

    // ========== KPI 지표 (집계 테이블) ==========

    /**
     * KPI 요약 정보 조회
     */
    public VacuumAgg1mDto getKpiFrom1m(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return agg1mRepo.getKpiSummary(databaseId, instanceId, start, end);
    }

    public VacuumAgg5mDto getKpiFrom5m(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return agg5mRepo.getKpiSummary(databaseId, instanceId, start, end);
    }

    // ========== 차트 데이터 (집계 테이블) ==========

    /**
     * 1분 집계 시계열 데이터
     */
    public List<VacuumAgg1mDto> getTimeSeriesFrom1m(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return agg1mRepo.findByTimeRange(databaseId, instanceId, start, end);
    }

    /**
     * 5분 집계 시계열 데이터
     */
    public List<VacuumAgg5mDto> getTimeSeriesFrom5m(
            Long databaseId, Long instanceId,
            OffsetDateTime start, OffsetDateTime end) {
        return agg5mRepo.findByTimeRange(databaseId, instanceId, start, end);
    }

    // ========== 세션 데이터 (Raw 데이터) ==========

    /**
     * 현재 실행 중인 Vacuum 세션
     */
    public List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions(
            Long databaseId, String tableName) {
        return rawMapper.getCurrentVacuumSessions(databaseId, tableName);
    }
}