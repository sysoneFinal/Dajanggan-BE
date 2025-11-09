package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Checkpoint List 요청 파라미터
 * GET /api/engine/checkpoint/list
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointListRequest {

    private Long instanceId;                    // 필수
    private String period;                      // "1h", "6h", "24h", "7d" (기본: "24h")
    private List<String> types;                 // ["timed", "requested"]
    private List<String> statuses;              // ["정상", "주의", "위험"]
    private Integer page;                       // 기본값: 1
    private Integer size;                       // 기본값: 10
    
    /**
     * period를 시간(hour)으로 변환
     */
    public Integer getPeriodInHours() {
        if (period == null || period.isEmpty()) {
            return 24; // 기본값
        }
        
        return switch (period.toLowerCase()) {
            case "1h" -> 1;
            case "6h" -> 6;
            case "24h" -> 24;
            case "7d" -> 168; // 7 * 24
            default -> 24;
        };
    }
    
    /**
     * Offset 계산 (페이징용)
     */
    public Integer getOffset() {
        int pageNum = (page != null && page > 0) ? page : 1;
        int sizeNum = (size != null && size > 0) ? size : 10;
        return (pageNum - 1) * sizeNum;
    }
    
    /**
     * Limit 계산 (페이징용)
     */
    public Integer getLimit() {
        return (size != null && size > 0) ? size : 10;
    }
}
