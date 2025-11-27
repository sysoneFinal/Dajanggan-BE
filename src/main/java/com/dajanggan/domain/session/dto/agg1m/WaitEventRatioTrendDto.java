/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class WaitEventRatioTrendDto {
    private OffsetDateTime collectedAt;
    private Integer lockWaitCount;          // Lock 대기 횟수
    private Integer ioWaitCount;            // I/O 대기 횟수
    private Integer clientWaitCount;        // Client 대기 횟수
    private Integer lwlockWaitCount;        // LWLock 대기 횟수

}
