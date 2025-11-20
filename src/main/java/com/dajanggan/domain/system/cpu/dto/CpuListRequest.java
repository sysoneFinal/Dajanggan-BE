package com.dajanggan.domain.system.cpu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CPU 리스트 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuListRequest {
    private String timeRange;
    private String status;
}



