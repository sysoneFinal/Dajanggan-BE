package com.dajanggan.domain.osmetric.service;

import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.domain.OsMetricRaw;
import com.dajanggan.domain.osmetric.dto.OsMetricAggResponse;
import com.dajanggan.domain.osmetric.dto.OsMetricRequest;
import com.dajanggan.domain.osmetric.dto.OsMetricResponse;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.global.exception.ExceptionMessage;
import com.dajanggan.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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
     */
    @Transactional
    public void receiveMetric(OsMetricRequest request) {
        log.debug("Receiving OS metric: instanceName={}, metricType={}, value={}", 
                request.getInstanceName(), request.getMetricType(), request.getValue());
        
        // 1. instance_name으로 instance_id 조회
        Long instanceId = instanceRepository.findIdByInstanceName(request.getInstanceName())
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));
        
        // 2. Redis에 저장 (실시간 조회용)
        saveToRedis(instanceId, request);
        
        // 3. PostgreSQL Raw 테이블에 저장
        OsMetricRaw raw = OsMetricRaw.builder()
                .instanceId(instanceId)
                .collectedAt(OffsetDateTime.ofInstant(
                        request.getTimestamp().atZone(ZoneId.systemDefault()).toInstant(),
                        ZoneId.systemDefault()))
                .metricType(request.getMetricType())
                .value(request.getValue())
                .build();
        
        osMetricMapper.insertRaw(raw);
        
        log.debug("OS metric saved successfully: instanceId={}, metricType={}", 
                instanceId, request.getMetricType());
    }
    
    /**
     * Redis에 메트릭 저장
     */
    private void saveToRedis(Long instanceId, OsMetricRequest request) {
        try {
            String key = REDIS_KEY_PREFIX + instanceId + ":" + request.getMetricType();
            
            log.debug("Attempting to save to Redis: key={}", key);
            
            OsMetricResponse response = OsMetricResponse.builder()
                    .instanceId(instanceId)
                    .instanceName(request.getInstanceName())
                    .metricType(request.getMetricType())
                    .value(request.getValue())
                    .timestamp(request.getTimestamp())
                    .build();
            
            // List로 저장 (최근 데이터를 앞에 추가)
            redisTemplate.opsForList().leftPush(key, response);
            
            // TTL 설정
            redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
            
            // 최대 60개만 유지 (5분 / 5초 = 60개)
            redisTemplate.opsForList().trim(key, 0, 59);
            
            log.debug("Successfully saved to Redis: key={}, value={}", key, request.getValue());
            
        } catch (Exception e) {
            log.error("Failed to save to Redis: instanceId={}, metricType={}, error={}", 
                    instanceId, request.getMetricType(), e.getMessage(), e);
            // Redis 저장 실패해도 PostgreSQL 저장은 계속 진행
        }
    }
    
    /**
     * Redis에서 실시간 메트릭 조회
     */
    public List<OsMetricResponse> getRealTimeMetrics(Long instanceId, String metricType) {
        String key = REDIS_KEY_PREFIX + instanceId + ":" + metricType;
        
        List<Object> data = redisTemplate.opsForList().range(key, 0, -1);
        
        return data.stream()
                .map(obj -> (OsMetricResponse) obj)
                .toList();
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
