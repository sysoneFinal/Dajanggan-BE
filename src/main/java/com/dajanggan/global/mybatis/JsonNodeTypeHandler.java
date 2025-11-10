package com.dajanggan.global.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.*;

@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(JsonNode.class)
public class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(parameter.toString()); // Json 문자열로 변환
        ps.setObject(i, jsonObject);
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        try {
            return value == null ? null : objectMapper.readTree(value);
        } catch (Exception e) {
            throw new SQLException("JSON 파싱 실패", e);
        }
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        try {
            return value == null ? null : objectMapper.readTree(value);
        } catch (Exception e) {
            throw new SQLException("JSON 파싱 실패", e);
        }
    }

    @Override
    public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        try {
            return value == null ? null : objectMapper.readTree(value);
        } catch (Exception e) {
            throw new SQLException("JSON 파싱 실패", e);
        }
    }
}
