package com.dajanggan.domain.instance.dto;

import com.dajanggan.domain.instance.domain.Instance;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 인스턴스 응답 DTO
 *
 * 사용처:
 * 
 *   인스턴스 조회 API 응답
 *   인스턴스 생성/수정 후 응답
 * 
 *
 * 보안:
 * 
 *   비밀번호(secretRef) 제외 - 절대 노출 금지
 *   민감한 내부 설정 제외
 * 
 *
 * 설계 원칙:
 * 
 *   불변 객체 (Getter만)
 *   Builder 패턴
 *   null 필드 제외 옵션
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-06  김민서    1. 최초작성자
 *
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // null 필드는 JSON에서 제외
public class InstanceResponse {

    private Long instanceId;
    private String instanceName;
    private String host;
    private Integer port;
    private String userName;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String version;

    /**
     * Entity를 DTO로 변환
     *
     * 보안: secretRef(비밀번호)는 의도적으로 제외됨
     *
     * @param entity Instance 엔티티
     * @return InstanceResponse DTO
     */
    public static InstanceResponse from(Instance entity) {
        if (entity == null) {
            return null;
        }

        return InstanceResponse.builder()
                .instanceId(entity.getInstanceId())
                .instanceName(entity.getInstanceName())
                .host(entity.getHost())
                .port(entity.getPort())
                .userName(entity.getUserName())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}