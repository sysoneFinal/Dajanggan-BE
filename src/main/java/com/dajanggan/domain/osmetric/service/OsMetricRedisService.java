package com.dajanggan.domain.osmetric.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
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
            // Redis 연결 실패인지 확인
            Throwable cause = e;
            boolean isRedisConnectionFailure = false;
            while (cause != null) {
                if (cause instanceof RedisConnectionFailureException) {
                    isRedisConnectionFailure = true;
                    break;
                }
                cause = cause.getCause();
            }
            
            if (isRedisConnectionFailure || e instanceof RedisConnectionFailureException) {
                // Redis 연결 실패 시 더 명확한 에러 메시지
                String rootCause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                String redisHost = System.getenv("REDIS_HOST");
                if (redisHost == null || redisHost.isEmpty()) {
                    redisHost = "환경변수 미설정 (기본값: localhost)";
                }
                
                log.error("Redis 연결 실패: Redis 서버에 연결할 수 없습니다. " +
                        "현재 REDIS_HOST 환경변수: '{}'. " +
                        "로컬 개발 환경인 경우 REDIS_HOST=localhost로 설정하거나, " +
                        "Redis 서버를 실행하세요. " +
                        "instanceId={}, type={}, error={}, rootCause={}", 
                        redisHost, metricData.getInstanceId(), metricData.getMetricType(), 
                        e.getMessage(), rootCause);
                
                // Redis 연결 실패 시 예외를 던지지 않고 경고만 로깅
                // (실시간 메트릭이므로 Redis가 없어도 애플리케이션은 계속 동작해야 함)
                log.warn("Redis 저장 실패로 인해 메트릭 데이터가 저장되지 않았습니다. " +
                        "Redis 연결을 복구하면 정상 동작합니다.");
                // 예외를 던지지 않고 조용히 실패 처리
                return;
            } else {
                // 다른 예외인 경우 로깅 후 예외 던지기
                log.error("Redis 저장 실패: instanceId={}, type={}", 
                        metricData.getInstanceId(), metricData.getMetricType(), e);
                throw new RuntimeException("Redis 저장 중 오류 발생", e);
            }
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
                                                      OffsetDateTime startTime, 
                                                      OffsetDateTime endTime) {
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
                                        data.getCollectedAt() != null &&
                                        !data.getCollectedAt().isBefore(startTime.toLocalDateTime()) &&
                                        !data.getCollectedAt().isAfter(endTime.toLocalDateTime()));
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
     * CPU, MEMORY, DISK 최신 데이터를 배치로 조회 (SSE용 최적화)
     * Redis Pipeline을 사용하여 네트워크 왕복을 1회로 줄임
     * 
     * @param instanceId 인스턴스 ID
     * @return 메트릭 타입별 최신 데이터 맵 (key: "CPU", "MEMORY", "DISK")
     */
    public Map<String, RedisOsMetricData> getLatestMetricsBatch(Long instanceId) {
        try {
            // Redis Pipeline을 사용하여 3개 키를 한 번에 조회
            @SuppressWarnings("unchecked")
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                List<String> metricTypes = List.of("CPU", "MEMORY", "DISK");
                for (String metricType : metricTypes) {
                    String key = KEY_PREFIX + instanceId + ":" + metricType;
                    // Redis List의 마지막 요소 조회 (LINDEX key -1)
                    connection.listCommands().lIndex(key.getBytes(), -1);
                }
                return null; // Pipeline에서는 반환값이 무시됨
            });

            // 결과를 맵으로 변환
            Map<String, RedisOsMetricData> metricsMap = new HashMap<>();
            List<String> metricTypes = List.of("CPU", "MEMORY", "DISK");
            
            for (int i = 0; i < metricTypes.size() && i < results.size(); i++) {
                String metricType = metricTypes.get(i);
                Object result = results.get(i);
                
                if (result != null) {
                    RedisOsMetricData metricData = convertToRedisOsMetricData(result);
                    if (metricData != null) {
                        metricsMap.put(metricType, metricData);
                    }
                }
            }
            
            log.debug("Redis 배치 조회 성공: instanceId={}, 조회된 메트릭 수={}", instanceId, metricsMap.size());
            return metricsMap;
            
        } catch (Exception e) {
            log.error("Redis 배치 조회 실패: instanceId={}", instanceId, e);
            // 실패 시 빈 맵 반환 (기존 동작과 동일하게 null 대신 빈 맵)
            return new HashMap<>();
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
                                                            OffsetDateTime startTime, OffsetDateTime endTime) {
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
                            data.getCollectedAt() != null &&
                            !data.getCollectedAt().isBefore(startTime.toLocalDateTime()) &&
                            !data.getCollectedAt().isAfter(endTime.toLocalDateTime()))
                    .sorted((a, b) -> a.getCollectedAt().compareTo(b.getCollectedAt()))
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Redis 조회 실패: instanceId={}, type={}", instanceId, metricType, e);
            return List.of();
        }
    }
}
