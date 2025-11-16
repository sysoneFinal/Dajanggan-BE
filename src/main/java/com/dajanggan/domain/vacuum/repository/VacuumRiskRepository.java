package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumRiskRepository {

    private final VacuumRiskMapper mapper;

    // ✅ aggTable 제거
    public List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(
            Long dbId, OffsetDateTime start, OffsetDateTime end) {
        return mapper.getBlockersPerHour(dbId, start, end);
    }

    public List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(
            Long dbId, int limit, OffsetDateTime start, OffsetDateTime end) {
        return mapper.getTopBloatTables(dbId, limit, start, end);
    }

    public List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers(
            Long dbId, OffsetDateTime start, OffsetDateTime end) {
        return mapper.getVacuumBlockers(dbId, start, end);
    }

    public List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress(
            Long dbId, OffsetDateTime start, OffsetDateTime end) {
        return mapper.getWraparoundProgress(dbId, start, end);
    }
}
