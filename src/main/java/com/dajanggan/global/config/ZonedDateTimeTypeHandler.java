package com.dajanggan.global.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ZonedDateTime을 PostgreSQL TIMESTAMPTZ로 변환하는 TypeHandler
 * - MyBatis에서 ZonedDateTime 타입을 DB의 TIMESTAMPTZ와 매핑
 * - Java 8 Time API와 PostgreSQL의 시간대 지원 타입 간 변환 처리
 *
 * @author 이해든
 */
@MappedTypes(ZonedDateTime.class)
public class ZonedDateTimeTypeHandler extends BaseTypeHandler<ZonedDateTime> {

    /**
     * Java ZonedDateTime을 DB에 저장
     * ZonedDateTime을 OffsetDateTime으로 변환 후 저장
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ZonedDateTime parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter.toOffsetDateTime());
    }

    /**
     * DB Timestamp를 ZonedDateTime으로 변환 (컬럼명으로 조회)
     */
    @Override
    public ZonedDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return getZonedDateTime(timestamp);
    }

    /**
     * DB Timestamp를 ZonedDateTime으로 변환 (컬럼 인덱스로 조회)
     */
    @Override
    public ZonedDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnIndex);
        return getZonedDateTime(timestamp);
    }

    /**
     * DB Timestamp를 ZonedDateTime으로 변환 (CallableStatement용)
     */
    @Override
    public ZonedDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp timestamp = cs.getTimestamp(columnIndex);
        return getZonedDateTime(timestamp);
    }

    /**
     * Timestamp를 시스템 기본 시간대의 ZonedDateTime으로 변환
     *
     * @param timestamp 변환할 Timestamp (null 가능)
     * @return ZonedDateTime 또는 null
     */
    private ZonedDateTime getZonedDateTime(Timestamp timestamp) {
        if (timestamp != null) {
            return ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }
}