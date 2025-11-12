package com.dajanggan.domain.session.dto.agg1m;

import lombok.*;
import java.time.OffsetDateTime;

/**
 * 1분 단위 세션 상세 집계 DTO
 * 실시간 모니터링용 (세션 상태, 대기, 락, 커넥션 중심)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAgg1mDto {

    private Long instanceId;           // 인스턴스 ID
    private Long databaseId;           // DB ID
    private OffsetDateTime collectedAt; // 수집 시각

    private Double totalSessions;      // 전체 세션 수
    private Double activeSessions;     // 활성 세션 수
    private Double waitingSessions;    // 대기 세션 수
    private Double idleSessions;       // 유휴 세션 수

    // Wait/Lock 관련
    private Integer lockWaitCount;     // Lock 대기 횟수
    private Integer ioWaitCount;       // IO 대기 횟수
    private Integer lwlockWaitCount;   // LWLock 대기 횟수
    private Integer clientWaitCount;   // Client 대기 횟수

    // 트랜잭션 / 락 시간
    private Double avgTxDurationSec;   // 평균 트랜잭션 시간
    private Double avgLockWaitSec;     // 평균 Lock 대기 시간

    // 커넥션 정보
    private Double usedConnections;    // 사용 커넥션 수
    private Double maxConnections;     // 최대 커넥션 수

    // 이벤트성
    private Integer deadlockCount;     // Deadlock 발생 횟수

    private OffsetDateTime createdAt;  // 생성 시각
}
