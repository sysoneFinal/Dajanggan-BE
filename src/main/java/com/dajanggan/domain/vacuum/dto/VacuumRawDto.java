package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacuumRawDto {
    private String databaseId;
    private String sessionPhase;
    private Double sessionProgress;
    private Boolean autovacuum;
    private Long elapsedSeconds;
    private Long heapBlksTotal;
    private Long heapBlksScanned;
    private Long heapBlksVacuumed;
    private Long deadTupleTotal;
}