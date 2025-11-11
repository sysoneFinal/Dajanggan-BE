package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;
import java.time.OffsetDateTime;

/**
 * 5분 단위 세션 요약 집계 DTO
 * - 상단 요약 카드 (Active, Idle, Wait, Tx Time, Deadlock)
 * - 커넥션/비율, Top5 사용자 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAgg5mDto {

    private Long instanceId;           // 인스턴스 ID
    private Long databaseId;           // DB ID
    private OffsetDateTime collectedAt; // 수집 시각


    private Double totalSessions;      // 전체 세션 수
    private Double activeSessions;     // 활성 세션 수
    private Double waitingSessions;    // 대기 세션 수
    private Double idleSessions;       // 유휴 세션 수
    private Double idleTxnSessions;    // 트랜잭션 내 유휴 세션 수


    private Double avgTxDurationSec;   // 평균 트랜잭션 시간 (최근 5분)
    private Integer deadlockCount;     // 최근 10분 내 데드락 횟수


    private Double usedConnections;    // 사용 커넥션 수
    private Double maxConnections;     // 최대 커넥션 수
    private Double activeRatio;        // 활성 세션 비율 (%)
    private Double waitRatio;          // 대기 세션 비율 (%)


    private String topUser1;
    private Double topUser1Sessions;
    private String topUser2;
    private Double topUser2Sessions;
    private String topUser3;
    private Double topUser3Sessions;
    private String topUser4;
    private Double topUser4Sessions;
    private String topUser5;
    private Double topUser5Sessions;

    private OffsetDateTime createdAt;
}
