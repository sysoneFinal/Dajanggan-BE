/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class SessionStateDto {

    private OffsetDateTime collectedAt;
    private Long activeSessions;
    private Long idleSessions;
    private Long waitingSessions;
}
