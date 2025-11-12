package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;

import java.time.OffsetDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class DeadLockCountDto {
    private OffsetDateTime collectedAt;
    private Long deadlockCount;
}
