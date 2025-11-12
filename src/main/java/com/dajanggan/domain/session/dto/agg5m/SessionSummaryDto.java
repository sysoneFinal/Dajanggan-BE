package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;

import java.time.OffsetDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class SessionSummaryDto {
    private Long instanceId;
    private Long databaseId;
    private Double activeSessions;     // 활성 세션 수
    private Double waitingSessions;    // 대기 세션 수
    private Double idleSessions;       // 유휴 세션 수
    private Double idleTxnSessions;    // 트랜잭션 내 유휴 세션 수
    private Double avgTxDurationSec;   // 평균 트랜잭션 시간
    private OffsetDateTime createdAt;
}
