package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Checkpoint 리스트 아이템 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointListItem {
    private String id;
    private String timestamp;
    private String type;
    private Double writeTime;
    private Double syncTime;
    private Double totalTime;
    private String walGenerated;
    private Long walFilesAdded;
    private Long walFilesRemoved;
    private String checkpointDistance;
    private Long buffersWritten;
    private Long buffersBackend;
    private Double avgBuffersPerSec;
    private String status;
}









