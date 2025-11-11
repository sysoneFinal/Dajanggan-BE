package com.dajanggan.domain.session.dto;

import com.dajanggan.domain.session.dto.agg1m.*;
import com.dajanggan.domain.session.dto.agg5m.*;
import lombok.*;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class SessionDetailsDto {

    private SessionSummaryDto sessionSummary;           // 5분 세션 요약
    private DeadLockSummaryDto deadlockCounts;      // 10분 데드락 통계
    private ConnectionDto connectionUsage;          // 연결 사용량 게이지
    private TopUserSessionDto topUserSessions;   // 세션 top 사용자
    private List<DeadLockCountDto> deadLockTrend; // 데드락 추이
    private List<SessionStateDto> sessionStateTrend; // 세션 상태 추이
    private List<WaitEventRatioTrendDto> waitEventTrend; // 병목 현상 추이
    private List<ConnectionTrendDto> connectionUsageTrend; // 연결 사용량 추이
    private List<AvgTxDurationTrendDto> avgTxDurationTrend;   // 평균 트랜잭션 실행시간 추이
    private List<AvgLockWaitTrendDto> avgLockWaitTrend;    // 평균 락 대기시간 추이

}
