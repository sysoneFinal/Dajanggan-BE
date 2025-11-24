package com.dajanggan.domain.system.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Memory Raw 데이터 엔티티 (pg_statio 기반)
 * 테이블: memory_raw
 * PostgreSQL의 I/O 통계 및 메모리 관련 메트릭
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRaw {

    private Long memoryRawId;               // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime collectedAt;     // 수집 시각
    
    // 대상 객체 정보
    private String relname;                 // 테이블/인덱스명 (NULL이면 전체)
    private String relkind;                 // 객체 종류 (r=table, i=index)
    
    // 버퍼 통계 (기본값 0, pg_buffercache 미사용)
    private Long buffers;                   // 버퍼 수
    private Long dirtyBuffers;              // Dirty 버퍼 수
    private Long pinnedBuffers;             // 고정된 버퍼 수
    
    // pg_statio 통계 (누적값)
    private Long heapBlksRead;              // Heap 블록 읽기 (디스크)
    private Long heapBlksHit;               // Heap 블록 히트 (캐시)
    private Long idxBlksRead;               // 인덱스 블록 읽기
    private Long idxBlksHit;                // 인덱스 블록 히트
    
    // usagecount 통계 (기본값 0, pg_buffercache 미사용)
    private Double avgUsagecount;           // usagecount 평균 (버퍼 재사용 빈도)
    private Long maxUsagecount;             // usagecount 최대값
    private Long minUsagecount;             // usagecount 최소값
    
    // pg_stat_database 통계
    private String databaseName;            // 데이터베이스명
    private Long tempFiles;                 // 임시 파일 생성 수 (누적)
    private Long tempBytes;                 // 임시 파일 사용 바이트 (누적)
    private Double blkReadTime;             // 블록 읽기 대기 시간 (ms, 누적)
    private Double blkWriteTime;            // 블록 쓰기 대기 시간 (ms, 누적)
    
    private OffsetDateTime createdAt;       // 생성 시각
}