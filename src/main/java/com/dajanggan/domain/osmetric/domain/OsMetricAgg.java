// 작성자 : 김동현
package com.dajanggan.domain.osmetric.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * OS 메트릭 집계 데이터 엔티티
 * 테이블: os_metric_agg
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsMetricAgg {
    
    private Long osMetricAggId;          // PK
    private Long instanceId;             // 인스턴스 ID
    private OffsetDateTime collectedAt;  // 수집 시각 (1분 단위)
    private String metricType;           // 메트릭 타입: CPU, MEMORY, DISK_USAGE, DISK_READ, DISK_WRITE
    private Double avgValue;             // 평균값
    private Double maxValue;             // 최대값
    private Double minValue;             // 최소값
    private Integer sampleCount;         // 샘플 개수 (정상: 12개)
    private OffsetDateTime createdAt;    // 생성 시각
}
