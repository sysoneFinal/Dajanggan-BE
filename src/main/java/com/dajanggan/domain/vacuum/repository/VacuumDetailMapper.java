// 작성자: 김민서
package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumDetailDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface VacuumDetailMapper {

    /**
     * 특정 테이블의 최신 세션 정보 조회
     */
    VacuumDetailDto.SessionInfoRaw findLatestSessionInfo(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName
    );

    /**
     * 특정 테이블의 Progress 데이터 조회 (시간순)
     */
    List<VacuumDetailDto.ProgressRaw> findProgressData(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 데이터베이스 내 테이블 목록 조회
     */
    List<String> findTableList(
            @Param("databaseId") Long databaseId
    );
}