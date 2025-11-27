package com.dajanggan.domain.instance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Database 엔티티
 *
 * 테이블: monitor_database
 *
 * 주요 책임:
 * 
 *   데이터베이스 정보 영속성 관리
 *   MyBatis 매핑 대상
 *   비즈니스 로직 포함 가능
 * 
 *
 * 설계 원칙:
 * 
 *   Getter/Setter 제공 (MyBatis 호환)
 *   Builder 패턴 지원 (가독성)
 *   @Data 대신 명시적 어노테이션
 *   비즈니스 로직 메서드 포함 가능
 * 
 *
 * 주의사항:
 * 
 *   Controller에 직접 반환 금지
 *   Service에서 DTO로 변환하여 사용
 *   민감 정보 포함 가능 (내부 사용)
 * 
 *
 *   ----------  ------  --------------------------------------------------
 *     작업일자      작성자    Description
 *     ----------  ------  --------------------------------------------------
 *     2025-11-04  김민서    1. 최초작성자
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Database {

    private Long databaseId;
    private Long instanceId;
    private String databaseName;

    @Builder.Default
    private Boolean isEnabled = true;

    private Integer connections;
    private String sizeBytes;
    private String cacheHitRate;

    @Builder.Default
    private String status = "active";

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // ========== 비즈니스 로직 메서드 ==========
    /**
     * 활성화 여부 확인
     *
     * @return 활성화되어 있으면 true
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(this.isEnabled);
    }

    /**
     * 메트릭 정보 업데이트
     *
     * @param connections 연결 수
     * @param sizeBytes 크기
     * @param cacheHitRate 캐시 히트율
     */
    public void updateMetrics(Integer connections, String sizeBytes, String cacheHitRate) {
        this.connections = connections;
        this.sizeBytes = sizeBytes;
        this.cacheHitRate = cacheHitRate;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 상태 변경
     *
     * @param newStatus 새로운 상태
     */
    public void changeStatus(String newStatus) {
        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now();
    }
}