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
            
            // DISK 메트릭은 세분화하여 처리
            if ("DISK".equals(metricType)) {
                processDiskMetrics(instanceId, typeMetrics, collectedAt, aggList);
            } else {
                // 일반 메트릭 처리 (CPU, MEMORY 등)
                processNormalMetrics(instanceId, metricType, typeMetrics, collectedAt, aggList);
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
     * DISK 메트릭을 DISK_READ, DISK_WRITE, DISK_USAGE로 세분화
     */
    private void processDiskMetrics(Long instanceId, 
                                     List<RedisOsMetricData> diskMetrics,
                                     OffsetDateTime collectedAt,
                                     List<OsMetricAgg> aggList) {
        // DISK_USAGE 집계
        double usageSum = 0.0, usageMax = Double.MIN_VALUE, usageMin = Double.MAX_VALUE;
        int usageCount = 0;
        
        // DISK_READ 집계 (bytes)
        double readSum = 0.0, readMax = Double.MIN_VALUE, readMin = Double.MAX_VALUE;
        int readCount = 0;
        
        // DISK_WRITE 집계 (bytes)
        double writeSum = 0.0, writeMax = Double.MIN_VALUE, writeMin = Double.MAX_VALUE;
        int writeCount = 0;
        
        for (RedisOsMetricData metric : diskMetrics) {
            Map<String, Object> details = metric.getDetails();
            if (details == null) continue;
            
            // 1. DISK_USAGE 처리
            Double usagePercent = extractDiskUsagePercent(details);
            if (usagePercent != null) {
                usageSum += usagePercent;
                usageMax = Math.max(usageMax, usagePercent);
                usageMin = Math.min(usageMin, usagePercent);
                usageCount++;
            }
            
            // 2. DISK_READ 처리 (readBytes)
            Double readBytes = extractValue(details, "readBytes");
            if (readBytes != null && readBytes > 0) {
                readSum += readBytes;
                readMax = Math.max(readMax, readBytes);
                readMin = Math.min(readMin, readBytes);
                readCount++;
            }
            
            // 3. DISK_WRITE 처리 (writeBytes)
            Double writeBytes = extractValue(details, "writeBytes");
            if (writeBytes != null && writeBytes > 0) {
                writeSum += writeBytes;
                writeMax = Math.max(writeMax, writeBytes);
                writeMin = Math.min(writeMin, writeBytes);
                writeCount++;
            }
        }
        
        // DISK_USAGE Agg 저장
        if (usageCount > 0) {
            OsMetricAgg usageAgg = OsMetricAgg.builder()
                    .instanceId(instanceId)
                    .collectedAt(collectedAt)
                    .metricType("DISK_USAGE")
                    .avgValue(usageSum / usageCount)
                    .maxValue(usageMax)
                    .minValue(usageMin)
                    .sampleCount(usageCount)
                    .build();
            aggList.add(usageAgg);
            log.debug("DISK_USAGE 집계: avg={}, max={}, min={}, count={}", 
                    usageAgg.getAvgValue(), usageMax, usageMin, usageCount);
        }
        
        // DISK_READ Agg 저장
        if (readCount > 0) {
            OsMetricAgg readAgg = OsMetricAgg.builder()
                    .instanceId(instanceId)
                    .collectedAt(collectedAt)
                    .metricType("DISK_READ")
                    .avgValue(readSum / readCount)
                    .maxValue(readMax)
                    .minValue(readMin)
                    .sampleCount(readCount)
                    .build();
            aggList.add(readAgg);
            log.debug("DISK_READ 집계: avg={}, max={}, min={}, count={}", 
                    readAgg.getAvgValue(), readMax, readMin, readCount);
        }
        
        // DISK_WRITE Agg 저장
        if (writeCount > 0) {
            OsMetricAgg writeAgg = OsMetricAgg.builder()
                    .instanceId(instanceId)
                    .collectedAt(collectedAt)
                    .metricType("DISK_WRITE")
                    .avgValue(writeSum / writeCount)
                    .maxValue(writeMax)
                    .minValue(writeMin)
                    .sampleCount(writeCount)
                    .build();
            aggList.add(writeAgg);
            log.debug("DISK_WRITE 집계: avg={}, max={}, min={}, count={}", 
                    writeAgg.getAvgValue(), writeMax, writeMin, writeCount);
        }
    }
    
    /**
     * 일반 메트릭 처리 (CPU, MEMORY 등)
     */
    private void processNormalMetrics(Long instanceId,
                                      String metricType,
                                      List<RedisOsMetricData> typeMetrics,
                                      OffsetDateTime collectedAt,
                                      List<OsMetricAgg> aggList) {
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
    
    /**
     * details에서 디스크 사용률 추출
     */
    private Double extractDiskUsagePercent(Map<String, Object> details) {
        try {
            // usagePercent 필드 직접 확인
            Object usagePercent = details.get("usagePercent");
            if (usagePercent != null) {
                return toDouble(usagePercent);
            }
            
            // filesystem.usagePercent 확인
            Object filesystem = details.get("filesystem");
            if (filesystem instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fsMap = (Map<String, Object>) filesystem;
                Object fsUsage = fsMap.get("usagePercent");
                if (fsUsage != null) {
                    return toDouble(fsUsage);
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("디스크 사용률 추출 실패", e);
            return null;
        }
    }
    
    /**
     * details에서 특정 값 추출
     */
    private Double extractValue(Map<String, Object> details, String key) {
        try {
            Object value = details.get(key);
            return value != null ? toDouble(value) : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Object를 Double로 변환
     */
    private Double toDouble(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 활성 인스턴스 ID 목록 조회
     */
    private List<Long> getActiveInstanceIds() {
        // 임시로 하드코딩 (실제로는 DB 조회)
        return osMetricMapper.selectActiveInstanceIds();
    }
}
