package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wraparound Progress Raw DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WraparoundProgressRawDto {
    private Long databaseId;
    private Double wraparoundProgressPct;
}