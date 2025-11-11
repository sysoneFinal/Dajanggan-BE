package com.dajanggan.domain.session.service;

import com.dajanggan.domain.session.dto.agg5m.*;
import com.dajanggan.domain.session.repository.SessionAgg5mRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SessionAgg5mService {

    private final SessionAgg5mRepository sessionAgg5mRepository;

    public SessionAgg5mService(SessionAgg5mRepository sessionAgg5mRepository){
        this.sessionAgg5mRepository = sessionAgg5mRepository;
    }

    /** 지표 : 요약카드 4개 (단일 DB - Details 페이지) */
    public SessionSummaryDto findLatestSummary(Map<String, Object> params) {
        return sessionAgg5mRepository.findLatestSummary(params);
    }
    /** 지표 : 데드락 요약카드 1개*/
    public DeadLockSummaryDto findDeadLockCount(Map<String, Object> params) {
        return sessionAgg5mRepository.findLatestDeadLock(params);
    }

    /** 커넥션 사용량 조회 */
    public ConnectionDto findConnectionUsage(Map<String, Object> params){
        return sessionAgg5mRepository.findConnectionUsage(params);
    }

    /** 세션 최다 사용자 추적 */
    public TopUserSessionDto findTopUserSession(Map<String, Object> params){
        return sessionAgg5mRepository.findTopUserSession(params);
    }

    /** 데드락 수 추이 */
    public List<DeadLockCountDto> findDeadLockTrend(Map<String, Object> params){
        return sessionAgg5mRepository.findDeadLockTrend(params);
    }
}
