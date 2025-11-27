/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.repository;

import com.dajanggan.domain.overview.dto.DatabaseMetricsAgg;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface DatabaseMetricsAggRepository {
    
    /** 활성 데이터베이스 ID 목록 조회 */
    List<Long> getActiveDatabaseIds();
    
    /**데이터베이스 메트릭 집계 및 저장*/
    int aggregateDatabaseMetrics(Map<String, Object> params);

    /** 데이터베이스 summary 페이지 조회 */
    List<DatabaseMetricsAgg> getDatabaseSummary(Long databaseId);

}
