package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vacuum Blocker Detail Raw DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacuumBlockerDetailRawDto {
    private Long databaseId;
    private Integer pid;
    private String lockType;
    private Long transactionAge;
    private Long blockDuration;
    private String queryState;
}