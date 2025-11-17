package com.dajanggan.domain.osmetric.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SSE로 전송될 OS 메트릭 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseOsMetricResponse {
    
    /**
     * 인스턴스 ID
     */
    private Long instanceId;
    
    /**
     * 수집 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime collectedAt;
    
    /**
     * 메트릭 데이터 맵
     * Key: 메트릭 타입 (CPU, MEMORY, DISK_USAGE, DISK_READ, DISK_WRITE)
     * Value: 메트릭 값
     */
    private Map<String, Double> metrics;
    
    /**
     * 이벤트 타입 (SSE event type)
     */
    private String eventType;
    
    public static SseOsMetricResponse of(Long instanceId, 
                                          LocalDateTime collectedAt, 
                                          Map<String, Double> metrics) {
        return SseOsMetricResponse.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .metrics(metrics)
                .eventType("os-metric")
                .build();
    }
}
