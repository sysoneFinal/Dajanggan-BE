package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacuumSessionDto {
    private String table;
    private String phase;
    private String deadTuples;
    private String trigger;
    private String elapsed;
    private List<Integer> progressSeries;
}