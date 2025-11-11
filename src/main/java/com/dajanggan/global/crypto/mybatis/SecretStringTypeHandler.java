package com.dajanggan.global.crypto.mybatis;

import com.dajanggan.global.crypto.AesGcmService;
import org.apache.ibatis.type.*;
import java.sql.*;

@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(String.class)
public class SecretStringTypeHandler extends BaseTypeHandler<String> {

    private static volatile AesGcmService crypto;

    public SecretStringTypeHandler() { }

    // 부팅 시 Config에서 호출
    public static void setCrypto(AesGcmService svc) { crypto = svc; }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        if (crypto == null) throw new SQLException("AesGcmService not initialized");
        ps.setString(i, crypto.encryptString(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String v = rs.getString(columnName);
        if (v == null) return null;
        if (crypto == null) throw new SQLException("AesGcmService not initialized");
        return crypto.decryptToString(v);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String v = rs.getString(columnIndex);
        if (v == null) return null;
        if (crypto == null) throw new SQLException("AesGcmService not initialized");
        return crypto.decryptToString(v);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String v = cs.getString(columnIndex);
        if (v == null) return null;
        if (crypto == null) throw new SQLException("AesGcmService not initialized");
        return crypto.decryptToString(v);
    }
}
