package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryRawDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vacuum History Mapper
 * - vacuum_trend_metrics 테이블 조회
 */
@Mapper
public interface VacuumHistoryMapper {

    /**
     * Vacuum History 목록 조회
     * @param start 시작 시간
     * @param end 종료 시간
     * @return Vacuum History Raw 데이터 목록
     */
    List<VacuumHistoryRawDto> getVacuumHistoryList(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 특정 테이블의 최근 vacuum 빈도 계산
     * @param databaseId 데이터베이스 ID
     * @param hours 조회 기간 (시간)
     * @return 총 vacuum 실행 횟수
     */
    Integer getVacuumFrequency(
            @Param("databaseId") Long databaseId,
            @Param("hours") int hours
    );
}