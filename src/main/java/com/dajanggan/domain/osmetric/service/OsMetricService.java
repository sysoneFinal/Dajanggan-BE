package com.dajanggan.domain.osmetric.service;

import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.dto.OsMetricAggResponse;
import com.dajanggan.domain.osmetric.dto.OsMetricRequest;
import com.dajanggan.domain.osmetric.dto.OsMetricResponse;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.global.exception.ExceptionMessage;
import com.dajanggan.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OS 메트릭 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsMetricService {
    
    private final OsMetricMapper osMetricMapper;
    private final InstanceRepository instanceRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String REDIS_KEY_PREFIX = "os:metric:live:";
    private static final long REDIS_TTL_SECONDS = 300; // 5분
    
    /**
     * Agent로부터 메트릭 수신 및 저장
     * Redis에만 저장 (실시간 조회용)
     */
    @Transactional
    public void receiveMetric(OsMetricRequest request) {
        // 1. instance_name으로 instance_id 조회
        Long instanceId = instanceRepository.findIdByInstanceName(request.getInstanceName())
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));
        
        // 2. Redis에만 저장 (실시간 조회용)
        saveToRedis(instanceId, request);
    }
    
    /**
     * Redis에 메트릭 저장
     */
    private void saveToRedis(Long instanceId, OsMetricRequest request) {
        try {
            String key = REDIS_KEY_PREFIX + instanceId + ":" + request.getMetricType();
            
            // RedisOsMetricData 객체 생성
            RedisOsMetricData data = RedisOsMetricData.builder()
                    .instanceId(instanceId)
                    .metricType(request.getMetricType())
                    .value(request.getValue())
                    .collectedAt(request.getTimestamp())
                    .build();
            
            // List로 저장 (최근 데이터를 앞에 추가)
            redisTemplate.opsForList().leftPush(key, data);
            
            // TTL 설정
            redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
            
            // 최대 60개만 유지 (5분 / 5초 = 60개)
            redisTemplate.opsForList().trim(key, 0, 59);
            
        } catch (Exception e) {
            log.error("Redis 저장 실패: instanceId={}, metricType={}", 
                    instanceId, request.getMetricType(), e);
        }
    }
    
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
    private OsMetricResponse convertToOsMetricResponse(Object obj) {
        try {
            if (obj instanceof RedisOsMetricData) {
                RedisOsMetricData data = (RedisOsMetricData) obj;
                return OsMetricResponse.builder()
                        .instanceId(data.getInstanceId())
                        .instanceName(null) // instanceName은 필요시 조회
                        .metricType(data.getMetricType())
                        .value(data.getValue())
                        .timestamp(data.getCollectedAt())
                        .build();
            } else if (obj instanceof java.util.Map) {
                // Jackson이 Map으로 역직렬화한 경우
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
                
                return OsMetricResponse.builder()
                        .instanceId(getLongValue(map.get("instanceId")))
                        .instanceName(null)
                        .metricType((String) map.get("metricType"))
                        .value(getDoubleValue(map.get("value")))
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
    
    private Double getDoubleValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
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
