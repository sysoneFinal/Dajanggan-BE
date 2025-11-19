package com.dajanggan.global.crypto.mybatis;

import com.dajanggan.global.crypto.AesGcmService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.*;
import java.sql.*;

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
