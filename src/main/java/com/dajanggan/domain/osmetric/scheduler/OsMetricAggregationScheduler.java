package com.dajanggan.domain.osmetric.scheduler;

import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.dto.OsMetricResponse;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OS 메트릭 집계 스케줄러
 * 1분마다 Redis 데이터를 집계하여 PostgreSQL에 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OsMetricAggregationScheduler {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final OsMetricMapper osMetricMapper;
    private final InstanceRepository instanceRepository;
    
    private static final String REDIS_KEY_PREFIX = "os:metric:live:";
    
    @PostConstruct
    public void init() {
        log.info("========== OsMetricAggregationScheduler Bean Created ==========");
    }
    
    /**
     * 1분마다 실행 (정시에 실행: 매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void aggregateMetrics() {
        log.info("========== Starting OS metric aggregation ==========");
        
        try {
            // 1. Redis에서 모든 metric 키 조회
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            
            log.info("Found {} keys in Redis", keys != null ? keys.size() : 0);
            
            if (keys == null || keys.isEmpty()) {
                log.info("No metrics found in Redis");
                return;
            }
            
            List<OsMetricAgg> aggList = new ArrayList<>();
            OffsetDateTime collectedAt = OffsetDateTime.now()
                    .truncatedTo(ChronoUnit.MINUTES); // 현재 시간을 분 단위로 절삭
            
            // 2. 각 키에 대해 집계 수행
            for (String key : keys) {
                try {
                    log.debug("Processing key: {}", key);
                    OsMetricAgg agg = aggregateFromRedis(key, collectedAt);
                    if (agg != null) {
                        aggList.add(agg);
                        log.debug("Successfully aggregated key: {}", key);
                    } else {
                        log.warn("Failed to aggregate key: {}", key);
                    }
                } catch (Exception e) {
                    log.error("Error aggregating key: {}", key, e);
                }
            }
            
            // 3. 집계 결과를 PostgreSQL에 배치 저장
            if (!aggList.isEmpty()) {
                log.info("Inserting {} aggregated metrics to database", aggList.size());
                osMetricMapper.insertAggBatch(aggList);
                log.info("Successfully aggregated {} metrics", aggList.size());
            } else {
                log.warn("No metrics were aggregated");
            }
            
        } catch (Exception e) {
            log.error("Error in OS metric aggregation", e);
        }
        
        log.info("========== OS metric aggregation completed ==========");
    }
    
    /**
     * Redis 키에서 데이터를 읽어서 집계
     */
    private OsMetricAgg aggregateFromRedis(String key, OffsetDateTime collectedAt) {
        // 키 파싱: os:metric:live:{instanceId}:{metricType}
        String[] parts = key.split(":");
        if (parts.length != 5) {
            log.warn("Invalid key format: {}", key);
            return null;
        }
        
        Long instanceId = Long.parseLong(parts[3]);
        String metricType = parts[4];
        
        // Redis에서 데이터 조회
        List<Object> dataList = redisTemplate.opsForList().range(key, 0, -1);
        
        if (dataList == null || dataList.isEmpty()) {
            log.debug("No data for key: {}", key);
            return null;
        }
        
        log.debug("Found {} items in Redis for key: {}", dataList.size(), key);
        
        // 첫 번째 아이템의 타입 확인
        if (!dataList.isEmpty()) {
            Object firstItem = dataList.get(0);
            log.debug("First item type: {}, value: {}", 
                    firstItem.getClass().getName(), firstItem);
        }
        
        // 통계 계산
        double sum = 0.0;
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        int count = 0;
        
        for (Object obj : dataList) {
            try {
                double value = extractValue(obj);
                
                sum += value;
                max = Math.max(max, value);
                min = Math.min(min, value);
                count++;
            } catch (Exception e) {
                log.warn("Failed to extract value from object: {}", obj, e);
            }
        }
        
        if (count == 0) {
            log.warn("No valid values found in key: {}", key);
            return null;
        }
        
        double avg = sum / count;
        
        // 집계 객체 생성
        OsMetricAgg agg = OsMetricAgg.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .metricType(metricType)
                .avgValue(avg)
                .maxValue(max)
                .minValue(min)
                .sampleCount(count)
                .build();
        
        log.debug("Aggregated: instanceId={}, metricType={}, avg={}, max={}, min={}, samples={}", 
                instanceId, metricType, avg, max, min, count);
        
        return agg;
    }
    
    /**
     * Redis 객체에서 value 추출
     * OsMetricResponse 객체 또는 Map 형태 모두 처리
     */
    private double extractValue(Object obj) {
        if (obj instanceof OsMetricResponse) {
            return ((OsMetricResponse) obj).getValue();
        } else if (obj instanceof java.util.Map) {
            // Jackson이 Map으로 역직렬화한 경우
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
            Object valueObj = map.get("value");
            
            if (valueObj instanceof Number) {
                return ((Number) valueObj).doubleValue();
            } else if (valueObj instanceof String) {
                return Double.parseDouble((String) valueObj);
            }
        }
        
        throw new IllegalArgumentException("Cannot extract value from object: " + obj.getClass().getName());
    }
}
