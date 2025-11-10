package com.dajanggan.domain.overview.repository;

import com.dajanggan.domain.overview.dto.MetricDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MetricRepository {

    // 지표 정의 목록 조회
    List<MetricDefinition> getMetricList();

    List<Map<String, Object>> getMetricData(
            @Param("tableName") String tableName,
            @Param("columnName") String columnName,
            @Param("instanceId") String instanceId,
            @Param("databaseId") String databaseId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
