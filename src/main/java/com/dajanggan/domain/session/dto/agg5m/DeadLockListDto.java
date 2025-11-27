/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;

import java.time.OffsetDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class DeadLockListDto {

    private Long sessionRawId;
    private OffsetDateTime collectedAt;
    private String username;
    private String query;
    private Double lockDurationMs;
    private String tableName;
    private String blockingUserName;
    private Integer pid;


}
