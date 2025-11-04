package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Vacuum Risk 페이지 응답 DTO
 * - 프론트엔드 DashboardData 타입과 매핑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacuumRiskDto {
    private ChartDto blockers;
    private ChartDto autovacuum;
    private ChartDto wraparound;
    private List<TopBloatTableDto> bloat;
    private List<VacuumBlockerDto> vacuumblockers;
}