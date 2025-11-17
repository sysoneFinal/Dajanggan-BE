package com.dajanggan.domain.osmetric.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Redis에 저장될 OS 메트릭 데이터
 */
@Data
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
     * 메트릭 타입 (CPU, MEMORY, DISK)
     */
    private String metricType;
    
    /**
     * 메트릭 상세 정보 (JSON 형태)
     */
    private Map<String, Object> details;
    
    /**
     * 수집 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime collectedAt;
    
    /**
     * Redis 키 생성
     * 패턴: os:metric:live:{instanceId}:{metricType}
     * List 형태로 저장하므로 timestamp 제외
     */
    public String toRedisKey() {
        return String.format("os:metric:live:%d:%s",
                instanceId,
                metricType);
    }
    
    /**
     * 집계를 위한 대표값 추출
     * - CPU: totalUsage
     * - MEMORY: usagePercent
     * - DISK: filesystem.usagePercent (우선), readSpeedMBps + writeSpeedMBps (없으면 0)
     */
    public Double getValue() {
        if (details == null || details.isEmpty()) {
            return 0.0;
        }
        
        try {
            switch (metricType) {
                case "CPU":
                    Object totalUsage = details.get("totalUsage");
                    return toDouble(totalUsage);
                    
                case "MEMORY":
                    Object memUsage = details.get("usagePercent");
                    return toDouble(memUsage);
                    
                case "DISK":
                    // 파일시스템 사용률을 우선 사용
                    Object filesystem = details.get("filesystem");
                    if (filesystem instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fsMap = (Map<String, Object>) filesystem;
                        Object fsUsage = fsMap.get("usagePercent");
                        if (fsUsage != null) {
                            return toDouble(fsUsage);
                        }
                    }
                    
                    // 파일시스템이 없으면 I/O 속도 합계 사용
                    Double readSpeed = toDouble(details.get("readSpeedMBps"));
                    Double writeSpeed = toDouble(details.get("writeSpeedMBps"));
                    return (readSpeed != null ? readSpeed : 0.0) + 
                           (writeSpeed != null ? writeSpeed : 0.0);
                    
                default:
                    return 0.0;
            }
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Object를 Double로 변환
     */
    private Double toDouble(Object obj) {
        if (obj == null) {
            return 0.0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
