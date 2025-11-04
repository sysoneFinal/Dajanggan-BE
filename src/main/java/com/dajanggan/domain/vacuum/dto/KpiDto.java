package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiDto {
    private Double avgDelay;           // 평균 지연시간
    private Double avgDuration;        // 평균 Duration
    private Double totalDeadTuple;     // 총 Dead Tuple (M)
    private Integer autovacuumWorker;  // Worker 활동률 (%)
}