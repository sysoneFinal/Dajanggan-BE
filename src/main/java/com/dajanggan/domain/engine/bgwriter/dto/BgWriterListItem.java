package com.dajanggan.domain.engine.bgwriter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BGWriter 리스트 아이템 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgWriterListItem {
    private String id;
    private String timestamp;
    private Long buffersAlloc;
    private Double cleanRate;
    private Double backendRate;
    private Long checkpointBuffers;
    private Double backendRatio;
    private Double fsyncRate;
    private Double maxWrittenRate;
    private Double avgCycleTime;
    private String status;
}



