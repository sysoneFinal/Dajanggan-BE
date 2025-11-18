package com.dajanggan.domain.vacuum.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 대시보드 응답 (프론트 스키마와 1:1 매핑) */
@Getter @Setter
public class VacuumRiskDto {

    @Getter @Setter
    public static class Response {
        private ChartDto blockers;                // Blockers per Hour
        private ChartDto wraparound;              // Wraparound Progress
        private List<TopBloatTableDto> bloat;     // Top bloat table rows
        private List<VacuumBlockerDto> vacuumblockers; // Blockers detail rows
        private ScatterDto transactionScatter;    // (선택) 산포도
    }

    /** 공통 차트 스키마 */
    @Getter @Setter
    public static class ChartDto {
        private List<String> labels;
        private List<List<? extends Number>> data;
    }

    /** Top bloat 테이블용 */
    @Getter @Setter
    public static class TopBloatTableDto {
        private String table;       // tableName
        private String bloat;       // "9.4%"
        private String deadTuple;   // "81K"
    }

    /** Vacuum blockers 상세 테이블용 */
    @Getter @Setter
    public static class VacuumBlockerDto {
        private String table;
        private String pid;
        private String lockType;
        private String txAge;              // "2h 31m"
        private String blocked_seconds;    // "12m"
        private String status;             // queryState
    }

    /** 산포도 (x=txAgeSec, y=blockedSec) */
    @Getter @Setter
    public static class ScatterDto {
        private List<List<Long>> data;     // [[x, y], ...]
        private List<String> labels;       // ["txAgeSec", "blockedSec"]
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
        private Double bloatRatio;   // 0.094 → 9.4%
        private Long deadTuples;
    }

    @Getter @Setter
    public static class VacuumBlockerDetailRaw {
        private Long databaseId;
        private String tableName;
        private Integer pid;
        private String lockType;
        private Long transactionAge;     // seconds
        private Long blockDuration;      // seconds
        private String queryState;
    }

    @Getter @Setter
    public static class WraparoundProgressRaw {
        private Long databaseId;
        private Double wraparoundProgressPct;  // 0~100
    }
}