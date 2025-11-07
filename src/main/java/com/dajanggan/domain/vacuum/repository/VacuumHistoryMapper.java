package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VacuumHistoryMapper {

    // Vacuum History 목록 조회
    List<VacuumHistoryDto.Raw> getVacuumHistoryList(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 특정 테이블의 최근 vacuum 빈도 계산
    Integer getVacuumFrequency(
            @Param("databaseId") Long databaseId,
            @Param("hours") int hours
    );
}