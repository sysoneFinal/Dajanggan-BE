package com.dajanggan.domain.session.service;

import com.dajanggan.domain.session.dto.agg1m.*;
import com.dajanggan.domain.session.repository.SessionAgg1mRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SessionAgg1mService {

    private final SessionAgg1mRepository sessionAgg1mRepository;

    public SessionAgg1mService(SessionAgg1mRepository sessionAgg1mRepository){
        this.sessionAgg1mRepository = sessionAgg1mRepository;
    }

    /** 세션 활성 상태 추이 */
    public List<SessionStateDto> findSessionStateTrend(Map<String, Object> params){
        List<SessionStateDto> result = sessionAgg1mRepository.getSessionStatTrend(params);
        log.debug("findSessionStateTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 병목 현상 추이 */
    public List<WaitEventRatioTrendDto> findWaitEventRatioTrend(Map<String, Object> params){
        List<WaitEventRatioTrendDto> result = sessionAgg1mRepository.getWaitEventRatioTrend(params);
        log.debug("findWaitEventRatioTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** Connection Usage 추이 */
    public List<ConnectionTrendDto> findConnectionUsageTrend(Map<String, Object> params){
        List<ConnectionTrendDto> result = sessionAgg1mRepository.getConnectionUsageTrend(params);
        log.debug("findConnectionUsageTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 트랜잭션 실행 시간 추이 */
    public List<AvgTxDurationTrendDto> findAvgTxDurationTrend(Map<String, Object> params){
        List<AvgTxDurationTrendDto> result = sessionAgg1mRepository.getTxDurationTrend(params);
        log.debug("findAvgTxDurationTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 평균 락 대기시간 추이 */
    public List<AvgLockWaitTrendDto> findAvgLockWaitTrend(Map<String, Object> params){
        List<AvgLockWaitTrendDto> result = sessionAgg1mRepository.getLockWaitTrend(params);
        log.debug("findAvgLockWaitTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }
}
