package com.dajanggan.domain.query.dto.agg5m;

import lombok.*;

/**
 * Top 슬로우 쿼리 DTO
 *
 * 기능:
 * - 가장 느린 쿼리 Top 5 정보
 * - 5분 집계 데이터에서 추출
 * - QueryDetails 화면의 Top Slow Query 섹션용
 * - 쿼리문과 실행 시간만 포함 (간단한 요약)
 *
 * 작성자: 이해든
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class TopSlowQueryDto {

    private Long instanceId;
    private Long databaseId;

    // Top 5 슬로우 쿼리 (쿼리문과 실행 시간)
    private String topSlowQuery1;
    private Double topSlowQuery1Time;
    private String topSlowQuery2;
    private Double topSlowQuery2Time;
    private String topSlowQuery3;
    private Double topSlowQuery3Time;
    private String topSlowQuery4;
    private Double topSlowQuery4Time;
    private String topSlowQuery5;
    private Double topSlowQuery5Time;
}