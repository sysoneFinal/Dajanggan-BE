package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Blockers per Hour Raw DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockersPerHourRawDto {
    private String hourLabel;
    private Integer blockersCount;
}