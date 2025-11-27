/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class AvgTxDurationTrendDto {

    private OffsetDateTime collectedAt;
    private Double avgTxDurationSec;   // 평균 트랜잭션 시간


}
