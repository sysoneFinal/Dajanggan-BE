/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class DeadLockSummaryDto {

    private Long deadlockCount;
}
