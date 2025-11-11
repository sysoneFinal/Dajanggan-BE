package com.dajanggan.domain.session.dto.raw;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockSessionDto {
    private Integer pid;
    private String lockType;
    private String mode;
    private Boolean granted;

}
