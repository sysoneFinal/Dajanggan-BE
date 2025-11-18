package com.dajanggan.domain.engine.hotindex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HotIndex 리스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotIndexListResponse {
    private List<HotIndexListItem> data;
    private Long total;
}

