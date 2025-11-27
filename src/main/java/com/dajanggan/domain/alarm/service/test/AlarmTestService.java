package com.dajanggan.domain.alarm.service.test;

import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.global.crypto.AesGcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 알람 테스트 공통 로직 처리 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmTestService {

    private final AesGcmService aesGcmService;
    private final MetricConfig metricConfig;
    private final AlarmFeedMapper alarmFeedMapper;

    /**
     * DB 연결 생성
     */
    public Connection createConnection(Instance instance, String databaseName) throws SQLException {
        String host = instance.getHost() != null ? instance.getHost().trim() : "";
        String userName = instance.getUserName() != null ? instance.getUserName().trim() : "";
        String password = aesGcmService.decryptToString(instance.getSecretRef());
        String dbName = databaseName != null ? databaseName.trim() : "";

        String url = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                dbName);

        log.info("DB 연결 시도:");
        log.info("  - URL: {}", url);
        log.info("  - User: {}", userName);
        log.info("  - Password exists: {}", !password.isEmpty());

        try {
            Connection conn = DriverManager.getConnection(url, userName, password);
            log.info("DB 연결 성공");
            return conn;
        } catch (SQLException e) {
            log.error("DB 연결 실패: {}", e.getMessage());
            log.error("  - SQLState: {}", e.getSQLState());
            log.error("  - ErrorCode: {}", e.getErrorCode());
            throw e;
        }
    }

    /**
     * JSON levels 파싱 헬퍼 메서드
     */
    public Map<String, Map<String, Object>> parseJsonLevels(String levelsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(levelsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("JSON 파싱 실패", e);
            return new HashMap<>();
        }
    }

    /**
     * 값 비교 헬퍼 메서드
     */
    public boolean compareValue(BigDecimal current, BigDecimal threshold, String operator) {
        if (current == null || threshold == null || operator == null) return false;

        int cmp = current.compareTo(threshold);
        return switch (operator) {
            case "gt" -> cmp > 0;
            case "gte" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "lte" -> cmp <= 0;
            case "eq" -> cmp == 0;
            default -> false;
        };
    }

    /**
     * 관련 객체 저장 (GenericAlarmCollector와 동일한 로직)
     */
    public void saveRelatedObjects(Connection conn, Long alarmFeedId, Long alarmRuleId, String metricType) {
        String sql = metricConfig.getRelatedObjectsQuery(metricType);
        if (sql == null) {
            log.debug("관련 객체 쿼리 없음: {}", metricType);
            return;
        }

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                alarmFeedMapper.insertRelatedObject(
                        alarmFeedId,
                        alarmRuleId,
                        rs.getString("object_type"),
                        rs.getString("object_name"),
                        rs.getBigDecimal("metric_value"),
                        rs.getString("status")
                );
            }

        } catch (SQLException e) {
            log.error("관련 객체 저장 실패: {}", metricType, e);
        }
    }
}