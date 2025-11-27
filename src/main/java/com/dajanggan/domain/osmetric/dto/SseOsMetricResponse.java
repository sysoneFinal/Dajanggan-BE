// 작성자 : 김동현
package com.dajanggan.domain.osmetric.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SSE로 전송될 OS 메트릭 응답 DTO
 * 프론트엔드 RealtimeMetrics 인터페이스에 맞춤
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseOsMetricResponse {

    /**
     * 인스턴스 ID
     */
    private Long instanceId;

    /**
     * 수집 시각 (타임스탬프)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime collectedAt;

    /**
     * 타임스탬프 (밀리초)
     */
    private Long timestamp;

    /**
     * CPU 사용률 (%)
     */
    private Double cpu;

    /**
     * 메모리 사용률 (%)
     */
    private Double memory;

    /**
     * 메모리 총량 (GB)
     */
    private Double memoryTotalGB;

    /**
     * 메모리 사용량 (GB)
     */
    private Double memoryUsedGB;

    /**
     * 메모리 사용 가능량 (GB)
     */
    private Double memoryAvailableGB;

    /**
     * 메모리 캐시 (GB)
     */
    private Double memoryCacheGB;

    /**
     * 디스크 사용률 (%)
     */
    private Double diskUsage;

    /**
     * 디스크 총량 (GB)
     */
    private Double diskTotalGB;

    /**
     * 디스크 사용량 (GB)
     */
    private Double diskUsedGB;

    /**
     * 디스크 사용 가능량 (GB)
     */
    private Double diskAvailableGB;

    /**
     * 디스크 읽기 속도 (MB/s)
     */
    private Double diskRead;

    /**
     * 디스크 쓰기 속도 (MB/s)
     */
    private Double diskWrite;

    /**
     * Load Average [1분, 5분, 15분]
     */
    private List<Double> loadAverage;

    /**
     * Swap 사용률 (%)
     */
    private Double swapUsage;

    /**
     * Swap 총량 (GB)
     */
    private Double swapTotalGB;

    /**
     * Swap 사용량 (GB)
     */
    private Double swapUsedGB;

    /**
     * Swap In Rate (per sec)
     */
    private Long swapInPerSec;

    /**
     * Swap Out Rate (per sec)
     */
    private Long swapOutPerSec;

    /**
     * 이벤트 타입 (SSE event type)
     */
    private String eventType;

    /**
     * 타임스탬프 자동 설정
     */
    public void setCollectedAt(LocalDateTime collectedAt) {
        this.collectedAt = collectedAt;
        if (collectedAt != null) {
            this.timestamp = System.currentTimeMillis();
        }
    }
}
