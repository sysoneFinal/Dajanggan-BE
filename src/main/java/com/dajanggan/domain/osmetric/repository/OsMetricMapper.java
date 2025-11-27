// 작성자 : 김동현
package com.dajanggan.domain.osmetric.repository;

import com.dajanggan.domain.osmetric.domain.OsMetricAgg;
import com.dajanggan.domain.osmetric.domain.OsMetricRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * OS 메트릭 MyBatis Mapper
 */
@Mapper
public interface OsMetricMapper {
    
    /**
     * Raw 데이터 저장
     */
    void insertRaw(OsMetricRaw osMetricRaw);
    
    /**
     * Raw 데이터 배치 저장
     */
    void insertRawBatch(List<OsMetricRaw> osMetricRawList);
    
    /**
     * 특정 기간의 Raw 데이터 조회
     */
    List<OsMetricRaw> findRawByInstanceAndPeriod(
            @Param("instanceId") Long instanceId,
            @Param("metricType") String metricType,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
    
    /**
     * 집계 데이터 저장
     */
    void insertAgg(OsMetricAgg osMetricAgg);
    
    /**
     * 집계 데이터 배치 저장
     */
    void insertAggBatch(List<OsMetricAgg> osMetricAggList);
    
    /**
     * 집계 데이터 조회
     */
    List<OsMetricAgg> findAggByInstanceAndPeriod(
            @Param("instanceId") Long instanceId,
            @Param("metricType") String metricType,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
    
    /**
     * Raw 데이터 삭제 (특정 시간 이전)
     */
    int deleteRawBefore(@Param("beforeTime") OffsetDateTime beforeTime);
    
    /**
     * 활성화된 인스턴스 ID 목록 조회
     */
    List<Long> selectActiveInstanceIds();

    /**
     * 집계된 메트릭 조회 (차트용)
     */
    List<java.util.Map<String, Object>> selectAggregatedMetrics(
            @Param("instanceId") Long instanceId,
            @Param("metricType") String metricType,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}
