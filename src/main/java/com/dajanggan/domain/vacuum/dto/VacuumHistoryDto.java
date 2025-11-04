package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vacuum History 목록 응답 DTO
 * - 프론트엔드 VacuumHistoryRow 타입과 매핑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacuumHistoryDto {
    private String table;
    private String lastVacuum;
    private String lastAutovacuum;
    private String deadTuples;
    private String modSinceAnalyze;
    private String bloatPct;
    private String tableSize;
    private String frequency;
    private String status;  // "주의" or "정상"
}