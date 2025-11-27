package com.dajanggan.domain.instance.dto;

import com.dajanggan.domain.instance.domain.Instance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인스턴스 생성 요청 DTO
 *
 * 사용처:
 * 
 *   POST /api/instances - 인스턴스 생성
 *   POST /api/instances/test-connection - 연결 테스트
 * 
 *
 * 검증 규칙:
 * 
 *   instanceName: 필수, 공백 불가
 *   host: 필수, 공백 불가
 *   port: 필수, 1~65535 범위
 *   userName: 필수, 공백 불가
 *   secretRef: 필수, 공백 불가 (비밀번호)
 *   sslmode: 선택, 기본값 "disable"
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
public class InstanceCreateRequest {

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


    @NotBlank(message = "비밀번호는 필수입니다.")
    private String secretRef;


    private String sslmode;

    /**
     * DTO를 Entity로 변환
     *
     * 주의: 비밀번호는 암호화되지 않은 상태로 반환됨.
     * Service 레이어에서 암호화 처리 필요
     *
     * @return Instance 엔티티 (비밀번호 미암호화)
     */
    public Instance toEntity() {
        return Instance.builder()
                .instanceName(this.instanceName)
                .host(this.host)
                .port(this.port)
                .userName(this.userName)
                .secretRef(this.secretRef)  // 평문 (Service에서 암호화)
                .sslmode(getSslModeOrDefault())
                .build();
    }

    /**
     * SSL 모드 기본값 처리
     *
     * @return SSL 모드 (null이면 "disable")
     */
    private String getSslModeOrDefault() {
        return (sslmode != null && !sslmode.trim().isEmpty())
                ? sslmode.trim()
                : "disable";
    }
}