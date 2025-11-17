package com.dajanggan.domain.osmetric.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OS Metric Redis 서비스
 * - 5초마다 수집되는 실시간 데이터를 Redis에 저장/조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsMetricRedisService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String KEY_PREFIX = "os:metric:live:";
    private static final int MAX_LIST_SIZE = 12; // 1분간 데이터 (5초 * 12 = 60초)
    
    /**
     * Redis에 OS 메트릭 데이터 저장 (List 형태)
     * 키 패턴: os:metric:live:{instanceId}:{metricType}
     * 
     * @param metricData Redis에 저장할 메트릭 데이터
     */
    public void save(RedisOsMetricData metricData) {
        try {
            String key = metricData.toRedisKey();
            
            // List의 오른쪽에 추가 (최신 데이터)
            redisTemplate.opsForList().rightPush(key, metricData);
            
            // List 크기 제한 (최대 12개 = 1분)
            redisTemplate.opsForList().trim(key, -MAX_LIST_SIZE, -1);
            
            // TTL 설정 (2분)
            redisTemplate.expire(key, Duration.ofMinutes(2));
            
            log.debug("Redis 저장 성공: key={}, type={}, details={}", 
                    key, metricData.getMetricType(), metricData.getDetails());
        } catch (Exception e) {
            log.error("Redis 저장 실패: instanceId={}, type={}", 
                    metricData.getInstanceId(), metricData.getMetricType(), e);
            throw new RuntimeException("Redis 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 특정 인스턴스의 최근 1분간 데이터 조회
     * 
     * @param instanceId 인스턴스 ID
     * @param startTime 조회 시작 시간
     * @param endTime 조회 종료 시간
     * @return 메트릭 데이터 리스트
     */
    public List<RedisOsMetricData> getRecentMetrics(Long instanceId, 
                                                      LocalDateTime startTime, 
                                                      LocalDateTime endTime) {
        try {
            // CPU, MEMORY, DISK 3가지 메트릭 타입
            List<String> metricTypes = List.of("CPU", "MEMORY", "DISK");
            
            List<RedisOsMetricData> allMetrics = metricTypes.stream()
                    .flatMap(metricType -> {
                        // 키 패턴: os:metric:live:{instanceId}:{metricType}
                        String key = KEY_PREFIX + instanceId + ":" + metricType;
                        
                        // List에서 모든 데이터 조회 (0부터 -1까지 = 전체)
                        List<Object> values = redisTemplate.opsForList().range(key, 0, -1);
                        
                        if (values == null || values.isEmpty()) {
                            return List.<RedisOsMetricData>of().stream();
                        }
                        
                        // Object를 RedisOsMetricData로 변환하고 시간 필터링
                        return values.stream()
                                .map(this::convertToRedisOsMetricData)
                                .filter(data -> data != null &&
                                        !data.getCollectedAt().isBefore(startTime) &&
                                        !data.getCollectedAt().isAfter(endTime));
                    })
                    .sorted((a, b) -> a.getCollectedAt().compareTo(b.getCollectedAt()))
                    .collect(Collectors.toList());
            
            if (allMetrics.isEmpty()) {
                log.debug("Redis에서 데이터를 찾을 수 없음: instanceId={}", instanceId);
            } else {
                log.debug("Redis 조회 성공: instanceId={}, 데이터 수={}", instanceId, allMetrics.size());
            }
            
            return allMetrics;
            
        } catch (Exception e) {
            log.error("Redis 조회 실패: instanceId={}", instanceId, e);
            throw new RuntimeException("Redis 조회 중 오류 발생", e);
        }
    }
    
    /**
     * Object를 RedisOsMetricData로 변환
     */
    @SuppressWarnings("unchecked")
    private RedisOsMetricData convertToRedisOsMetricData(Object obj) {
        try {
            if (obj instanceof RedisOsMetricData) {
                return (RedisOsMetricData) obj;
            } else if (obj instanceof Map) {
                // Jackson이 Map으로 역직렬화한 경우
                Map<String, Object> map = (Map<String, Object>) obj;
                
                return RedisOsMetricData.builder()
                        .instanceId(getLongValue(map.get("instanceId")))
                        .metricType((String) map.get("metricType"))
                        .details((Map<String, Object>) map.get("details"))
                        .collectedAt(getLocalDateTime(map.get("collectedAt")))
                        .build();
            }
            log.warn("알 수 없는 타입: {}", obj.getClass().getName());
            return null;
        } catch (Exception e) {
            log.error("데이터 변환 실패", e);
            return null;
        }
    }
    
    private Long getLongValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return null;
    }
    
    private LocalDateTime getLocalDateTime(Object obj) {
        if (obj instanceof String) {
            return LocalDateTime.parse((String) obj);
        } else if (obj instanceof LocalDateTime) {
            return (LocalDateTime) obj;
        }
        return null;
    }
    
    /**
     * 특정 메트릭 타입의 최신 데이터 조회 (SSE용)
     * 
     * @param instanceId 인스턴스 ID
     * @param metricType 메트릭 타입
     * @return 최신 메트릭 데이터
     */
    public RedisOsMetricData getLatestMetric(Long instanceId, String metricType) {
        try {
            String key = KEY_PREFIX + instanceId + ":" + metricType;
            
            // List의 가장 마지막 요소 조회 (최신 데이터)
            Object obj = redisTemplate.opsForList().index(key, -1);
            
            if (obj == null) {
                return null;
            }
            
            return convertToRedisOsMetricData(obj);
            
        } catch (Exception e) {
            log.error("Redis 최신 데이터 조회 실패: instanceId={}, type={}", instanceId, metricType, e);
            return null;
        }
    }

    /**
     * 특정 메트릭 타입의 시간 범위 데이터 조회
     * 
     * @param instanceId 인스턴스 ID
     * @param metricType 메트릭 타입 (CPU, MEMORY, DISK)
     * @param startTime 조회 시작 시간
     * @param endTime 조회 종료 시간
     * @return 메트릭 데이터 리스트
     */
    public List<RedisOsMetricData> getRecentMetricsByType(Long instanceId, String metricType,
                                                            LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String key = KEY_PREFIX + instanceId + ":" + metricType;
            
            List<Object> values = redisTemplate.opsForList().range(key, 0, -1);
            
            if (values == null || values.isEmpty()) {
                log.debug("Redis에서 데이터를 찾을 수 없음: instanceId={}, type={}", instanceId, metricType);
                return List.of();
            }
            
            return values.stream()
                    .map(this::convertToRedisOsMetricData)
                    .filter(data -> data != null &&
                            !data.getCollectedAt().isBefore(startTime) &&
                            !data.getCollectedAt().isAfter(endTime))
                    .sorted((a, b) -> a.getCollectedAt().compareTo(b.getCollectedAt()))
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Redis 조회 실패: instanceId={}, type={}", instanceId, metricType, e);
            return List.of();
        }
    }
}
