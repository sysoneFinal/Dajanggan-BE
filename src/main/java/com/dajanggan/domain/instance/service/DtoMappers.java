package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.dto.DatabaseResponse;
import com.dajanggan.domain.instance.dto.InstanceResponse;
import com.dajanggan.domain.instance.dto.InstanceWithDatabasesDto;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO 변환 유틸리티 클래스
 *
 * 주요 책임:
 * 
 *   DTO 간 변환
 *   여러 DTO를 조합한 복합 DTO 생성
 *   uptime 계산 등 파생 데이터 생성
 * 
 *
 * 설계 원칙:
 * 
 *   모든 메서드는 static으로 선언 (인스턴스 생성 불필요)
 *   Entity를 받지 않고 DTO만 받음
 *   null 안전성 보장
 *   불변 컬렉션 반환
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-04  김민서    1. 최초작성자
 *
 */
public final class DtoMappers {

    /**
     * Private 생성자 - 유틸리티 클래스 인스턴스화 방지
     */
    private DtoMappers() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }

    /**
     * InstanceResponse와 DatabaseResponse 리스트를 결합하여
     * InstanceWithDatabasesDto 생성
     *
     * 추가 작업:
     * 
     *   인스턴스 생성일로부터 uptime 계산 (밀리초 단위)
     *   DatabaseResponse 리스트를 그대로 포함
     * 
     *
     * @param instanceResponse 인스턴스 응답 DTO
     * @param databases DatabaseResponse DTO 리스트
     * @return InstanceWithDatabasesDto
     * @throws NullPointerException instanceResponse가 null인 경우
     */
    public static InstanceWithDatabasesDto toInstanceWithDbDto(
            InstanceResponse instanceResponse,
            List<DatabaseResponse> databases) {

        if (instanceResponse == null) {
            throw new NullPointerException("InstanceResponse는 null일 수 없습니다.");
        }

        // Uptime 계산
        Long uptimeMs = null;
        if (instanceResponse.getCreatedAt() != null) {
            uptimeMs = calculateUptimeMillis(instanceResponse.getCreatedAt());
        }

        return InstanceWithDatabasesDto.builder()
                .instanceId(instanceResponse.getInstanceId())
                .instanceName(instanceResponse.getInstanceName())
                .host(instanceResponse.getHost())
                .port(instanceResponse.getPort())
                .version(instanceResponse.getVersion())
                .createdAt(instanceResponse.getCreatedAt())
                .updatedAt(instanceResponse.getUpdatedAt())
                .uptimeMs(uptimeMs)
                .databases(databases != null ? databases : List.of())
                .build();
    }

    /**
     * Uptime 계산 (밀리초 단위)
     *
     * 생성 시점부터 현재까지의 경과 시간을 계산
     *
     * @param createdAt 생성 일시
     * @return 경과 시간 (밀리초)
     */
    private static long calculateUptimeMillis(OffsetDateTime createdAt) {
        if (createdAt == null) {
            return 0L;
        }

        OffsetDateTime now = OffsetDateTime.now();
        Duration duration = Duration.between(createdAt, now);

        return duration.toMillis();
    }
}