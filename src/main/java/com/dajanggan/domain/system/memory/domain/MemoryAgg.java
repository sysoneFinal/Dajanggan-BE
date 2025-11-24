package com.dajanggan.domain.system.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Memory 집계 데이터 엔티티
 * 테이블: memory_agg
 * Raw 데이터 기반 집계 및 계산된 메트릭
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryAgg {

    private Long memoryAggId;               // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime collectedAt;     // 수집 시각
    
    // 대상 객체 정보
    private String relname;                 // 테이블/인덱스명
    private String relkind;                 // 객체 종류
    
    // 버퍼 통계 (Raw 데이터 그대로)
    private Long avgBuffers;                // 평균 버퍼 수
    private Double avgBufferUsagePct;       // 버퍼 사용률 (%)
    private Double avgDirtyRatio;           // Dirty 비율 (%)
    private Double avgPinnedBuffers;        // 평균 고정 버퍼
    
    // I/O 통계 (증분)
    private Long deltaHeapBlksRead;         // Heap 블록 읽기 증분
    private Long deltaHeapBlksHit;          // Heap 블록 히트 증분
    private Long deltaIdxBlksRead;          // 인덱스 블록 읽기 증분
    private Long deltaIdxBlksHit;           // 인덱스 블록 히트 증분
    
    // 계산된 메트릭
    private Double cacheHitRatio;           // 캐시 히트율 (%)
    
    // usagecount 통계 (기본값 0, pg_buffercache 미사용)
    private Double avgUsagecount;           // 버퍼 재사용 빈도 평균
    private Double bufferReuseScore;        // 버퍼 재사용 점수 (0~100)
    
    // pg_stat_database 통계
    private String databaseName;            // 데이터베이스명
    private Long deltaTempFiles;            // 임시 파일 생성 증분
    private Long deltaTempBytes;            // 임시 파일 바이트 증분
    private Double tempFileRate;            // 초당 임시 파일 생성 수
    private Double tempBytesPerSec;         // 초당 임시 파일 바이트
    private Double deltaBlkReadTime;        // 블록 읽기 대기 시간 증분
    private Double deltaBlkWriteTime;       // 블록 쓰기 대기 시간 증분
    private Double avgIoWaitTimeMs;         // 평균 I/O 대기 시간 (ms)
    
    // 상태 (정상/주의/위험)
    private String status;
    
    private OffsetDateTime createdAt;       // 생성 시각
}