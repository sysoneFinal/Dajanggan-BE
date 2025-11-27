// 작성자 : 김동현
package com.dajanggan.domain.osmetric.service;

import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.dto.OsMetricAggResponse;
import com.dajanggan.domain.osmetric.dto.OsMetricResponse;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * OS 메트릭 서비스
 * (Agent 데이터 수신은 AgentOsMetricController -> OsMetricRedisService가 담당)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsMetricService {
    
    private final OsMetricMapper osMetricMapper;
    private final InstanceRepository instanceRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String REDIS_KEY_PREFIX = "os:metric:live:";
    
    /**
     * Redis에서 실시간 메트릭 조회 (Controller용)
     */
    public List<OsMetricResponse> getRealTimeMetrics(Long instanceId, String metricType) {
        String key = REDIS_KEY_PREFIX + instanceId + ":" + metricType;
        
        List<Object> data = redisTemplate.opsForList().range(key, 0, -1);
        
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        
        return data.stream()
                .map(this::convertToOsMetricResponse)
                .toList();
    }
    
    /**
     * Redis 객체를 OsMetricResponse로 변환
     */
    @SuppressWarnings("unchecked")
    private OsMetricResponse convertToOsMetricResponse(Object obj) {
        try {
            if (obj instanceof RedisOsMetricData) {
                RedisOsMetricData data = (RedisOsMetricData) obj;
                return OsMetricResponse.builder()
                        .instanceId(data.getInstanceId())
                        .instanceName(null) // instanceName은 필요시 조회
                        .metricType(data.getMetricType())
                        .details(data.getDetails())
                        .timestamp(data.getCollectedAt())
                        .build();
            } else if (obj instanceof Map) {
                // Jackson이 Map으로 역직렬화한 경우
                Map<String, Object> map = (Map<String, Object>) obj;
                
                return OsMetricResponse.builder()
                        .instanceId(getLongValue(map.get("instanceId")))
                        .instanceName(null)
                        .metricType((String) map.get("metricType"))
                        .details((Map<String, Object>) map.get("details"))
                        .timestamp(getLocalDateTime(map.get("collectedAt")))
                        .build();
            }
            
            throw new IllegalArgumentException("Cannot convert object to OsMetricResponse: " + obj.getClass().getName());
        } catch (Exception e) {
            log.error("Failed to convert to OsMetricResponse", e);
            return null;
        }
    }
    
    private Long getLongValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return null;
    }
    
    private java.time.LocalDateTime getLocalDateTime(Object obj) {
        if (obj instanceof String) {
            return java.time.LocalDateTime.parse((String) obj);
        } else if (obj instanceof java.time.LocalDateTime) {
            return (java.time.LocalDateTime) obj;
        }
        return null;
    }
    
    /**
     * 집계 데이터 조회 (과거 데이터)
     */
    public List<OsMetricAggResponse> getAggregatedMetrics(
            Long instanceId, 
            String metricType,
            OffsetDateTime startTime,
            OffsetDateTime endTime) {
        
        List<OsMetricAgg> aggList = osMetricMapper.findAggByInstanceAndPeriod(
                instanceId, metricType, startTime, endTime);
        
        return aggList.stream()
                .map(agg -> OsMetricAggResponse.builder()
                        .instanceId(agg.getInstanceId())
                        .metricType(agg.getMetricType())
                        .avgValue(agg.getAvgValue())
                        .maxValue(agg.getMaxValue())
                        .minValue(agg.getMinValue())
                        .sampleCount(agg.getSampleCount())
                        .collectedAt(agg.getCollectedAt().toLocalDateTime())
                        .build())
                .toList();
    }
}
