package com.dajanggan.domain.osmetric.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Redis에 저장될 OS 메트릭 데이터
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisOsMetricData implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 모니터링 인스턴스 ID
     */
    private Long instanceId;
    
    /**
     * 메트릭 타입
     */
    private String metricType;
    
    /**
     * 메트릭 값
     */
    private Double value;
    
    /**
     * 수집 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime collectedAt;
    
    /**
     * Redis 키 생성
     * 패턴: os:metric:live:{instanceId}:{timestamp}:{metricType}
     */
    public String toRedisKey() {
        return String.format("os:metric:live:%d:%s:%s",
                instanceId,
                collectedAt.toString(),
                metricType);
    }
}
