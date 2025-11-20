package com.dajanggan.domain.engine.bgwriter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BGWriter 리스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgWriterListResponse {
    private List<BgWriterListItem> data;
    private Long total;
}



