package com.dajanggan.domain.osmetric.scheduler;

import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.domain.OsMetricRaw;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import com.dajanggan.domain.osmetric.service.OsMetricSseService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OS 메트릭 집계 스케줄러
 * 1분마다 실행:
 * 1. Redis에서 최근 1분간 데이터 조회 (12개)
 * 2. 첫 번째 데이터만 os_metric_raw에 저장
 * 3. 12개 데이터로 집계 계산 후 os_metric_agg에 저장
 * 4. SSE로 실시간 데이터 브로드캐스트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OsMetricAggregationScheduler {
    
    private final OsMetricRedisService redisService;
    private final OsMetricMapper osMetricMapper;
    private final OsMetricSseService sseService;
    
    @PostConstruct
    public void init() {
        log.info("========== OsMetricAggregationScheduler 초기화 완료 ==========");
    }
    
    /**
     * 1분마다 실행 (매분 0초)
     * SSE 브로드캐스트는 더 자주 실행 (5초마다)
     */
    @Scheduled(cron = "0 * * * * *")
    public void aggregateMetrics() {
        log.info("========== OS 메트릭 집계 시작 ==========");
        
        try {
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            LocalDateTime startTime = now.minusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime endTime = now.truncatedTo(ChronoUnit.MINUTES);
            
            List<Long> instanceIds = getActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (Long instanceId : instanceIds) {
                try {
                    processInstanceMetrics(instanceId, startTime, endTime);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("OS 메트릭 처리 실패: instanceId={}", instanceId, e);
                }
            }
            
            log.info("========== OS 메트릭 집계 완료: 성공={}, 실패={} ==========", successCount, failCount);
            
        } catch (Exception e) {
            log.error("OS 메트릭 집계 중 오류 발생", e);
        }
    }
    
    /**
     * SSE 브로드캐스트 (5초마다)
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    public void broadcastMetrics() {
        try {
            log.debug("SSE 브로드캐스트 시작");
            sseService.broadcastAllInstances();
        } catch (Exception e) {
            log.error("SSE 브로드캐스트 실패", e);
        }
    }
    
    /**
     * 특정 인스턴스의 메트릭 처리
     */
    private void processInstanceMetrics(Long instanceId, 
                                         LocalDateTime startTime, 
                                         LocalDateTime endTime) {
        List<RedisOsMetricData> allMetrics = redisService.getRecentMetrics(
                instanceId, startTime, endTime);
        
        if (allMetrics.isEmpty()) {
            return;
        }
        
        Map<String, List<RedisOsMetricData>> metricsByType = allMetrics.stream()
                .collect(Collectors.groupingBy(RedisOsMetricData::getMetricType));
        
        List<OsMetricRaw> rawList = new ArrayList<>();
        List<OsMetricAgg> aggList = new ArrayList<>();
        
        OffsetDateTime collectedAt = OffsetDateTime.now()
                .truncatedTo(ChronoUnit.MINUTES);
        
        for (Map.Entry<String, List<RedisOsMetricData>> entry : metricsByType.entrySet()) {
            String metricType = entry.getKey();
            List<RedisOsMetricData> typeMetrics = entry.getValue();
            
            typeMetrics.sort(Comparator.comparing(RedisOsMetricData::getCollectedAt));
            
            // Raw 데이터 저장 (첫 번째 데이터)
            if (!typeMetrics.isEmpty()) {
                RedisOsMetricData firstMetric = typeMetrics.get(0);
                OsMetricRaw raw = OsMetricRaw.builder()
                        .instanceId(instanceId)
                        .collectedAt(OffsetDateTime.of(firstMetric.getCollectedAt(), 
                                OffsetDateTime.now().getOffset()))
                        .metricType(metricType)
                        .details(firstMetric.getDetails())  // details 전달
                        .build();
                rawList.add(raw);
            }
            
            // Agg 데이터 계산 및 저장
            double sum = 0.0;
            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            int count = 0;
            
            for (RedisOsMetricData metric : typeMetrics) {
                double value = metric.getValue();  // 대표값 추출
                sum += value;
                max = Math.max(max, value);
                min = Math.min(min, value);
                count++;
            }
            
            if (count > 0) {
                double avg = sum / count;
                
                OsMetricAgg agg = OsMetricAgg.builder()
                        .instanceId(instanceId)
                        .collectedAt(collectedAt)
                        .metricType(metricType)
                        .avgValue(avg)
                        .maxValue(max)
                        .minValue(min)
                        .sampleCount(count)
                        .build();
                aggList.add(agg);
            }
        }
        
        // DB 저장
        if (!rawList.isEmpty()) {
            osMetricMapper.insertRawBatch(rawList);
            log.debug("Raw 데이터 일괄 저장 완료: instanceId={}, count={}", instanceId, rawList.size());
        }
        
        if (!aggList.isEmpty()) {
            osMetricMapper.insertAggBatch(aggList);
            log.debug("Agg 데이터 일괄 저장 완료: instanceId={}, count={}", instanceId, aggList.size());
        }

        log.info("메트릭 처리 완료: instanceId={}, raw={}, agg={}, samples={}", 
                instanceId, rawList.size(), aggList.size(), allMetrics.size());
    }
    
    /**
     * 활성 인스턴스 ID 목록 조회
     */
    private List<Long> getActiveInstanceIds() {
        // 임시로 하드코딩 (실제로는 DB 조회)
        return osMetricMapper.selectActiveInstanceIds();
    }
}
