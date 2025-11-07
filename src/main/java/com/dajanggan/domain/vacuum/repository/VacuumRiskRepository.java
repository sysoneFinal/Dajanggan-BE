package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vacuum Risk Repository
 * - Risk 페이지 전용 Repository
 */
@Repository
@RequiredArgsConstructor
public class VacuumRiskRepository {

    private final VacuumRiskMapper riskMapper;

    /**
     * 시간대별 Blockers 수 조회
     */
    public List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return riskMapper.getBlockersPerHour(start, end, buckets);
    }

    /**
     * Bloat 상위 테이블 조회
     */
    public List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(int limit) {
        return riskMapper.getTopBloatTables(limit);
    }

    /**
     * Vacuum Blocker 목록 조회
     */
    public List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers() {
        return riskMapper.getVacuumBlockers();
    }

    /**
     * Wraparound 진행률 조회
     */
    public List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress() {
        return riskMapper.getWraparoundProgress();
    }
}