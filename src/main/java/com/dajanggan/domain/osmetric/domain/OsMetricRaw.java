package com.dajanggan.domain.osmetric.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * OS 메트릭 Raw 데이터 엔티티
 * 테이블: os_metric_raw
 * OSHI Agent에서 5초마다 수집하는 OS 레벨 메트릭
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsMetricRaw {

    private Long osMetricRawId;          // PK
    private Long instanceId;             // 인스턴스 ID
    private OffsetDateTime collectedAt;  // 수집 시각
    private String metricType;           // 메트릭 타입: CPU, MEMORY, DISK

    /**
     * 메트릭 상세 정보 (JSONB)
     * CPU: totalUsage, perCoreUsage, loadAverage
     * MEMORY: total, used, available, swap
     * DISK: readBytes, writeBytes, readCount, writeCount, filesystem
     */
    private Map<String, Object> details;

    private OffsetDateTime createdAt;    // 생성 시각
}
