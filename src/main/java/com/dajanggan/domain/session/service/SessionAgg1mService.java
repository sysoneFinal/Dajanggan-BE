package com.dajanggan.domain.session.service;

import com.dajanggan.domain.session.dto.agg1m.*;
import com.dajanggan.domain.session.repository.SessionAgg1mRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SessionAgg1mService {

    private final SessionAgg1mRepository sessionAgg1mRepository;

    public SessionAgg1mService(SessionAgg1mRepository sessionAgg1mRepository){
        this.sessionAgg1mRepository = sessionAgg1mRepository;
    }

    /** 세션 활성 상태 추이 */
    public List<SessionStateDto> findSessionStateTrend(Map<String, Object> params){
        return sessionAgg1mRepository.getSessionStatTrend(params);
    }

    /** 병목 현상 추이 */
    public List<WaitEventRatioTrendDto> findWaitEventRatioTrend(Map<String, Object> params){
        return sessionAgg1mRepository.getWaitEventRatioTrend(params);
    }

    /** Connection Usage 추이 */
    public List<ConnectionTrendDto> findConnectionUsageTrend(Map<String, Object> params){
        return sessionAgg1mRepository.getConnectionUsageTrend(params);
    }

    /** 트랜잭션 실행 시간 추이 */
    public List<AvgTxDurationTrendDto> findAvgTxDurationTrend(Map<String, Object> params){
        return sessionAgg1mRepository.getTxDurationTrend(params);
    }

    /** 평균 락 대기시간 추이 */
    public List<AvgLockWaitTrendDto> findAvgLockWaitTrend(Map<String, Object> params){
        return sessionAgg1mRepository.getLockWaitTrend(params);
    }

    /** */
}
