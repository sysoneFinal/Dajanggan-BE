/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class DeadLockDetailDto {
    private OffsetDateTime collectedAt;
    private String databaseName;
    private String tableName;
    private String lockType;
    private Double duration;

    // 차단 당한 세션
    private Integer waiterPid;
    private String waiterUser;
    private String waiterQuery;

    // 차단한 세션
    private Integer holderPid;
    private String holderUser;
    private String holderQuery;

    // 반복 카운트
    private Integer recurrenceCount;

}
