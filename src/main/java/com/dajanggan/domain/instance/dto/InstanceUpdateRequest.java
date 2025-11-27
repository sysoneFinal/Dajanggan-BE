package com.dajanggan.domain.instance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인스턴스 수정 요청 DTO
 *
 * 사용처:
 * 
 *   PUT /api/instances/{id} - 인스턴스 수정
 * 
 *
 * 검증 규칙:
 * 
 *   instanceName: 필수
 *   host: 필수
 *   port: 필수, 1~65535 범위
 *   userName: 필수
 *   secretRef: 선택 (변경 시에만 입력)
 * 
 *
 * 특이사항:
 * 
 *   secretRef가 null이면 비밀번호 변경 안 함
 *   secretRef가 제공되면 비밀번호 변경
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
public class InstanceUpdateRequest {

    @NotBlank(message = "인스턴스 이름은 필수입니다.")
    private String instanceName;

    @NotBlank(message = "호스트는 필수입니다.")
    private String host;

    @NotNull(message = "포트는 필수입니다.")
    @Min(value = 1, message = "포트는 1 이상이어야 합니다.")
    @Max(value = 65535, message = "포트는 65535 이하여야 합니다.")
    private Integer port;

    @NotBlank(message = "사용자명은 필수입니다.")
    private String userName;

    /**
     * 비밀번호 (선택)
     *
     * null이면 비밀번호 변경 안 함
     * 값이 있으면 비밀번호 변경
     */
    private String secretRef;
}