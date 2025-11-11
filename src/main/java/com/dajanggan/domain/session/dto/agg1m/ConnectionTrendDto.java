package com.dajanggan.domain.session.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
public class ConnectionTrendDto {

    private OffsetDateTime collectedAt;
    private Double usedConnections;

}
