package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vacuum Blocker DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacuumBlockerDto {
    private String table;
    private String pid;
    private String lockType;
    private String txAge;
    private String blocked_seconds;
    private String status;
}