package com.dajanggan.global.crypto.mybatis;

import com.dajanggan.global.crypto.AesGcmService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.*;
import java.sql.*;


/**
 * MyBatis 암호화 TypeHandler
 *
 * 주요 책임:
 * - MyBatis에서 String 타입의 자동 암호화/복호화
 * - DB INSERT/UPDATE 시 자동 암호화
 * - DB SELECT 시 자동 복호화
 *
 * 동작 방식:
 * - setNonNullParameter: DB 저장 전 암호화
 * - getNullableResult: DB 조회 후 복호화
 * - 암호화 서비스가 초기화되지 않으면 SQLException 발생
 *
 * 보안 고려사항:
 * - 복호화 실패 시 상세 에러 로그 (디버깅용)
 * - 민감 정보는 로그에 일부만 출력 (최대 50자)
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-11  김민서    1. 최초작성
 */

@Slf4j
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(String.class)
public class SecretStringTypeHandler extends BaseTypeHandler<String> {

    private static volatile AesGcmService crypto;

    public SecretStringTypeHandler() { }

    // 부팅 시 Config에서 호출
    public static void setCrypto(AesGcmService svc) { 
        crypto = svc;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        if (crypto == null) {
            log.error("AesGcmService not initialized during setNonNullParameter");
            throw new SQLException("AesGcmService not initialized");
        }
        try {
            String encrypted = crypto.encryptString(parameter);
            ps.setString(i, encrypted);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage(), e);
            throw new SQLException("Encryption failed", e);
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String v = rs.getString(columnName);
        if (v == null) return null;
        if (crypto == null) {
            throw new SQLException("AesGcmService not initialized");
        }
        try {
            String decrypted = crypto.decryptToString(v);
            return decrypted;
        } catch (Exception e) {
            log.error("Decryption failed for column '{}': {} - Data: {}",
                     columnName, e.getMessage(), v.length() > 50 ? v.substring(0, 50) + "..." : v);
            throw new SQLException("Decryption failed for column: " + columnName, e);
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String v = rs.getString(columnIndex);
        if (v == null) return null;
        if (crypto == null) {
            log.error("AesGcmService not initialized during getNullableResult");
            throw new SQLException("AesGcmService not initialized");
        }
        try {
            String decrypted = crypto.decryptToString(v);
            return decrypted;
        } catch (Exception e) {
            log.error("Decryption failed for column index {}: {} - Data: {}", 
                     columnIndex, e.getMessage(), v.length() > 50 ? v.substring(0, 50) + "..." : v);
            throw new SQLException("Decryption failed for column index: " + columnIndex, e);
        }
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String v = cs.getString(columnIndex);
        if (v == null) return null;
        if (crypto == null) {
            throw new SQLException("AesGcmService not initialized");
        }
        try {
            String decrypted = crypto.decryptToString(v);
            return decrypted;
        } catch (Exception e) {
            log.error("Decryption failed for callable statement column {}: {}", 
                     columnIndex, e.getMessage());
            throw new SQLException("Decryption failed for callable statement column: " + columnIndex, e);
        }
    }
}
