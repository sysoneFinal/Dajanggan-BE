package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesDto {

    private String timeLabel;              // 시간 라벨 (예: "0:00", "2:00")
    private OffsetDateTime bucketTime;      // 집계 기준 시간
    
    // Checkpoint 발생 횟수
    private Long requestedCount;           // 요청 기반 횟수
    private Long timedCount;               // 시간 기반 횟수
    
    // 처리 시간
    private Double avgWriteTime;           // 평균 Write 시간 (ms)
    private Double avgSyncTime;            // 평균 Sync 시간 (ms)
    private Double avgTotalTime;           // 평균 Total 시간 (ms)
    
    // WAL 생성량
    private Long walBytes;                 // WAL 생성량 (bytes)
    
    // 버퍼 처리량
    private Long buffersCheckpoint;        // 버퍼 처리량
    
    // Checkpoint 간격
    private Double avgIntervalMinutes;     // 평균 간격 (분)
}
