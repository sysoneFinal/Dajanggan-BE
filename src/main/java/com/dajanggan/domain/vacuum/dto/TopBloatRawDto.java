package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top Bloat Tables Raw DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopBloatRawDto {
    private Long databaseId;
    private Long bloatBytes;
    private Double bloatRatio;
    private Long tableSize;
    private Long deadTuples;
}