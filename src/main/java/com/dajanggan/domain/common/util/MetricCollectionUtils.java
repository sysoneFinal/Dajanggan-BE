package com.dajanggan.domain.common.util;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 메트릭 수집 공통 유틸리티 클래스
 * 모든 스케줄러에서 공통으로 사용하는 헬퍼 메서드 제공
 */
public class MetricCollectionUtils {

    private MetricCollectionUtils() {
        // 유틸리티 클래스이므로 인스턴스화 방지
    }

    /**
     * Map에서 Long 값 추출 (null-safe)
     */
    public static Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Map에서 Double 값 추출 (null-safe)
     */
    public static Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Float) {
            return ((Float) value).doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Map에서 String 값 추출 (null-safe)
     */
    public static String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 안전한 증분 계산 (Long 타입)
     * stats_reset 발생 시 음수가 나오면 현재 값을 반환
     * 
     * @param current 현재 값
     * @param previous 이전 값
     * @return 증분 값 (음수인 경우 0 또는 current 반환)
     */
    public static long calculateSafeDelta(Long current, Long previous) {
        if (current == null || previous == null) {
            return 0L;
        }
        long delta = current - previous;
        // stats_reset이 발생한 경우 음수가 나올 수 있음
        // 이 경우 현재 값을 반환 (stats_reset 이후의 값)
        return delta >= 0 ? delta : current;
    }

    /**
     * 안전한 증분 계산 (Double 타입)
     * stats_reset 발생 시 음수가 나오면 현재 값을 반환
     * 
     * @param current 현재 값
     * @param previous 이전 값
     * @return 증분 값 (음수인 경우 0.0 또는 current 반환)
     */
    public static double calculateSafeDelta(Double current, Double previous) {
        if (current == null || previous == null) {
            return 0.0;
        }
        double delta = current - previous;
        // stats_reset이 발생한 경우 음수가 나올 수 있음
        return delta >= 0 ? delta : current;
    }

    /**
     * 안전한 증분 계산 (Long 타입) - 별칭
     * safeMinus와 동일한 기능
     */
    public static long safeMinus(Long current, Long previous) {
        return calculateSafeDelta(current, previous);
    }

    /**
     * 안전한 증분 계산 (Double 타입) - 별칭
     * safeMinus와 동일한 기능
     */
    public static double safeMinus(Double current, Double previous) {
        return calculateSafeDelta(current, previous);
    }

    /**
     * OffsetDateTime 값 추출 (null-safe)
     */
    public static OffsetDateTime getOffsetDateTime(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime) {
            return (OffsetDateTime) value;
        }
        // Timestamp 변환 로직은 필요시 추가
        return null;
    }
}



