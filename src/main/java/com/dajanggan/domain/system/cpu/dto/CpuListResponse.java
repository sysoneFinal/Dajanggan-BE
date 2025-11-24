package com.dajanggan.domain.system.cpu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CPU 리스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuListResponse {
    private List<CpuListItem> data;
    private Integer total;
    private Integer page;
    private Integer size;
    private Integer totalPages;
}


