package com.dajanggan.domain.osmetric.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent로부터 받는 OS 메트릭 데이터 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOsMetricRequest {
    
    /**
     * 모니터링 인스턴스 ID
     */
    private Long instanceId;
    
    /**
     * 데이터 수집 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime collectedAt;
    
    /**
     * OS 메트릭 목록
     */
    private List<OsMetricData> metrics;
    
    /**
     * 개별 메트릭 데이터
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsMetricData {
        /**
         * 메트릭 타입
         * CPU, MEMORY, DISK_USAGE, DISK_READ, DISK_WRITE
         */
        private String metricType;
        
        /**
         * 메트릭 값
         */
        private Double value;
    }
}
