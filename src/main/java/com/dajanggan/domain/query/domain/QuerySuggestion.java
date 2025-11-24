package com.dajanggan.domain.query.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿼리 제안 Entity
 *
 * 기능:
 * - AI 또는 규칙 기반으로 생성된 쿼리 최적화 제안 저장
 * - 7일 캐싱을 통한 중복 분석 방지
 * - OpenAI API 사용 토큰 수 추적
 * - 개선율 예측 및 제안 레벨(높음/경고/정보) 관리
 *
 * @author 이해든
 */
@Getter
@Setter
@NoArgsConstructor
public class QuerySuggestion {

    private Long suggestionId;
    private Long databaseId;
    private String queryHash;
    private Boolean hasTuningSuggestion;
    private String suggestionLevel;
    private String suggestionType;
    private String suggestionTitle;
    private String suggestionDescription;
    private String suggestionSql;
    private BigDecimal expectedImprovementPercent;
    private Integer queriesPerTransaction;
    private Integer transactionId;
    private LocalDateTime createdAt;

    // AI 캐싱 관련 필드
    private String aiModel;
    private Integer tokenUsed;
    private Boolean isFromCache;

    /**
     * 생성 시점에 자동으로 createdAt 설정
     */
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}