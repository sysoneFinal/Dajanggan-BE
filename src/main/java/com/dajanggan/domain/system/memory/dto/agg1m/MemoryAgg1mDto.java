// 작성자 : 김동현
package com.dajanggan.domain.system.memory.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Memory 1분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryAgg1mDto {
    private Long instanceId;
    private OffsetDateTime collectedAt;
    private String relname;
    private String databaseName;
}







