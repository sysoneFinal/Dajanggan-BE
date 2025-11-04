package com.dajanggan.domain.vacuum.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Vacuum 대시보드 응답 DTO
 * - 프론트엔드 DashboardData 타입과 매핑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacuumDashboardDto {
    private KpiDto kpi;
    private ChartDto deadtuple;
    private ChartDto autovacuum;
    private ChartDto latency;
    private List<VacuumSessionDto> sessions;
}
