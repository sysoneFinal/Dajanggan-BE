package com.dajanggan.domain.system.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Memory 30분 집계 데이터 엔티티
 * 테이블: memory_agg_30m
 * memory_agg 데이터를 30분 단위로 재집계
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryAgg30m {

    private Long memoryAgg30mId;            // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime timeBucket;      // 30분 단위 시간 버킷
    
    // 대상 객체 정보
    private String relname;                 // 테이블/인덱스명
    private String relkind;                 // 객체 종류
    
    // 버퍼 통계 (30분 평균)
    private Long avgBuffers;                // 평균 버퍼 수
    private Double avgBufferUsagePct;       // 평균 버퍼 사용률 (%)
    private Double avgDirtyRatio;           // 평균 Dirty 비율 (%)
    private Double avgPinnedBuffers;        // 평균 고정 버퍼
    
    // I/O 통계 (30분 합계)
    private Long totalHeapBlksRead;         // Heap 블록 읽기 합계
    private Long totalHeapBlksHit;          // Heap 블록 히트 합계
    private Long totalIdxBlksRead;          // 인덱스 블록 읽기 합계
    private Long totalIdxBlksHit;           // 인덱스 블록 히트 합계
    
    // 계산된 메트릭 (30분 평균)
    private Double avgCacheHitRatio;        // 평균 캐시 히트율 (%)
    
    // usagecount 통계
    private Double avgUsagecount;           // 평균 버퍼 재사용 빈도
    private Double avgBufferReuseScore;     // 평균 버퍼 재사용 점수
    
    // pg_stat_database 통계
    private String databaseName;            // 데이터베이스명
    private Long totalTempFiles;            // 임시 파일 생성 합계
    private Long totalTempBytes;            // 임시 파일 바이트 합계
    private Double avgTempFileRate;         // 평균 초당 임시 파일 생성
    private Double avgTempBytesPerSec;      // 평균 초당 임시 파일 바이트
    private Double totalBlkReadTime;        // 블록 읽기 대기 시간 합계
    private Double totalBlkWriteTime;       // 블록 쓰기 대기 시간 합계
    private Double avgIoWaitTimeMs;         // 평균 I/O 대기 시간 (ms)
    
    // 데이터 건수
    private Long recordCount;               // 집계된 레코드 수
    
    private OffsetDateTime createdAt;       // 생성 시각
}
