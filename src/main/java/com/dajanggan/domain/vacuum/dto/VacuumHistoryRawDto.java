package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Vacuum History Raw DTO
 * - DB에서 조회한 원시 데이터
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacuumHistoryRawDto {
    private Long databaseId;
    private Timestamp lastVacuum;
    private Timestamp lastAutovacuum;
    private Long deadTuples;
    private Long modSinceAnalyze;
    private Long bloatBytes;
    private Double bloatRatio;
    private Long tableSize;
    private Integer vacuumCount24h;
    private Integer autovacuumCount24h;
}