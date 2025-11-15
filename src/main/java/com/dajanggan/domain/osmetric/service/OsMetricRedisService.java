package com.dajanggan.domain.osmetric.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
    
    private static final String KEY_PREFIX = "os:metric:live:";
    private static final Duration TTL = Duration.ofMinutes(2); // 2분 후 자동 삭제
    
    /**
     * Redis에 OS 메트릭 데이터 저장
     * 
     * @param metricData Redis에 저장할 메트릭 데이터
     */
    public void save(RedisOsMetricData metricData) {
        try {
            String key = metricData.toRedisKey();
            redisTemplate.opsForValue().set(key, metricData, TTL);
            
            log.debug("Redis 저장 성공: key={}, type={}, value={}", 
                    key, metricData.getMetricType(), metricData.getValue());
        } catch (Exception e) {
            log.error("Redis 저장 실패: instanceId={}, type={}", 
                    metricData.getInstanceId(), metricData.getMetricType(), e);
            throw new RuntimeException("Redis 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 특정 인스턴스의 최근 1분간 데이터 조회
     * OsMetricService에서 List로 저장한 데이터를 조회
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
            // CPU, MEMORY, DISK_READ, DISK_WRITE 4가지 메트릭 타입
            List<String> metricTypes = List.of("CPU", "MEMORY", "DISK_READ", "DISK_WRITE");
            
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
    private RedisOsMetricData convertToRedisOsMetricData(Object obj) {
        try {
            if (obj instanceof RedisOsMetricData) {
                return (RedisOsMetricData) obj;
            } else if (obj instanceof java.util.Map) {
                // Jackson이 Map으로 역직렬화한 경우
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
                
                return RedisOsMetricData.builder()
                        .instanceId(getLongValue(map.get("instanceId")))
                        .metricType((String) map.get("metricType"))
                        .value(getDoubleValue(map.get("value")))
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
    
    private Double getDoubleValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
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
     * 특정 키 패턴의 데이터 삭제
     * 
     * @param instanceId 인스턴스 ID
     * @param startTime 삭제할 데이터의 시작 시간
     */
    public void deleteMetrics(Long instanceId, LocalDateTime startTime) {
        try {
            String pattern = KEY_PREFIX + instanceId + ":" + startTime + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Redis 데이터 삭제 완료: instanceId={}, count={}", instanceId, keys.size());
            }
        } catch (Exception e) {
            log.error("Redis 삭제 실패: instanceId={}", instanceId, e);
        }
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
            String pattern = KEY_PREFIX + instanceId + ":*:" + metricType;
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys == null || keys.isEmpty()) {
                return null;
            }
            
            // 가장 최근 키 찾기
            return keys.stream()
                    .map(key -> (RedisOsMetricData) redisTemplate.opsForValue().get(key))
                    .filter(data -> data != null)
                    .max((a, b) -> a.getCollectedAt().compareTo(b.getCollectedAt()))
                    .orElse(null);
            
        } catch (Exception e) {
            log.error("Redis 최신 데이터 조회 실패: instanceId={}, type={}", instanceId, metricType, e);
            return null;
        }
    }
}
