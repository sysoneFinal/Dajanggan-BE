package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.crypto.AesGcmService;
import com.dajanggan.global.exception.NotFoundException;
import com.dajanggan.global.exception.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL 연결 및 데이터베이스 조회 서비스
 *
 * 주요 책임:
 * 
 *   PostgreSQL 서버 연결 테스트
 *   데이터베이스 목록 조회
 *   서버 버전 정보 조회
 * 
 *
 * DTO 반환 원칙:
 * 
 *   Entity를 받지 않고 primitive 타입만 파라미터로 받음
 *   Map 또는 List 같은 기본 타입 반환
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-13  김민서    1. 최초작성자
 *
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PostgresConnectionService {

    private final InstanceRepository instanceRepository;
    private final AesGcmService aesGcmService;

    /** 기본 PostgreSQL 관리 데이터베이스명 */
    private static final String DEFAULT_DATABASE = "postgres";

    /** 템플릿 데이터베이스 제외 목록 */
    private static final String[] EXCLUDED_DATABASES = {"template0", "template1"};

    /** 기본 SSL 모드 */
    private static final String DEFAULT_SSL_MODE = "disable";

    /**
     * PostgreSQL 연결 테스트
     *
     * @param host 호스트 주소
     * @param port 포트 번호
     * @param username 사용자명
     * @param password 비밀번호 (평문)
     * @param sslMode SSL 모드 (null인 경우 disable 사용)
     * @return 연결 테스트 결과 (success, message, version, errorCode 포함)
     */
    public Map<String, Object> testConnection(String host, Integer port, String username,
                                              String password, String sslMode) {
        Map<String, Object> result = new HashMap<>();

        // SSL 모드 기본값 처리
        String effectiveSslMode = getEffectiveSslMode(sslMode);
        String jdbcUrl = buildJdbcUrl(host, port, DEFAULT_DATABASE, effectiveSslMode);

        log.info("PostgreSQL 연결 테스트 시작: {} (sslMode: {})", jdbcUrl, effectiveSslMode);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {

            // 버전 정보 조회
            String version = getServerVersion(conn);
            log.info("연결 성공! PostgreSQL 버전: {}", version);

            result.put("success", true);
            result.put("message", "연결 성공!");
            result.put("version", version);

        } catch (SQLException e) {
            log.error("PostgreSQL 연결 실패: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "연결 실패: " + e.getMessage());
            result.put("errorCode", e.getSQLState());
        }

        return result;
    }

    /**
     * PostgreSQL 서버의 데이터베이스 목록 조회
     *
     * 시스템 템플릿 데이터베이스(template0, template1)는 제외됨
     *
     * @param host 호스트 주소
     * @param port 포트 번호
     * @param username 사용자명
     * @param encryptedPassword 암호화된 비밀번호 (복호화된 상태로 전달받음)
     * @param sslMode SSL 모드
     * @return 데이터베이스 이름 목록
     * @throws RuntimeException PostgreSQL 연결 또는 조회 실패 시
     */
    public List<String> fetchDatabaseNames(String host, Integer port, String username,
                                           String encryptedPassword, String sslMode) {
        String effectiveSslMode = getEffectiveSslMode(sslMode);
        String jdbcUrl = buildJdbcUrl(host, port, DEFAULT_DATABASE, effectiveSslMode);

        log.info("데이터베이스 목록 조회 시작: {} (sslMode: {})", jdbcUrl, effectiveSslMode);

        // 템플릿 데이터베이스를 제외한 모든 사용자 데이터베이스 조회
        String query = """
            SELECT datname 
            FROM pg_database 
            WHERE datistemplate = false
              AND datname NOT IN ('template0', 'template1')
            ORDER BY datname
            """;

        List<String> databaseNames = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, encryptedPassword)) {
            log.info("PostgreSQL 연결 성공");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    String dbName = rs.getString("datname");
                    databaseNames.add(dbName);
                    log.debug("데이터베이스 발견: {}", dbName);
                }
            }

            log.info("총 {}개 데이터베이스 조회 완료", databaseNames.size());

        } catch (SQLException e) {
            log.error("PostgreSQL 연결/조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("데이터베이스 목록 조회 실패: " + e.getMessage(), e);
        }

        return databaseNames;
    }

    /**
     * PostgreSQL 서버 버전 조회
     *
     * @param conn 데이터베이스 연결
     * @return 서버 버전 문자열
     * @throws SQLException 쿼리 실행 실패 시
     */
    private String getServerVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version()")) {

            if (rs.next()) {
                return rs.getString(1);
            }
            return "Unknown";
        }
    }

    /**
     * 유효한 SSL 모드 반환
     *
     * null이거나 빈 문자열인 경우 기본값(disable) 반환
     *
     * @param sslMode 입력 SSL 모드
     * @return 유효한 SSL 모드
     */
    private String getEffectiveSslMode(String sslMode) {
        return (sslMode != null && !sslMode.trim().isEmpty())
                ? sslMode.trim()
                : DEFAULT_SSL_MODE;
    }

    /**
     * JDBC URL 생성
     *
     * @param host 호스트 주소
     * @param port 포트 번호
     * @param database 데이터베이스명
     * @param sslMode SSL 모드
     * @return JDBC URL 문자열
     */
    private String buildJdbcUrl(String host, Integer port, String database, String sslMode) {
        return String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                host, port, database, sslMode);
    }

    /**
     * PostgreSQL 연결 생성
     *
     * @param instanceId 인스턴스 ID
     * @param databaseName 데이터베이스명
     * @return PostgreSQL 연결
     * @throws SQLException 연결 실패 시
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     */
    public Connection createConnection(Long instanceId, String databaseName) throws SQLException {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));

        String host = instance.getHost() != null ? instance.getHost().trim() : "";
        String userName = instance.getUserName() != null ? instance.getUserName().trim() : "";
        String password = aesGcmService.decryptToString(instance.getSecretRef());
        String dbName = databaseName != null ? databaseName.trim() : "";

        String url = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                dbName);

        log.debug("DB 연결 시도: {}:{}/{}", host, instance.getPort(), dbName);

        try {
            Connection conn = DriverManager.getConnection(url, userName, password);
            log.debug("DB 연결 성공: {}:{}/{}", host, instance.getPort(), dbName);
            return conn;
        } catch (SQLException e) {
            log.error("DB 연결 실패: {}:{}/{} - {}", host, instance.getPort(), dbName, e.getMessage());
            throw e;
        }
    }
}