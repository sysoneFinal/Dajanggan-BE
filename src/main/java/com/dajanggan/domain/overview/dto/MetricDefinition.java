package com.dajanggan.domain.overview.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class MetricDefinition {
    private Long metricId;
    private String name;           // 메트릭명 (avg_system_cpu)
    private String tableName;      // 테이블명
    private String columnName;     // 컬럼명
    private String category;       // 카테고리
    private String level;          // 레벨
    private String unit;           // 단위
    private String description;    // 설명
}
