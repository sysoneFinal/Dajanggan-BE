/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class AvgLockWaitTrendDto {
    private OffsetDateTime createdAt;
    private Double avgLockWaitSec;     // 평균 Lock 대기 시간
}
