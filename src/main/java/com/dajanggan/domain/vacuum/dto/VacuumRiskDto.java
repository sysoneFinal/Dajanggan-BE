package com.dajanggan.domain.vacuum.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class VacuumRiskDto {

    @Getter @Setter
    public static class Response {
        private ChartDto blockers;
        private ChartDto wraparound;
        private List<TopBloatTableDto> bloat;
        private List<VacuumBlockerDto> vacuumblockers;
        private ScatterDto transactionScatter;
    }

    @Getter @Setter
    public static class ChartDto {
        private List<String> labels;
        private List<List<? extends Number>> data;
    }

    @Getter @Setter
    public static class TopBloatTableDto {
        private String table;
        private String bloat;
        private String deadTuple;
    }

    @Getter @Setter
    public static class VacuumBlockerDto {
        private String table;
        private String pid;
        private String lockType;
        private String txAge;
        private String blocked_seconds;
        private String status;
    }

    @Getter @Setter
    public static class ScatterDto {
        private List<List<Long>> data;
        private List<String> labels;
    }

    /* --------- Mapper Raw DTO --------- */

    @Getter @Setter
    public static class BlockersPerHourRaw {
        private String hourLabel;
        private Integer blockersCount;
    }

    @Getter @Setter
    public static class TopBloatRaw {
        private Long databaseId;
        private String tableName;
        private Long bloatBytes;
        private Double bloatRatio;
        private Long deadTuples;
    }

    @Getter @Setter
    public static class VacuumBlockerDetailRaw {
        private Long databaseId;
        private String tableName;
        private Integer pid;
        private String lockType;
        private Long transactionAge;
        private Long blockDuration;
        private String queryState;
    }

    @Getter @Setter
    public static class WraparoundProgressRaw {
        private Long databaseId;
        private Double wraparoundProgressPct;
    }
}