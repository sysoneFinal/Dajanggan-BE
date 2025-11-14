package com.dajanggan.domain.osmetric.scheduler;

import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.dto.OsMetricResponse;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
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
    
    /**
     * 1분마다 실행 (정시에 실행: 매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void aggregateMetrics() {
        log.info("Starting OS metric aggregation...");
        
        try {
            // 1. Redis에서 모든 metric 키 조회
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            
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
                    OsMetricAgg agg = aggregateFromRedis(key, collectedAt);
                    if (agg != null) {
                        aggList.add(agg);
                    }
                } catch (Exception e) {
                    log.error("Error aggregating key: {}", key, e);
                }
            }
            
            // 3. 집계 결과를 PostgreSQL에 배치 저장
            if (!aggList.isEmpty()) {
                osMetricMapper.insertAggBatch(aggList);
                log.info("Successfully aggregated {} metrics", aggList.size());
            }
            
        } catch (Exception e) {
            log.error("Error in OS metric aggregation", e);
        }
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
        
        // 통계 계산
        double sum = 0.0;
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        int count = 0;
        
        for (Object obj : dataList) {
            if (obj instanceof OsMetricResponse) {
                OsMetricResponse metric = (OsMetricResponse) obj;
                double value = metric.getValue();
                
                sum += value;
                max = Math.max(max, value);
                min = Math.min(min, value);
                count++;
            }
        }
        
        if (count == 0) {
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
        
        // Redis 키 삭제 (집계 완료 후)
        // 또는 오래된 데이터만 삭제하도록 수정 가능
        // redisTemplate.delete(key);
        
        return agg;
    }
}
