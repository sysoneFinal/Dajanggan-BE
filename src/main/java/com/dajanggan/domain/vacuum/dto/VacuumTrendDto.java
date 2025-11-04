package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacuumTrendDto {
    private String hourLabel;
    private Double deadTupleIncreaseRate;
    private Double avgProgress;
    private Double avgCostDelayMs;
    private Integer activeWorkers;
    private Double avgDelaySeconds;
}
