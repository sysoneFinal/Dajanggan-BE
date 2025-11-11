package com.dajanggan.domain.session.service;

import com.dajanggan.domain.session.dto.SessionDetailsDto;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SessionDetailService {

    private final SessionAgg5mService sessionAgg5mService;
    private final SessionAgg1mService sessionAgg1mService;

    public SessionDetailService(SessionAgg5mService sessionAgg5mService, SessionAgg1mService sessionAgg1mService){
        this.sessionAgg5mService = sessionAgg5mService;
        this.sessionAgg1mService = sessionAgg1mService;
    }

    /** 세션 디테일 지표 전체 조회 (단일 DB용) */
    public SessionDetailsDto getSessionDetail(Map<String, Object> params){
        return SessionDetailsDto.builder()
                .sessionSummary(sessionAgg5mService.findLatestSummary(params))
                .deadlockCounts(sessionAgg5mService.findDeadLockCount(params))
                .topUserSessions(sessionAgg5mService.findTopUserSession(params))
                .deadLockTrend(sessionAgg5mService.findDeadLockTrend(params))
                .connectionUsage(sessionAgg5mService.findConnectionUsage(params))
                .sessionStateTrend(sessionAgg1mService.findSessionStateTrend(params))
                .waitEventTrend(sessionAgg1mService.findWaitEventRatioTrend(params))
                .connectionUsageTrend(sessionAgg1mService.findConnectionUsageTrend(params))
                .avgTxDurationTrend(sessionAgg1mService.findAvgTxDurationTrend(params))
                .avgLockWaitTrend(sessionAgg1mService.findAvgLockWaitTrend(params))
                .build();
    }
}
