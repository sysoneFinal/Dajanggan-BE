package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Checkpoint 리스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointListResponse {
    private List<CheckpointListItem> data;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer totalPages;
}


