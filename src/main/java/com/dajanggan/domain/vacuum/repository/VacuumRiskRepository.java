package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumRiskRepository {

    private final VacuumRiskMapper riskMapper;

    public List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(
            OffsetDateTime start, OffsetDateTime end, int buckets) {
        return riskMapper.getBlockersPerHour(start, end, buckets);
    }

    public List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(int limit) {
        return riskMapper.getTopBloatTables(limit);
    }

    public List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers() {
        return riskMapper.getVacuumBlockers();
    }

    public List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress() {
        return riskMapper.getWraparoundProgress();
    }
}