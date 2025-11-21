package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.crypto.AesGcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmFeedService {

    private final AlarmFeedMapper alarmFeedMapper;
    private final MetricConfig metricConfig;
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final AesGcmService aesGcmService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
        
        // 관련 객체가 없으면 동적으로 생성 시도
        if ((relatedRaw == null || relatedRaw.isEmpty()) && feed.getMetricType() != null) {
            log.info("🔄 관련 객체가 없어서 동적으로 생성 시도: alarmFeedId={}, metricType={}", 
                    feed.getAlarmFeedId(), feed.getMetricType());
            relatedRaw = generateRelatedObjectsOnDemand(feed);
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
     */
    private List<AlarmFeedDto.RelatedObjectRaw> generateRelatedObjectsOnDemand(AlarmFeed feed) {
        String metricType = feed.getMetricType();
        if (metricType == null) {
            return List.of();
        }

        String sql = metricConfig.getRelatedObjectsQuery(metricType);
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

            // DB 연결 생성 및 쿼리 실행
            String host = instance.getHost() != null ? instance.getHost().trim() : "";
            String userName = instance.getUserName() != null ? instance.getUserName().trim() : "";
            String password = aesGcmService.decryptToString(instance.getSecretRef());
            String url = String.format("jdbc:postgresql://%s:%d/%s",
                    host, instance.getPort(), databaseName);

            log.info("🔌 관련 객체 생성용 DB 연결: instanceId={}, databaseId={}", 
                    feed.getInstanceId(), feed.getDatabaseId());

            try (Connection conn = DriverManager.getConnection(url, userName, password);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                List<AlarmFeedDto.RelatedObjectRaw> generated = new java.util.ArrayList<>();
                while (rs.next()) {
                    AlarmFeedDto.RelatedObjectRaw raw = new AlarmFeedDto.RelatedObjectRaw();
                    raw.setObjectType(rs.getString("object_type"));
                    raw.setObjectName(rs.getString("object_name"));
                    raw.setMetricValue(rs.getString("metric_value"));
                    raw.setStatus(rs.getString("status"));
                    generated.add(raw);
                }

                log.info("✅ 관련 객체 동적 생성 완료: alarmFeedId={}, 생성된 개수={}", 
                        feed.getAlarmFeedId(), generated.size());

                // 생성된 객체를 DB에 저장 (선택사항)
                if (!generated.isEmpty()) {
                    saveRelatedObjectsToDb(feed.getAlarmFeedId(), feed.getAlarmRuleId(), generated);
                }

                return generated;
            }

        } catch (SQLException e) {
            log.error("❌ 관련 객체 동적 생성 실패: alarmFeedId={}, metricType={}", 
                    feed.getAlarmFeedId(), metricType, e);
            return List.of();
        } catch (Exception e) {
            log.error("❌ 관련 객체 동적 생성 중 예외 발생: alarmFeedId={}, metricType={}", 
                    feed.getAlarmFeedId(), metricType, e);
            return List.of();
        }
    }

    /**
     * 동적으로 생성된 관련 객체를 DB에 저장
     */
    @Transactional
    private void saveRelatedObjectsToDb(Long alarmFeedId, Long alarmRuleId, 
                                       List<AlarmFeedDto.RelatedObjectRaw> relatedObjects) {
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
        } catch (Exception e) {
            log.error("❌ 관련 객체 저장 실패: alarmFeedId={}", alarmFeedId, e);
        }
    }
}
