/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.repository;

import com.dajanggan.domain.session.dto.agg1m.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SessionAgg1mRepository {

    /** 1분 집계 데이터 저장 */
    void insertAgg1m(@Param("metrics") List<SessionAgg1mDto> metrics);

    /** 세션 활성 상태 추이 */
    List<SessionStateDto> getSessionStatTrend(Map<String, Object> params);

    /** 병목 현상 추이 */
    List<WaitEventRatioTrendDto> getWaitEventRatioTrend(Map<String, Object>params);

    /** Connection Usage 추이*/
    List<ConnectionTrendDto> getConnectionUsageTrend(Map<String, Object> params);

    /** 트랜잭션 지속시간 추이*/
    List<AvgTxDurationTrendDto> getTxDurationTrend(Map<String, Object> params);

    /** 락 대기 시간 추이 */
    List<AvgLockWaitTrendDto> getLockWaitTrend(Map<String, Object> params);


}
