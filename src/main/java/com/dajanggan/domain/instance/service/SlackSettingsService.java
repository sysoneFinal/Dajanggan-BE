package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.InstanceSlackSettingsResponse;
import com.dajanggan.domain.instance.dto.SlackSettingsRequest;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.exception.ExceptionMessage;
import com.dajanggan.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Slack 설정 관리 서비스
 *
 * 주요 책임:
 * 
 *   인스턴스별 Slack 알림 설정 관리 (CRUD)
 *   Webhook URL, 채널, 멘션 설정
 *   Slack 알림 활성화/비활성화
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-21  김민서    1. 최초작성자
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlackSettingsService {

    private final InstanceRepository instanceRepository;

    /**
     * Slack 설정 업데이트 (인스턴스 이름 기준)
     *
     * @param instanceName 인스턴스 이름
     * @param request Slack 설정 요청 DTO
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     * @throws IllegalStateException 업데이트 실패 시
     */
    @Transactional
    public void updateSlackSettings(String instanceName, SlackSettingsRequest request) {
        log.info("=== Slack 설정 업데이트 시작 (이름 기준) ===");
        log.info("instanceName: {}, enabled: {}", instanceName, request.getEnabled());

        // 인스턴스 존재 여부 확인
        validateInstanceExistsByName(instanceName);

        // Slack 설정 업데이트 실행
        int updatedRows = instanceRepository.updateSlackSettings(
                instanceName,
                getEnabledValue(request),
                request.getWebhookUrl(),
                request.getDefaultChannel(),
                request.getMention()
        );

        validateUpdateResult(updatedRows, "instanceName=" + instanceName);
        log.info("Slack 설정 업데이트 완료: instanceName={}", instanceName);
    }

    /**
     * Slack 설정 업데이트 (인스턴스 ID 기준)
     *
     * @param instanceId 인스턴스 ID
     * @param request Slack 설정 요청 DTO
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     * @throws IllegalStateException 업데이트 실패 시
     */
    @Transactional
    public void updateSlackSettingsById(Long instanceId, SlackSettingsRequest request) {
        log.info("=== Slack 설정 업데이트 시작 (ID 기준) ===");
        log.info("instanceId: {}, enabled: {}", instanceId, request.getEnabled());

        // 인스턴스 존재 여부 확인
        validateInstanceExistsById(instanceId);

        // Slack 설정 업데이트 실행
        int updatedRows = instanceRepository.updateSlackSettingsById(
                instanceId,
                getEnabledValue(request),
                request.getWebhookUrl(),
                request.getDefaultChannel(),
                request.getMention()
        );

        validateUpdateResult(updatedRows, "instanceId=" + instanceId);
        log.info("Slack 설정 업데이트 완료: instanceId={}", instanceId);
    }

    /**
     * Slack 설정 조회 (인스턴스 이름 기준)
     *
     * @param instanceName 인스턴스 이름
     * @return Slack 설정 정보
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     */
    public SlackSettingsRequest getSlackSettings(String instanceName) {
        log.debug("Slack 설정 조회: instanceName={}", instanceName);

        // 인스턴스 ID 조회
        Long instanceId = findInstanceIdByName(instanceName);

        // 인스턴스 엔티티 조회
        Instance instance = findInstanceById(instanceId);

        return toSlackSettingsDto(instance);
    }

    /**
     * Slack 설정 조회 (인스턴스 ID 기준)
     *
     * @param instanceId 인스턴스 ID
     * @return Slack 설정 정보
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     */
    public SlackSettingsRequest getSlackSettingsById(Long instanceId) {
        log.debug("Slack 설정 조회: instanceId={}", instanceId);

        Instance instance = findInstanceById(instanceId);
        return toSlackSettingsDto(instance);
    }

    /**
     * Slack 설정 삭제/초기화 (인스턴스 이름 기준)
     *
     * Slack 관련 모든 설정을 초기 상태로 되돌림
     *
     * @param instanceName 인스턴스 이름
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     * @throws IllegalStateException 삭제 실패 시
     */
    @Transactional
    public void deleteSlackSettings(String instanceName) {
        log.info("=== Slack 설정 삭제 시작 (이름 기준) ===");
        log.info("instanceName: {}", instanceName);

        // 인스턴스 존재 여부 확인
        validateInstanceExistsByName(instanceName);

        // Slack 설정 삭제
        int deletedRows = instanceRepository.deleteSlackSettingsByName(instanceName);

        validateUpdateResult(deletedRows, "instanceName=" + instanceName);
        log.info("Slack 설정 삭제 완료: instanceName={}", instanceName);
    }

    /**
     * Slack 설정 삭제/초기화 (인스턴스 ID 기준)
     *
     * @param instanceId 인스턴스 ID
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     * @throws IllegalStateException 삭제 실패 시
     */
    @Transactional
    public void deleteSlackSettingsById(Long instanceId) {
        log.info("=== Slack 설정 삭제 시작 (ID 기준) ===");
        log.info("instanceId: {}", instanceId);

        // 인스턴스 존재 여부 확인
        validateInstanceExistsById(instanceId);

        // Slack 설정 삭제
        int deletedRows = instanceRepository.deleteSlackSettingsById(instanceId);

        validateUpdateResult(deletedRows, "instanceId=" + instanceId);
        log.info("Slack 설정 삭제 완료: instanceId={}", instanceId);
    }

    /**
     * 모든 인스턴스의 Slack 설정 목록 조회
     *
     * @return 전체 인스턴스의 Slack 설정 목록
     */
    public List<InstanceSlackSettingsResponse> getAllSlackSettings() {
        log.debug("전체 Slack 설정 목록 조회");

        List<Instance> instances = instanceRepository.findAll();

        return instances.stream()
                .map(this::buildSlackSettingsListResponse)
                .toList();
    }

    // ========== Private Helper Methods ==========

    /**
     * 인스턴스 이름으로 ID 조회
     */
    private Long findInstanceIdByName(String instanceName) {
        return instanceRepository.findIdByInstanceName(instanceName)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));
    }

    /**
     * 인스턴스 ID로 엔티티 조회
     */
    private Instance findInstanceById(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));
    }

    /**
     * 인스턴스 존재 여부 검증 (이름 기준)
     */
    private void validateInstanceExistsByName(String instanceName) {
        Optional<Long> instanceIdOpt = instanceRepository.findIdByInstanceName(instanceName);
        if (instanceIdOpt.isEmpty()) {
            throw new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND);
        }
    }

    /**
     * 인스턴스 존재 여부 검증 (ID 기준)
     */
    private void validateInstanceExistsById(Long instanceId) {
        instanceRepository.findById(instanceId)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));
    }

    /**
     * 업데이트 결과 검증
     *
     * @param affectedRows 영향받은 행 수
     * @param identifier 식별자 정보
     * @throws IllegalStateException 업데이트가 정상적으로 이루어지지 않은 경우
     */
    private void validateUpdateResult(int affectedRows, String identifier) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Slack 설정 처리 실패: " + identifier);
        }
    }

    /**
     * enabled 값 추출 (null 안전)
     *
     * @param request Slack 설정 요청
     * @return enabled 값 (null인 경우 false)
     */
    private Boolean getEnabledValue(SlackSettingsRequest request) {
        return request.getEnabled() != null ? request.getEnabled() : false;
    }

    /**
     * Instance 엔티티로부터 SlackSettingsRequest DTO 생성
     */
    private SlackSettingsRequest toSlackSettingsDto(Instance instance) {
        return SlackSettingsRequest.builder()
                .enabled(instance.getSlackEnabled())
                .webhookUrl(instance.getSlackWebhookUrl())
                .defaultChannel(instance.getSlackChannel())
                .mention(instance.getSlackMention())
                .build();
    }

    /**
     * Instance 엔티티로부터 InstanceSlackSettingsResponse DTO 생성
     */
    private InstanceSlackSettingsResponse buildSlackSettingsListResponse(Instance instance) {
        return InstanceSlackSettingsResponse.builder()
                .instanceId(instance.getInstanceId())
                .instanceName(instance.getInstanceName())
                .slackEnabled(instance.getSlackEnabled())
                .slackWebhookUrl(instance.getSlackWebhookUrl())
                .slackChannel(instance.getSlackChannel())
                .slackMention(instance.getSlackMention())
                .build();
    }
}