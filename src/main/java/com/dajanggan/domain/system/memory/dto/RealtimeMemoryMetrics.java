package com.dajanggan.domain.system.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 실시간 Memory 메트릭 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeMemoryMetrics {
    private MemoryDashboardResponse.OsMemoryUsageWidget osMemoryUsage;
    private MemoryDashboardResponse.SwapUsageWidget swapUsage;
    private MemoryDashboardResponse.SharedBufferHitWidget sharedBufferHit;
    private MemoryDashboardResponse.BufferUsageWidget bufferUsage;
    private MemoryDashboardResponse.TempFileUsageWidget tempFileUsage;
}

