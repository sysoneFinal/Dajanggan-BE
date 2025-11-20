package com.dajanggan.domain.system.cpu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CPU 리스트 아이템 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuListItem {
    private String id;
    private String time;
    private Double totalCPU;
    private Double userCPU;
    private Double systemCPU;
    private Double idleCPU;
    private Double ioWait;
    private Double stealCPU;
    private Double loadAvg1;
    private Double loadAvg5;
    private Double loadAvg15;
    private Integer activeSessions;
    private Integer parallelWorkers;
    private Integer waitingSessions;
    private Double workerTime;
    private Long contextSwitches;
    private String status;
}



