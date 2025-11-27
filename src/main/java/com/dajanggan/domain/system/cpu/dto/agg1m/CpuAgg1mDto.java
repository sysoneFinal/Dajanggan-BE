// 작성자 : 김동현
package com.dajanggan.domain.system.cpu.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * CPU 1분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuAgg1mDto {
    private Long instanceId;
    private OffsetDateTime collectedAt;
    private OffsetDateTime createdAt;
}







