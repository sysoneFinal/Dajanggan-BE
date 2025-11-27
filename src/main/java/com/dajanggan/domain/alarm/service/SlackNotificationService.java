package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.instance.service.SlackSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Slack 알림 서비스
 *
 * 주요 책임:
 * - Slack Webhook 전송
 * - 인스턴스별 Slack 설정 관리
 * - 알림 포맷팅
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-20  김민서    1. 최초작성
 *
 */
@Slf4j
@Service
public class SlackNotificationService {

    private final WebClient webClient;
    private final SlackSettingsService slackSettingsService;

    @Value("${slack.webhook.url:}")
    private String defaultWebhookUrl;

    @Value("${slack.enabled:false}")
    private boolean defaultSlackEnabled;

    public SlackNotificationService(
            WebClient.Builder webClientBuilder,
            SlackSettingsService slackSettingsService
    ) {
        this.webClient = webClientBuilder.build();
        this.slackSettingsService = slackSettingsService;
    }

    /**
     * 인스턴스 ID로 Slack 알림 전송
     */
    public void sendAlarmNotification(
            Long instanceId,
            String title,
            String severity,
            String description,
            String instanceName,
            String databaseName
    ) {
        log.info("Slack 알림 전송 시도: instanceId={}, title={}", instanceId, title);

        // 인스턴스별 Slack 설정 조회
        var slackSettings = slackSettingsService.getSlackSettingsById(instanceId);
        if (slackSettings == null) {
            log.warn("Slack 설정을 찾을 수 없습니다: instanceId={}", instanceId);
            return;
        }

        // Slack 설정 확인
        Boolean enabled = slackSettings.getEnabled() != null
                ? slackSettings.getEnabled()
                : defaultSlackEnabled;
        String webhookUrl = slackSettings.getWebhookUrl() != null && !slackSettings.getWebhookUrl().isEmpty()
                ? slackSettings.getWebhookUrl()
                : defaultWebhookUrl;
        String channel = slackSettings.getDefaultChannel();
        String mention = slackSettings.getMention();

        if (!enabled) {
            log.warn("Slack 알림이 비활성화되어 있습니다: instanceId={}", instanceId);
            return;
        }

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Slack Webhook URL이 설정되지 않았습니다: instanceId={}", instanceId);
            return;
        }

        log.info("Slack 알림 전송 진행: instanceId={}, channel={}", instanceId, channel);
        sendSlackNotification(webhookUrl, channel, mention, title, severity, description, instanceName, databaseName);
    }

    /**
     * 기본 설정으로 Slack 알림 전송
     */
    public void sendAlarmNotification(
            String title,
            String severity,
            String description,
            String instanceName,
            String databaseName
    ) {
        if (!defaultSlackEnabled) {
            log.debug("Slack 알림이 비활성화되어 있습니다.");
            return;
        }

        if (defaultWebhookUrl == null || defaultWebhookUrl.isEmpty()) {
            log.warn("Slack Webhook URL이 설정되지 않았습니다.");
            return;
        }

        sendSlackNotification(defaultWebhookUrl, null, null, title, severity, description, instanceName, databaseName);
    }

    /**
     * 실제 Slack 알림 전송
     */
    private void sendSlackNotification(
            String webhookUrl,
            String channel,
            String mention,
            String title,
            String severity,
            String description,
            String instanceName,
            String databaseName
    ) {
        try {
            String color = getColorBySeverity(severity);
            String emoji = getEmojiBySeverity(severity);

            // 멘션 포함 텍스트
            StringBuilder textBuilder = new StringBuilder();
            if (mention != null && !mention.isEmpty()) {
                textBuilder.append(mention).append(" ");
            }
            textBuilder.append(description);

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("title", emoji + " " + title);
            attachment.put("text", textBuilder.toString());
            attachment.put("fields", new Object[]{
                    Map.of("title", "심각도", "value", severity, "short", true),
                    Map.of("title", "인스턴스", "value", instanceName, "short", true),
                    Map.of("title", "데이터베이스", "value", databaseName, "short", true)
            });
            attachment.put("footer", "DaJangGan Alarm System");
            attachment.put("ts", System.currentTimeMillis() / 1000);

            Map<String, Object> payload = new HashMap<>();
            payload.put("attachments", new Object[]{attachment});

            if (channel != null && !channel.isEmpty()) {
                payload.put("channel", channel);
            }

            log.info("🔨 Slack Webhook 호출: channel={}, title={}", channel, title);

            webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            response -> log.info("Slack 알림 전송 성공: channel={}", channel),
                            error -> log.error("Slack 알림 전송 실패: channel={}, error={}", channel, error.getMessage())
                    );

        } catch (Exception e) {
            log.error("Slack 알림 전송 중 예외 발생: {}", e.getMessage(), e);
        }
    }

    private String getColorBySeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#dc3545";
            case "WARNING", "WARN" -> "#ffc107";
            case "NOTICE", "INFO" -> "#17a2b8";
            default -> "#6c757d";
        };
    }

    private String getEmojiBySeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "🚨";
            case "WARNING", "WARN" -> "⚠️";
            case "NOTICE", "INFO" -> "ℹ️";
            default -> "📢";
        };
    }
}
