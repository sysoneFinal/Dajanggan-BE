package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class VacuumBloatDetailRepository {

    private final VacuumBloatDetailMapper bloatDetailMapper;

    /**
     * 특정 테이블의 최신 메트릭 조회
     */
    public VacuumTrendMetrics findLatestMetricsByTable(Long databaseId, String tableName) {
        return bloatDetailMapper.findLatestMetricsByTable(databaseId, tableName);
    }

    /**
     * 특정 테이블의 Bloat % 트렌드 조회 (기간별)
     */
    public List<VacuumTrendMetrics> findBloatTrendByTable(
            Long databaseId, String tableName, LocalDateTime startDate, LocalDateTime endDate) {
        return bloatDetailMapper.findBloatTrendByTable(databaseId, tableName, startDate, endDate);
    }

    /**
     * 특정 테이블의 Dead Tuples 트렌드 조회 (기간별)
     */
    public List<VacuumTrendMetrics> findDeadTuplesTrendByTable(
            Long databaseId, String tableName, LocalDateTime startDate, LocalDateTime endDate) {
        return bloatDetailMapper.findDeadTuplesTrendByTable(databaseId, tableName, startDate, endDate);
    }

    /**
     * 특정 테이블의 인덱스별 Bloat 트렌드 조회 (기간별)
     */
    public List<Map<String, Object>> findIndexBloatTrendByTable(
            Long databaseId, String tableName, LocalDateTime startDate, LocalDateTime endDate) {
        return bloatDetailMapper.findIndexBloatTrendByTable(databaseId, tableName, startDate, endDate);
    }

    /**
     * 데이터베이스 내 테이블 목록 조회
     */
    public List<String> findTableList(Long databaseId) {
        return bloatDetailMapper.findTableList(databaseId);
    }
}