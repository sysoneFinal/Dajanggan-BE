// 작성자 : 김동현
package com.dajanggan.domain.osmetric.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.dto.SseOsMetricResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * OS Metric SSE 서비스
 * - Redis의 실시간 데이터를 SSE로 프론트엔드에 전송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsMetricSseService {

    private final OsMetricRedisService redisService;

    // instanceId별 SSE Emitter 목록 관리
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private static final Long SSE_TIMEOUT = 60 * 60 * 1000L; // 1시간

    /**
     * SSE 연결 생성
     */
    public SseEmitter createEmitter(Long instanceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Emitter 목록에 추가
        emitters.computeIfAbsent(instanceId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        log.info("SSE 연결 생성: instanceId={}, total emitters={}",
                instanceId, emitters.get(instanceId).size());

        // 연결 종료 시 목록에서 제거
        emitter.onCompletion(() -> {
            emitters.get(instanceId).remove(emitter);
            log.info("SSE 연결 완료: instanceId={}", instanceId);
        });

        emitter.onTimeout(() -> {
            emitters.get(instanceId).remove(emitter);
            log.info("SSE 연결 타임아웃: instanceId={}", instanceId);
        });

        emitter.onError(e -> {
            emitters.get(instanceId).remove(emitter);
            log.error("SSE 연결 오류: instanceId={}", instanceId, e);
        });

        // 초기 연결 확인 메시지 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE Connected - Instance " + instanceId));
        } catch (IOException e) {
            log.error("SSE 초기 메시지 전송 실패", e);
        }

        return emitter;
    }

    /**
     * 특정 인스턴스의 실시간 메트릭 데이터를 모든 연결된 클라이언트에게 전송
     */
    public void broadcastMetrics(Long instanceId) {
        List<SseEmitter> instanceEmitters = emitters.get(instanceId);

        if (instanceEmitters == null || instanceEmitters.isEmpty()) {
            return;
        }

        try {
            // Redis에서 배치로 최신 메트릭 데이터 조회 (최적화: 3번 조회 → 1번 조회)
            Map<String, RedisOsMetricData> metricsMap = redisService.getLatestMetricsBatch(instanceId);
            
            RedisOsMetricData cpuData = metricsMap.get("CPU");
            RedisOsMetricData memoryData = metricsMap.get("MEMORY");
            RedisOsMetricData diskData = metricsMap.get("DISK");
            
            // 디버깅: Redis에서 가져온 데이터 확인
            if (diskData != null) {
                log.debug("Redis Disk 데이터 존재: instanceId={}, collectedAt={}, details={}", 
                        instanceId, diskData.getCollectedAt(), diskData.getDetails());
            } else {
                log.debug("Redis Disk 데이터 없음: instanceId={}", instanceId);
            }

            if (cpuData == null && memoryData == null && diskData == null) {
                log.debug("전송할 메트릭 데이터 없음: instanceId={}", instanceId);
                return;
            }

            // SSE 응답 생성 (프론트엔드 형식에 맞춤)
            SseOsMetricResponse response = SseOsMetricResponse.builder()
                    .instanceId(instanceId)
                    .collectedAt(LocalDateTime.now())
                    .eventType("metrics")
                    .build();

            // CPU 데이터 처리
            if (cpuData != null && cpuData.getDetails() != null) {
                Map<String, Object> cpuDetails = cpuData.getDetails();

                // CPU 사용률
                Object totalUsage = cpuDetails.get("totalUsage");
                if (totalUsage != null) {
                    response.setCpu(toDouble(totalUsage));
                }

                // Load Average 추출
                Object loadAvgObj = cpuDetails.get("loadAverage");
                if (loadAvgObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> loadAvgMap = (Map<String, Object>) loadAvgObj;

                    // Redis에 저장된 형식: {"1m":0.01,"5m":0.1,"15m":0.07}
                    List<Double> loadAverage = List.of(
                            toDouble(loadAvgMap.get("1m")),    // 1분
                            toDouble(loadAvgMap.get("5m")),    // 5분
                            toDouble(loadAvgMap.get("15m"))    // 15분
                    );
                    response.setLoadAverage(loadAverage);
                }
            }

            // Memory 데이터 처리
            if (memoryData != null && memoryData.getDetails() != null) {
                Map<String, Object> memoryDetails = memoryData.getDetails();
                Object memUsage = memoryDetails.get("usagePercent");
                if (memUsage != null) {
                    response.setMemory(toDouble(memUsage));
                }
                
                // Memory 상세 정보 (total, used, available, cache) - GB 단위로 변환
                Long memoryTotal = getLong(memoryDetails.get("total"));
                Long memoryUsed = getLong(memoryDetails.get("used"));
                Long memoryAvailable = getLong(memoryDetails.get("available"));
                Long memoryCache = getLong(memoryDetails.get("cache"));
                
                if (memoryTotal != null && memoryTotal > 0) {
                    // 바이트를 GB로 변환 (1024^3)
                    double gbDivisor = 1024.0 * 1024.0 * 1024.0;
                    response.setMemoryTotalGB(memoryTotal / gbDivisor);
                    response.setMemoryUsedGB(memoryUsed != null ? memoryUsed / gbDivisor : 0.0);
                    response.setMemoryAvailableGB(memoryAvailable != null ? memoryAvailable / gbDivisor : 0.0);
                    response.setMemoryCacheGB(memoryCache != null ? memoryCache / gbDivisor : 0.0);
                }
                
                // Swap 데이터 처리
                Object swapObj = memoryDetails.get("swap");
                
                // 디버깅: Memory details 전체 구조 확인
                log.info("Memory details 전체 구조: instanceId={}, memoryDetails={}", instanceId, memoryDetails);
                log.info("Swap 객체 확인: instanceId={}, swapObj={}, swapObj type={}", 
                        instanceId, swapObj, swapObj != null ? swapObj.getClass().getName() : "null");
                
                if (swapObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> swapMap = (Map<String, Object>) swapObj;
                    
                    log.info("Swap Map 확인: instanceId={}, swapMap={}, swapMap keys={}", 
                            instanceId, swapMap, swapMap.keySet());
                    
                    Long swapTotal = getLong(swapMap.get("total"));
                    Long swapUsed = getLong(swapMap.get("used"));
                    Long swapIn = getLong(swapMap.get("swapIn"));
                    Long swapOut = getLong(swapMap.get("swapOut"));
                    
                    // swapIn, swapOut이 없으면 다른 키 이름으로 시도
                    if (swapIn == null) {
                        swapIn = getLong(swapMap.get("swapInPerSec"));
                    }
                    if (swapOut == null) {
                        swapOut = getLong(swapMap.get("swapOutPerSec"));
                    }
                    if (swapIn == null) {
                        swapIn = getLong(swapMap.get("in"));
                    }
                    if (swapOut == null) {
                        swapOut = getLong(swapMap.get("out"));
                    }
                    
                    log.info("Swap 데이터 추출: instanceId={}, swapTotal={}, swapUsed={}, swapIn={}, swapOut={}", 
                            instanceId, swapTotal, swapUsed, swapIn, swapOut);
                    
                    if (swapTotal != null && swapTotal > 0) {
                        // GB 단위로 변환
                        response.setSwapTotalGB(swapTotal / (1024.0 * 1024.0 * 1024.0));
                        response.setSwapUsedGB(swapUsed != null ? swapUsed / (1024.0 * 1024.0 * 1024.0) : 0.0);
                        response.setSwapUsage((swapUsed != null ? swapUsed.doubleValue() : 0.0) / swapTotal * 100.0);
                    } else {
                        // Swap이 없거나 0인 경우에도 기본값 설정
                        response.setSwapTotalGB(0.0);
                        response.setSwapUsedGB(0.0);
                        response.setSwapUsage(0.0);
                    }
                    
                    if (swapIn != null) {
                        response.setSwapInPerSec(swapIn);
                    } else {
                        response.setSwapInPerSec(0L);
                    }
                    if (swapOut != null) {
                        response.setSwapOutPerSec(swapOut);
                    } else {
                        response.setSwapOutPerSec(0L);
                    }
                } else {
                    // Swap 객체가 없는 경우 - memoryDetails에서 직접 확인
                    log.warn("Swap 객체가 Map이 아님: instanceId={}, swapObj={}, swapObj type={}", 
                            instanceId, swapObj, swapObj != null ? swapObj.getClass().getName() : "null");
                    
                    // memoryDetails에서 직접 swap 관련 필드 확인
                    Long swapTotal = getLong(memoryDetails.get("swapTotal"));
                    Long swapUsed = getLong(memoryDetails.get("swapUsed"));
                    Long swapIn = getLong(memoryDetails.get("swapIn"));
                    Long swapOut = getLong(memoryDetails.get("swapOut"));
                    
                    if (swapTotal == null) {
                        swapTotal = getLong(memoryDetails.get("swap_total"));
                    }
                    if (swapUsed == null) {
                        swapUsed = getLong(memoryDetails.get("swap_used"));
                    }
                    if (swapIn == null) {
                        swapIn = getLong(memoryDetails.get("swap_in"));
                    }
                    if (swapOut == null) {
                        swapOut = getLong(memoryDetails.get("swap_out"));
                    }
                    
                    log.info("Memory details에서 직접 Swap 데이터 추출: instanceId={}, swapTotal={}, swapUsed={}, swapIn={}, swapOut={}", 
                            instanceId, swapTotal, swapUsed, swapIn, swapOut);
                    
                    if (swapTotal != null && swapTotal > 0) {
                        response.setSwapTotalGB(swapTotal / (1024.0 * 1024.0 * 1024.0));
                        response.setSwapUsedGB(swapUsed != null ? swapUsed / (1024.0 * 1024.0 * 1024.0) : 0.0);
                        response.setSwapUsage((swapUsed != null ? swapUsed.doubleValue() : 0.0) / swapTotal * 100.0);
                    } else {
                        response.setSwapTotalGB(0.0);
                        response.setSwapUsedGB(0.0);
                        response.setSwapUsage(0.0);
                    }
                    
                    if (swapIn != null) {
                        response.setSwapInPerSec(swapIn);
                    } else {
                        response.setSwapInPerSec(0L);
                    }
                    if (swapOut != null) {
                        response.setSwapOutPerSec(swapOut);
                    } else {
                        response.setSwapOutPerSec(0L);
                    }
                }
                
                // 디버깅: Memory 상세 정보 로깅
                log.debug("Memory SSE 데이터: instanceId={}, usagePercent={}, totalGB={}, usedGB={}, availableGB={}, cacheGB={}, swapUsage={}", 
                        instanceId, response.getMemory(), response.getMemoryTotalGB(), 
                        response.getMemoryUsedGB(), response.getMemoryAvailableGB(), response.getMemoryCacheGB(), response.getSwapUsage());
            }

            // Disk 데이터 처리
            if (diskData != null && diskData.getDetails() != null) {
                Map<String, Object> diskDetails = diskData.getDetails();
                log.debug("Redis에서 가져온 Disk 데이터: instanceId={}, details={}", instanceId, diskDetails);

                // Disk 사용률 및 파일시스템 정보
                Object filesystem = diskDetails.get("filesystem");
                Map<String, Object> fsMap = null;
                if (filesystem instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tempFsMap = (Map<String, Object>) filesystem;
                    fsMap = tempFsMap;
                    Object fsUsage = fsMap.get("usagePercent");
                    if (fsUsage != null) {
                        response.setDiskUsage(toDouble(fsUsage));
                    }
                    
                    // Disk 총량, 사용량, 사용 가능량 (바이트를 GB로 변환)
                    Object total = fsMap.get("total");
                    Object used = fsMap.get("used");
                    Object available = fsMap.get("available");
                    
                    if (total != null) {
                        // 바이트를 GB로 변환 (1024^3)
                        response.setDiskTotalGB(toDouble(total) / (1024.0 * 1024.0 * 1024.0));
                    }
                    if (used != null) {
                        response.setDiskUsedGB(toDouble(used) / (1024.0 * 1024.0 * 1024.0));
                    }
                    if (available != null) {
                        response.setDiskAvailableGB(toDouble(available) / (1024.0 * 1024.0 * 1024.0));
                    }
                }
                
                // 디버깅: Disk 상세 정보 로깅 (INFO 레벨로 변경하여 디스크 사용률 고정 문제 확인)
                log.info("Disk SSE 데이터 전송: instanceId={}, usagePercent={}, totalGB={}, usedGB={}, availableGB={}, readMBps={}, writeMBps={}, filesystem={}", 
                        instanceId, response.getDiskUsage(), response.getDiskTotalGB(), 
                        response.getDiskUsedGB(), response.getDiskAvailableGB(), 
                        response.getDiskRead(), response.getDiskWrite(), fsMap);

                // Disk Read/Write 속도
                Object readSpeed = diskDetails.get("readSpeedMBps");
                if (readSpeed != null) {
                    response.setDiskRead(toDouble(readSpeed));
                }

                Object writeSpeed = diskDetails.get("writeSpeedMBps");
                if (writeSpeed != null) {
                    response.setDiskWrite(toDouble(writeSpeed));
                }
            }

            // 모든 연결된 클라이언트에게 전송
            List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

            for (SseEmitter emitter : instanceEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("metrics")
                            .data(response));

                    log.debug("SSE 데이터 전송 성공: instanceId={}, cpu={}, memory={}, swapUsage={}, loadAverage={}",
                            instanceId, response.getCpu(), response.getMemory(), response.getSwapUsage(), response.getLoadAverage());

                } catch (IOException e) {
                    log.warn("SSE 데이터 전송 실패, Emitter 제거 예정: instanceId={}", instanceId);
                    deadEmitters.add(emitter);
                }
            }

            // 전송 실패한 Emitter 제거
            instanceEmitters.removeAll(deadEmitters);

        } catch (Exception e) {
            log.error("SSE 브로드캐스트 중 오류 발생: instanceId={}", instanceId, e);
        }
    }

    /**
     * Object를 Double로 변환
     */
    private Double toDouble(Object obj) {
        if (obj == null) {
            return 0.0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * Object를 Long으로 변환
     */
    private Long getLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 모든 인스턴스의 메트릭 브로드캐스트
     */
    public void broadcastAllInstances() {
        if (emitters.isEmpty()) {
            log.debug("연결된 SSE 클라이언트가 없음 - 브로드캐스트 스킵");
            return;
        }
        
        log.info("전체 인스턴스 SSE 브로드캐스트 시작 - 연결된 인스턴스 수: {}", emitters.size());
        emitters.keySet().forEach(this::broadcastMetrics);
        log.info("전체 인스턴스 SSE 브로드캐스트 완료");
    }

    /**
     * 특정 인스턴스의 모든 SSE 연결 종료
     *
     * @param instanceId 인스턴스 ID
     */
    public void closeAllEmitters(Long instanceId) {
        List<SseEmitter> instanceEmitters = emitters.get(instanceId);

        if (instanceEmitters != null) {
            instanceEmitters.forEach(SseEmitter::complete);
            emitters.remove(instanceId);
            log.info("SSE 연결 모두 종료: instanceId={}", instanceId);
        }
    }
}
