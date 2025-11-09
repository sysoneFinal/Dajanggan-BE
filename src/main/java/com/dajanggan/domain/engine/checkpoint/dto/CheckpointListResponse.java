package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Checkpoint List 응답 Wrapper
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointListResponse {

    private List<CheckpointListDto> data;
    private Long totalElements;             // 전체 데이터 수
    private Integer totalPages;             // 전체 페이지 수
    private Integer currentPage;            // 현재 페이지
    private Integer size;                   // 페이지 크기
    private Boolean success;                // 성공 여부
}
