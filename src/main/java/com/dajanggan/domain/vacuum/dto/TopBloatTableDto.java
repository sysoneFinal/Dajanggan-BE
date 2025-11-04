package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top Bloat Tables DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopBloatTableDto {
    private String table;
    private String bloat;
    private String tableSize;
    private String deadTuple;
}