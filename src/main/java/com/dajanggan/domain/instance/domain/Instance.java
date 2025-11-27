package com.dajanggan.domain.instance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Instance 엔티티
 *
 * 테이블: monitor_instance
 *
 * 주요 책임:
 * 
 *   PostgreSQL 인스턴스 정보 영속성 관리
 *   MyBatis 매핑 대상
 *   Slack 알림 설정 관리
 *   비즈니스 로직 포함
 * 
 *
 * 설계 원칙:
 * 
 *   Getter/Setter 제공 (MyBatis 호환)
 *   Builder 패턴 지원 (가독성)
 *   @Data 대신 명시적 어노테이션
 *   비즈니스 로직 메서드 포함
 * 
 *
 * 보안:
 * 
 *   secretRef: 암호화된 비밀번호 (내부 전용)
 *   Controller 직접 반환 금지
 *   Service에서 DTO로 변환 시 secretRef 제외
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-10-23  김민서    1. 최초작성자
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instance {

    // ========== 기본 정보 ==========

    private Long instanceId;
    private String instanceName;
    private String host;
    private Integer port;
    private String userName;
    private String secretRef;

    @Builder.Default
    private String sslmode = "disable";

    @Builder.Default
    private String status = "active";

    private String version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // ========== Slack 알림 설정 ==========

    @Builder.Default
    private Boolean slackEnabled = false;

    private String slackWebhookUrl;
    private String slackChannel;
    private String slackMention;

    // ========== 비즈니스 로직 메서드 ==========

    /**
     * 인스턴스 활성화
     */
    public void activate() {
        this.status = "active";
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 인스턴스 비활성화
     */
    public void deactivate() {
        this.status = "inactive";
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 에러 상태로 변경
     */
    public void markAsError() {
        this.status = "error";
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 활성 상태 확인
     *
     * @return 활성 상태이면 true
     */
    public boolean isActive() {
        return "active".equals(this.status);
    }

    /**
     * PostgreSQL 버전 업데이트
     *
     * @param version 새로운 버전
     */
    public void updateVersion(String version) {
        this.version = version;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Slack 알림 활성화
     *
     * @param webhookUrl Webhook URL
     * @param channel 채널명
     * @param mention 멘션 대상
     */
    public void enableSlack(String webhookUrl, String channel, String mention) {
        this.slackEnabled = true;
        this.slackWebhookUrl = webhookUrl;
        this.slackChannel = channel;
        this.slackMention = mention;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Slack 알림 비활성화
     */
    public void disableSlack() {
        this.slackEnabled = false;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Slack 알림 설정 여부 확인
     *
     * @return Slack이 활성화되고 Webhook URL이 있으면 true
     */
    public boolean hasSlackConfigured() {
        return Boolean.TRUE.equals(this.slackEnabled)
                && this.slackWebhookUrl != null
                && !this.slackWebhookUrl.trim().isEmpty();
    }

    /**
     * 연결 정보 문자열 반환 (로깅용)
     *
     * 주의: 비밀번호는 포함하지 않음
     *
     * @return 연결 정보 (예: postgres@localhost:5432)
     */
    public String getConnectionInfo() {
        return String.format("%s@%s:%d", userName, host, port);
    }

    /**
     * JDBC URL 생성
     *
     * 주의: 비밀번호는 포함하지 않음
     *
     * @param databaseName 데이터베이스 이름
     * @return JDBC URL
     */
    public String buildJdbcUrl(String databaseName) {
        return String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                host, port, databaseName, sslmode);
    }
}