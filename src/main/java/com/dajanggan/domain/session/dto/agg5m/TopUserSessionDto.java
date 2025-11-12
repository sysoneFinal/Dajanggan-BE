package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class TopUserSessionDto {
    private Long instanceId;
    private Long databaseId;
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
}
