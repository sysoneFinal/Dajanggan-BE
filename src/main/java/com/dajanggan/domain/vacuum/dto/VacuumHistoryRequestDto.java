package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vacuum History 조회 요청 파라미터
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacuumHistoryRequestDto {
    private Integer hours;        // 시간 필터 (1, 6, 24, 168)
    private String status;        // 상태 필터 ("정상", "주의", null=전체)
}