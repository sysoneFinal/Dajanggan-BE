package com.dajanggan.domain.query.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 쿼리 제안 Entity
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
    private ZonedDateTime createdAt;

    // AI 캐싱 관련 필드 추가
    private String aiModel;           // 사용된 AI 모델 (예: gpt-4)
    private Integer tokenUsed;        // 사용된 토큰 수
    private Boolean isFromCache;      // 캐시에서 가져온 제안인지 여부

    // 생성 시점에 자동으로 createdAt 설정
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = ZonedDateTime.now();
        }
    }
}