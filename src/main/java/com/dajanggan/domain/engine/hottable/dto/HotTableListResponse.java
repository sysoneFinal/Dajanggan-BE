package com.dajanggan.domain.engine.hottable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HotTable 리스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTableListResponse {
    private List<HotTableListItem> data;
    private Long total;
}



