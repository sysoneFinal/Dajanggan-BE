package com.dajanggan.domain.session.repository;

import com.dajanggan.domain.session.dto.agg5m.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;


@Mapper
public interface SessionAgg5mRepository {

    void insert5mAgg(@Param("metrics") List<SessionAgg5mDto> metrics);

    /** 요약카드 및 5분 집계 connection (단일 DB용 - Details 페이지) */
    SessionSummaryDto findLatestSummary(Map<String, Object> params);

    /** 커넥션 사용량 */
    ConnectionDto findConnectionUsage(Map<String, Object> params);

    /** 요약카드 데드락(10분 집계)*/
    DeadLockSummaryDto findLatestDeadLock (Map<String, Object> params);

    /** 상위 최다 세션 사용자 */
    TopUserSessionDto findTopUserSession (Map<String, Object> params);

    /** 데드락 추이*/
    List<DeadLockCountDto> findDeadLockTrend(Map<String, Object> params);

    /**  데드락 리스트 */
    List<DeadLockListDto> findDeadLockList(Map<String, Object> params);

    /** 데드락 상세 모달 */
    DeadLockDetailDto getDeadlockDetail(Map<String, Object> params);

    /**최근 24시간 내 데드락 반복 발생 횟수*/
    int getDeadlockRecurrenceCount(Map<String, Object> params);
}
