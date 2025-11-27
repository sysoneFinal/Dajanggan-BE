package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.crypto.AesGcmService;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmFeedService {

    private final AlarmFeedMapper alarmFeedMapper;
    private final AlarmRuleMapper alarmRuleMapper;
    private final MetricConfig metricConfig;
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final AesGcmService aesGcmService;
    private final DataSourceFactory dataSourceFactory;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    // 비동기 처리용 ExecutorService
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(5);
    
    // 비동기 생성 중인 alarmFeedId 추적 (중복 실행 방지)
    private final java.util.Set<Long> generatingAlarmFeedIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    // TransactionTemplate은 PlatformTransactionManager로부터 생성
    private TransactionTemplate getTransactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    /**
     * 알림 목록 조회
     */
    public AlarmFeedDto.ListResponse getAlarmList(
            Long instanceId, Long databaseId, String severityLevel, Boolean isRead
    ) {
        List<AlarmFeedDto.AlarmListRaw> rawList = alarmFeedMapper.selectAlarmList(
                instanceId, databaseId, severityLevel, isRead);

        List<AlarmFeedDto.AlarmItem> alarms = rawList.stream()
                .map(raw -> AlarmFeedDto.AlarmItem.builder()
                        .id(raw.getAlarmFeedId())
                        .title(raw.getAlarmTitle())
                        .severity(raw.getSeverityLevel())
                        .occurredAt(raw.getOccurredAt().format(FORMATTER))
                        .description(raw.getDescription())
                        .isRead(raw.getIsRead())
                        .build())
                .collect(Collectors.toList());

        int unreadCount = (int) alarms.stream()
                .filter(a -> !a.getIsRead())
                .count();

        return AlarmFeedDto.ListResponse.builder()
                .alarms(alarms)
                .totalCount(alarms.size())
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 알림 상세 조회
     */
    public AlarmFeedDto.DetailResponse getAlarmDetail(Long alarmFeedId) {
        // 알림 기본 정보
        AlarmFeed feed = alarmFeedMapper.selectAlarmDetail(alarmFeedId);
        if (feed == null) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }

        // Latency 데이터 (24시간)
        List<AlarmFeedDto.LatencyDataRaw> latencyRaw =
                alarmFeedMapper.selectLatencyData(feed.getAlarmFeedId());

        List<BigDecimal> latencyData = latencyRaw.stream()
                .map(AlarmFeedDto.LatencyDataRaw::getAvgLatency)
                .collect(Collectors.toList());

        List<String> latencyLabels = latencyRaw.stream()
                .map(AlarmFeedDto.LatencyDataRaw::getHourLabel)
                .collect(Collectors.toList());

        AlarmFeedDto.LatencyData latency = AlarmFeedDto.LatencyData.builder()
                .data(latencyData)
                .labels(latencyLabels)
                .build();

        // 요약 정보
        AlarmFeedDto.Summary summary = AlarmFeedDto.Summary.builder()
                .current(formatValue(feed.getCurrentValue()))
                .threshold(formatValue(feed.getThresholdValue()))
                .duration("15m")
                .build();

        // 관련 객체
        List<AlarmFeedDto.RelatedObjectRaw> relatedRaw =
                alarmFeedMapper.selectRelatedObjects(feed.getAlarmFeedId());

        log.info("📋 관련 객체 조회: alarmFeedId={}, 조회된 개수={}", feed.getAlarmFeedId(), relatedRaw != null ? relatedRaw.size() : 0);
        
        // 관련 객체 생성 중 여부 플래그
        // 마커 객체 확인: object_type='_SYSTEM' && object_name='_GENERATION_COMPLETE'이면 이미 생성 완료
        boolean hasGenerationCompleteMarker = relatedRaw != null && relatedRaw.stream()
                .anyMatch(r -> "_SYSTEM".equals(r.getObjectType()) && "_GENERATION_COMPLETE".equals(r.getObjectName()));
        
        // 마커를 제외한 실제 관련 객체만 필터링
        if (relatedRaw != null && hasGenerationCompleteMarker) {
            relatedRaw = relatedRaw.stream()
                    .filter(r -> !("_SYSTEM".equals(r.getObjectType()) && "_GENERATION_COMPLETE".equals(r.getObjectName())))
                    .collect(Collectors.toList());
        }
        
        boolean isGenerating = false;
        
        // 관련 객체가 없고, 생성 완료 마커도 없으면 비동기로 생성 (사용자 대기 없이 즉시 응답)
        if ((relatedRaw == null || relatedRaw.isEmpty()) && !hasGenerationCompleteMarker && feed.getMetricType() != null) {
            // 마커가 없으면 무조건 생성 시작 (마커가 최종 진실의 원천)
            // generatingAlarmFeedIds에 있더라도 마커가 없으면 이전 작업이 실패한 것이므로 재시도
            boolean alreadyGenerating = generatingAlarmFeedIds.contains(feed.getAlarmFeedId());
            
            if (alreadyGenerating) {
                log.warn("⚠️ 이전 생성 작업이 완료되지 않았습니다. 마커가 없으므로 재시도: alarmFeedId={}, metricType={}", 
                        feed.getAlarmFeedId(), feed.getMetricType());
                // 이전 작업이 완료되지 않았으므로 Set에서 제거하고 재시도
                generatingAlarmFeedIds.remove(feed.getAlarmFeedId());
            }
            
            // 마커가 없으면 무조건 생성 시작
            {
                log.info("🔄 관련 객체가 없어서 비동기로 생성 시작: alarmFeedId={}, metricType={}", 
                        feed.getAlarmFeedId(), feed.getMetricType());
                
                isGenerating = true;  // 생성 중 플래그 설정
                generatingAlarmFeedIds.add(feed.getAlarmFeedId());  // 생성 중 표시
                
                // 비동기로 생성 (사용자는 빈 리스트로 즉시 응답받음)
                log.info("🚀 비동기 작업 제출: alarmFeedId={}, thread={}", 
                        feed.getAlarmFeedId(), Thread.currentThread().getName());
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    log.info("🔄 비동기 작업 시작: alarmFeedId={}, thread={}", 
                            feed.getAlarmFeedId(), Thread.currentThread().getName());
                    try {
                        List<AlarmFeedDto.RelatedObjectRaw> generated = generateRelatedObjectsOnDemand(feed);
                        log.info("📊 generateRelatedObjectsOnDemand 완료: alarmFeedId={}, 결과 개수={}", 
                                feed.getAlarmFeedId(), generated != null ? generated.size() : 0);
                        
                        // 생성 완료 마커 저장 (결과가 0개여도 완료 표시)
                        log.info("💾 생성 완료 마커 저장 시작: alarmFeedId={}", feed.getAlarmFeedId());
                        markGenerationComplete(feed.getAlarmFeedId(), feed.getAlarmRuleId());
                        log.info("✅ 생성 완료 마커 저장 완료: alarmFeedId={}", feed.getAlarmFeedId());
                        
                        if (!generated.isEmpty()) {
                            log.info("✅ 관련 객체 비동기 생성 완료: alarmFeedId={}, 생성된 개수={}", 
                                    feed.getAlarmFeedId(), generated.size());
                        } else {
                            log.info("✅ 관련 객체 비동기 생성 완료 (결과 0개): alarmFeedId={}, metricType={}", 
                                    feed.getAlarmFeedId(), feed.getMetricType());
                        }
                    } catch (Exception e) {
                        log.error("❌ 관련 객체 비동기 생성 실패: alarmFeedId={}, error={}", 
                                feed.getAlarmFeedId(), e.getMessage(), e);
                        // 실패해도 완료 마커 저장 (다음 요청에서 생성 중이 아니라고 표시)
                        try {
                            log.info("💾 실패 후 생성 완료 마커 저장 시작: alarmFeedId={}", feed.getAlarmFeedId());
                            markGenerationComplete(feed.getAlarmFeedId(), feed.getAlarmRuleId());
                            log.info("✅ 실패 후 생성 완료 마커 저장 완료: alarmFeedId={}", feed.getAlarmFeedId());
                        } catch (Exception markerException) {
                            log.error("❌ 생성 완료 마커 저장 실패: alarmFeedId={}, error={}", 
                                    feed.getAlarmFeedId(), markerException.getMessage(), markerException);
                        }
                    } finally {
                        // 생성 중 표시 제거
                        generatingAlarmFeedIds.remove(feed.getAlarmFeedId());
                        log.info("🗑️ 생성 중 표시 제거: alarmFeedId={}", feed.getAlarmFeedId());
                        log.info("🏁 비동기 작업 종료: alarmFeedId={}, thread={}", 
                                feed.getAlarmFeedId(), Thread.currentThread().getName());
                    }
                }, asyncExecutor);
                
                // 타임아웃 추가: 10초 내에 완료되지 않으면 "객체 없음"으로 처리
                future.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        if (ex instanceof java.util.concurrent.TimeoutException) {
                            log.warn("⏰ 비동기 작업 타임아웃 (10초 초과): alarmFeedId={}, 객체 없음으로 처리", 
                                    feed.getAlarmFeedId());
                        } else {
                            log.error("❌ 비동기 작업 실행 중 예외 발생: alarmFeedId={}, error={}", 
                                    feed.getAlarmFeedId(), ex.getMessage(), ex);
                        }
                        // 타임아웃/예외 발생해도 완료 마커 저장 (객체 없음으로 처리)
                        try {
                            log.info("💾 타임아웃/예외 후 생성 완료 마커 저장 시작: alarmFeedId={}", feed.getAlarmFeedId());
                            markGenerationComplete(feed.getAlarmFeedId(), feed.getAlarmRuleId());
                            log.info("✅ 타임아웃/예외 후 생성 완료 마커 저장 완료: alarmFeedId={}", feed.getAlarmFeedId());
                        } catch (Exception markerException) {
                            log.error("❌ 생성 완료 마커 저장 실패: alarmFeedId={}, error={}", 
                                    feed.getAlarmFeedId(), markerException.getMessage(), markerException);
                        } finally {
                            // 생성 중 표시 제거
                            generatingAlarmFeedIds.remove(feed.getAlarmFeedId());
                            log.info("🗑️ 타임아웃/예외 후 생성 중 표시 제거: alarmFeedId={}", feed.getAlarmFeedId());
                        }
                        return null;
                    });
            
            // 빈 리스트 반환 (즉시 응답)
            relatedRaw = List.of();
            }  // else 블록 닫기
        }
        
        if (relatedRaw != null && !relatedRaw.isEmpty()) {
            relatedRaw.forEach(raw -> log.info("  - type={}, name={}, metricValue={}, status={}", 
                    raw.getObjectType(), raw.getObjectName(), raw.getMetricValue(), raw.getStatus()));
        }

        String metricType = feed.getMetricType();
        List<AlarmFeedDto.RelatedItem> related = relatedRaw != null ? relatedRaw.stream()
                .map(raw -> {
                    String formattedMetric = formatRelatedMetric(metricType, raw.getMetricValue(), raw.getObjectType());
                    log.debug("관련 객체 포맷팅: metricType={}, rawMetric={}, formatted={}", 
                            metricType, raw.getMetricValue(), formattedMetric);
                    return AlarmFeedDto.RelatedItem.builder()
                            .type(raw.getObjectType())
                            .name(raw.getObjectName())
                            .metric(formattedMetric)
                            .level(raw.getStatus())
                            .build();
                })
                .collect(Collectors.toList()) : List.of();

        log.info("📋 관련 객체 최종 결과: 개수={}", related.size());

        return AlarmFeedDto.DetailResponse.builder()
                .id(feed.getAlarmFeedId())
                .title(feed.getAlarmTitle())
                .severity(feed.getSeverityLevel())
                .occurredAt(feed.getOccurredAt().format(FORMATTER))
                .description(feed.getMessage())
                .latency(latency)
                .summary(summary)
                .related(related)
                .isGenerating(isGenerating)  // 프론트엔드 자동 폴링용 플래그
                .build();
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    public void markAsRead(Long alarmFeedId) {
        int updated = alarmFeedMapper.updateAlarmRead(alarmFeedId);
        if (updated == 0) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 알림 확인 처리
     */
    @Transactional
    public void acknowledgeAlarm(Long alarmFeedId) {
        int updated = alarmFeedMapper.updateAlarmAcknowledged(alarmFeedId);
        if (updated == 0) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 알림 삭제
     * 외래키 제약 조건을 피하기 위해 관련된 데이터를 먼저 삭제
     */
    @Transactional
    public void deleteAlarm(Long alarmFeedId) {
        // 1. 먼저 관련된 메트릭 히스토리 삭제
        alarmFeedMapper.deleteMetricHistoryByFeedId(alarmFeedId);
        
        // 2. 관련된 관련 객체 삭제
        alarmFeedMapper.deleteRelatedObjectsByFeedId(alarmFeedId);
        
        // 3. 알람 피드 삭제
        int deleted = alarmFeedMapper.deleteAlarm(alarmFeedId);
        if (deleted == 0) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 미확인 알림 개수
     */
    public int getUnreadCount(Long instanceId) {
        return alarmFeedMapper.countUnreadAlarms(instanceId);
    }

    /**
     * 값 포맷팅 (K, M 단위)
     */
    private String formatValue(BigDecimal value) {
        if (value == null) return "0";

        double val = value.doubleValue();
        if (val >= 1_000_000) {
            return String.format("%.1fM", val / 1_000_000);
        } else if (val >= 1_000) {
            return String.format("%.1fK", val / 1_000);
        }
        return String.valueOf(val);
    }

    /**
     * 관련 객체의 지표값 포맷팅 (지표 타입별)
     * 이미지 예시: "Dead 780K", "Dead 1.2M", "Dead 450K"
     */
    private String formatRelatedMetric(String metricType, String metricValue, String objectType) {
        if (metricValue == null || metricValue.isEmpty()) {
            return "N/A";
        }

        try {
            double value = Double.parseDouble(metricValue);

            return switch (metricType) {
                case "dead_tuples" -> {
                    // "Dead 780K" 형식
                    String formatted = formatNumber(value);
                    yield "Dead " + formatted;
                }
                case "bloat_size" -> {
                    // 바이트를 GB/MB/KB로 변환
                    if (value >= 1_073_741_824) { // 1GB
                        yield String.format("%.2fGB", value / 1_073_741_824);
                    } else if (value >= 1_048_576) { // 1MB
                        yield String.format("%.2fMB", value / 1_048_576);
                    } else if (value >= 1024) { // 1KB
                        yield String.format("%.2fKB", value / 1024);
                    } else {
                        yield String.format("%.0fB", value);
                    }
                }
                case "unused_indexes" -> {
                    // "Scans: 5" 형식
                    yield String.format("Scans: %s", formatNumber(value));
                }
                case "long_running_queries" -> {
                    // "Runtime: 1.2h" 형식 (초 단위)
                    if (value >= 3600) {
                        yield String.format("Runtime: %.1fh", value / 3600);
                    } else if (value >= 60) {
                        yield String.format("Runtime: %.1fm", value / 60);
                    } else {
                        yield String.format("Runtime: %.0fs", value);
                    }
                }
                case "sequential_scans" -> {
                    // "Scans: 1.2K" 형식
                    yield "Scans: " + formatNumber(value);
                }
                default -> {
                    // 기본 포맷팅
                    yield formatNumber(value);
                }
            };
        } catch (NumberFormatException e) {
            // 숫자가 아니면 그대로 반환
            return metricValue;
        }
    }

    /**
     * 숫자를 K, M 단위로 포맷팅
     */
    private String formatNumber(double value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000);
        } else if (value == (long) value) {
            return String.valueOf((long) value);
        } else {
            return String.format("%.2f", value);
        }
    }

    /**
     * 관련 객체가 없을 때 동적으로 생성
     * Connection Pool을 사용하여 성능 최적화
     */
    private List<AlarmFeedDto.RelatedObjectRaw> generateRelatedObjectsOnDemand(AlarmFeed feed) {
        String metricType = feed.getMetricType();
        if (metricType == null) {
            return List.of();
        }

        // 알람 규칙 조회하여 임계치 가져오기
        AlarmRuleDto.Levels levels = null;
        if (feed.getAlarmRuleId() != null) {
            try {
                AlarmRule rule = alarmRuleMapper.selectRuleDetail(feed.getAlarmRuleId());
                if (rule != null && rule.getLevels() != null) {
                    levels = objectMapper.readValue(rule.getLevels(), AlarmRuleDto.Levels.class);
                    log.info("📋 알람 규칙 임계치 조회 완료: alarmRuleId={}, metricType={}", 
                            feed.getAlarmRuleId(), metricType);
                }
            } catch (Exception e) {
                log.warn("⚠️ 알람 규칙 임계치 조회 실패: alarmRuleId={}, error={}", 
                        feed.getAlarmRuleId(), e.getMessage());
            }
        }
        
        // 임계치를 사용하여 동적 쿼리 생성
        String sql = metricConfig.getRelatedObjectsQuery(metricType, levels);
        if (sql == null) {
            log.warn("⚠️ 관련 객체 쿼리 없음: metricType={}", metricType);
            return List.of();
        }

        try {
            // 인스턴스와 데이터베이스 정보 조회
            Instance instance = instanceRepository.findAllWithSecrets(List.of(feed.getInstanceId())).stream()
                    .findFirst()
                    .orElse(null);
            
            if (instance == null) {
                log.warn("⚠️ 인스턴스를 찾을 수 없음: instanceId={}", feed.getInstanceId());
                return List.of();
            }

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(feed.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(feed.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElse(null);

            if (databaseName == null) {
                log.warn("⚠️ 데이터베이스를 찾을 수 없음: databaseId={}", feed.getDatabaseId());
                return List.of();
            }

            // 비밀번호 복호화 (1회만 수행)
            String decryptedPassword = aesGcmService.decryptToString(instance.getSecretRef());
            
            log.info("🔌 관련 객체 생성용 DB 연결 (Connection Pool 사용): instanceId={}, databaseId={}, databaseName={}", 
                    feed.getInstanceId(), feed.getDatabaseId(), databaseName);
            log.info("📝 실행할 쿼리 (metricType={}): {}", metricType, sql);

            // Connection Pool을 사용하여 JdbcTemplate 생성
            log.info("🔧 JdbcTemplate 생성 시작: instanceId={}, databaseName={}", 
                    feed.getInstanceId(), databaseName);
            JdbcTemplate jdbc;
            try {
                jdbc = dataSourceFactory.createJdbcTemplate(
                        instance, databaseName, decryptedPassword);
                log.info("✅ JdbcTemplate 생성 완료");
            } catch (Exception e) {
                log.error("❌ JdbcTemplate 생성 실패: instanceId={}, databaseName={}, error={}", 
                        feed.getInstanceId(), databaseName, e.getMessage(), e);
                throw e;
            }
            
            // 쿼리 타임아웃 설정 (30초로 증가 - 무거운 쿼리 대응)
            log.info("⏱️ 쿼리 타임아웃 설정 시작: 30초");
            try {
                jdbc.setQueryTimeout(30);
                log.info("✅ 타임아웃 설정 완료");
            } catch (Exception e) {
                log.error("❌ 타임아웃 설정 실패: error={}", e.getMessage(), e);
                throw e;
            }
            
            log.info("🚀 쿼리 실행 시작: alarmFeedId={}, metricType={}, timeout={}초", 
                    feed.getAlarmFeedId(), metricType, 30);
            
            long startTime = System.currentTimeMillis();

            // RowMapper를 사용하여 결과 매핑
            RowMapper<AlarmFeedDto.RelatedObjectRaw> rowMapper = (rs, rowNum) -> {
                AlarmFeedDto.RelatedObjectRaw raw = new AlarmFeedDto.RelatedObjectRaw();
                raw.setObjectType(rs.getString("object_type"));
                raw.setObjectName(rs.getString("object_name"));
                raw.setMetricValue(rs.getString("metric_value"));
                raw.setStatus(rs.getString("status"));
                return raw;
            };

            // 쿼리 실행
            List<AlarmFeedDto.RelatedObjectRaw> generated;
            try {
                generated = jdbc.query(sql, rowMapper);
                long elapsedTime = System.currentTimeMillis() - startTime;
                log.info("⏱️ 쿼리 실행 시간: {}ms", elapsedTime);
            } catch (Exception queryException) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                log.error("❌ 쿼리 실행 중 예외 발생: alarmFeedId={}, metricType={}, elapsedTime={}ms, error={}", 
                        feed.getAlarmFeedId(), metricType, elapsedTime, queryException.getMessage(), queryException);
                throw queryException;
            }
            
            log.info("📊 쿼리 실행 완료: alarmFeedId={}, 결과 개수={}", feed.getAlarmFeedId(), generated.size());

            if (generated.isEmpty()) {
                log.warn("⚠️ 관련 객체 쿼리 결과가 0개입니다: alarmFeedId={}, metricType={}", 
                        feed.getAlarmFeedId(), metricType);
                log.info("ℹ️ 참고: metricType={} 쿼리는 조건에 맞는 데이터가 없을 때 0개를 반환할 수 있습니다.", metricType);
                log.info("ℹ️ wraparound_progress의 경우, age(relfrozenxid)가 autovacuum_freeze_max_age * 0.1보다 작은 테이블은 제외됩니다.");
            } else {
                log.info("✅ 관련 객체 동적 생성 완료: alarmFeedId={}, 생성된 개수={}", 
                        feed.getAlarmFeedId(), generated.size());
                // 첫 번째 결과 샘플 로깅
                AlarmFeedDto.RelatedObjectRaw sample = generated.get(0);
                log.info("📋 샘플 데이터: type={}, name={}, metricValue={}, status={}", 
                        sample.getObjectType(), sample.getObjectName(), 
                        sample.getMetricValue(), sample.getStatus());
            }

            // 생성된 객체를 DB에 저장 (선택사항)
            if (!generated.isEmpty()) {
                saveRelatedObjectsToDb(feed.getAlarmFeedId(), feed.getAlarmRuleId(), generated);
            }
            
            // 생성 완료 마커 저장 (결과가 0개여도 완료 표시)
            markGenerationComplete(feed.getAlarmFeedId(), feed.getAlarmRuleId());

            return generated;

        } catch (Exception e) {
            log.error("❌ 관련 객체 동적 생성 실패: alarmFeedId={}, metricType={}, error={}", 
                    feed.getAlarmFeedId(), metricType, e.getMessage(), e);
            log.error("❌ 스택 트레이스:", e);
            return List.of();
        }
    }

    /**
     * 관련 객체 생성 완료 마커 저장
     * 비동기 생성이 완료되었음을 표시하기 위한 마커를 DB에 저장
     * ⚠️ 비동기 스레드에서 호출되므로 TransactionTemplate을 사용하여 트랜잭션 관리
     */
    protected void markGenerationComplete(Long alarmFeedId, Long alarmRuleId) {
        try {
            // TransactionTemplate을 사용하여 비동기 스레드에서도 트랜잭션 보장
            getTransactionTemplate().execute(status -> {
                try {
                    // 이미 마커가 있는지 확인 (중복 방지)
                    List<AlarmFeedDto.RelatedObjectRaw> existing = alarmFeedMapper.selectRelatedObjects(alarmFeedId);
                    boolean hasMarker = existing != null && existing.stream()
                            .anyMatch(r -> "_SYSTEM".equals(r.getObjectType()) && "_GENERATION_COMPLETE".equals(r.getObjectName()));
                    
                    if (!hasMarker) {
                        alarmFeedMapper.insertRelatedObject(
                                alarmFeedId,
                                alarmRuleId,
                                "_SYSTEM",  // 마커 타입
                                "_GENERATION_COMPLETE",  // 마커 이름
                                null,  // metricValue는 null
                                "완료"  // status
                        );
                        log.info("✅ 생성 완료 마커 저장: alarmFeedId={}", alarmFeedId);
                    }
                    return null;
                } catch (Exception e) {
                    log.error("❌ 생성 완료 마커 저장 중 오류 발생: alarmFeedId={}", alarmFeedId, e);
                    status.setRollbackOnly();
                    throw e;
                }
            });
        } catch (Exception e) {
            log.error("❌ 생성 완료 마커 저장 실패: alarmFeedId={}", alarmFeedId, e);
        }
    }

    /**
     * 동적으로 생성된 관련 객체를 DB에 저장
     * ⚠️ 비동기 스레드에서 호출되므로 TransactionTemplate을 사용하여 트랜잭션 관리
     */
    protected void saveRelatedObjectsToDb(Long alarmFeedId, Long alarmRuleId,
                                          List<AlarmFeedDto.RelatedObjectRaw> relatedObjects) {
        try {
            // TransactionTemplate을 사용하여 비동기 스레드에서도 트랜잭션 보장
            getTransactionTemplate().execute(status -> {
                try {
                    for (AlarmFeedDto.RelatedObjectRaw raw : relatedObjects) {
                        alarmFeedMapper.insertRelatedObject(
                                alarmFeedId,
                                alarmRuleId,
                                raw.getObjectType(),
                                raw.getObjectName(),
                                raw.getMetricValue() != null ? new BigDecimal(raw.getMetricValue()) : null,
                                raw.getStatus()
                        );
                    }
                    log.info("💾 동적으로 생성된 관련 객체 저장 완료: alarmFeedId={}, 개수={}", 
                            alarmFeedId, relatedObjects.size());
                    return null;
                } catch (Exception e) {
                    log.error("❌ 관련 객체 저장 중 오류 발생: alarmFeedId={}", alarmFeedId, e);
                    status.setRollbackOnly(); // 롤백 표시
                    throw e;
                }
            });
        } catch (Exception e) {
            log.error("❌ 관련 객체 저장 실패: alarmFeedId={}", alarmFeedId, e);
        }
    }
}
