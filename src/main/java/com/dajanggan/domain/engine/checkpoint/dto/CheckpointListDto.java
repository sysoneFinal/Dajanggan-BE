package com.dajanggan.domain.engine.checkpoint.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Checkpoint List 응답 DTO
 * GET /api/engine/checkpoint/list
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointListDto {

    private Long id;                        // checkpoint_raw_id
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime timestamp;       // collected_at
    
    private String type;                    // "timed" or "requested"
    private Double writeTime;               // 초 단위
    private Double syncTime;                // 초 단위
    private Double totalTime;               // 초 단위
    private String walGenerated;            // "1.3GB" 형식
    private Long walFilesAdded;             // WAL 파일 추가 수
    private Long walFilesRemoved;           // WAL 파일 제거 수
    private String checkpointDistance;      // "5분" 형식
    private Long buffersWritten;            // buffers_checkpoint
    private Long buffersBackend;            // buffers_backend
    private Long avgBuffersPerSec;          // 평균 버퍼/초
    private String status;                  // "정상", "주의", "위험"
}
