package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * I/O 블록 트렌드 DTO
 * - 시간대별 평균 I/O 블록 수 추이 데이터
 * - ExecutionStatus 차트용
 *
 * 작성자: 이해든
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class IoBlockTrendDto {

    /** 데이터 수집 시각 */
    private OffsetDateTime collectedAt;

    /** 평균 I/O 블록 수 */
    private Double avgIoBlocks;
}