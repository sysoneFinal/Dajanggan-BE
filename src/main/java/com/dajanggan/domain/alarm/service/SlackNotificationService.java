package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class SlackNotificationService {

    private final WebClient webClient;
    private final InstanceRepository instanceRepository;

    @Value("${slack.webhook.url:}")
    private String defaultWebhookUrl;

    @Value("${slack.enabled:false}")
    private boolean defaultSlackEnabled;

    public SlackNotificationService(WebClient.Builder webClientBuilder, InstanceRepository instanceRepository) {
        this.webClient = webClientBuilder.build();
        this.instanceRepository = instanceRepository;
    }

    /**
     * 인스턴스 ID로 Slack 알림 전송
     */
    public void sendAlarmNotification(Long instanceId, String title, String severity,
                                      String description, String instanceName,
                                      String databaseName) {
        log.info("📤 Slack 알림 전송 시도: instanceId={}, instanceName={}, title={}, severity={}", 
                instanceId, instanceName, title, severity);
        
        // 인스턴스별 Slack 설정 조회
        Optional<Instance> instanceOpt = instanceRepository.findById(instanceId);
        if (instanceOpt.isEmpty()) {
            log.warn("❌ 인스턴스를 찾을 수 없습니다: instanceId={}", instanceId);
            return;
        }

        Instance instance = instanceOpt.get();
        
        // 인스턴스별 설정이 있으면 사용, 없으면 기본값 사용
        Boolean enabled = instance.getSlackEnabled() != null 
                ? instance.getSlackEnabled() 
                : defaultSlackEnabled;
        String webhookUrl = instance.getSlackWebhookUrl() != null && !instance.getSlackWebhookUrl().isEmpty()
                ? instance.getSlackWebhookUrl()
                : defaultWebhookUrl;
        String channel = instance.getSlackChannel();
        String mention = instance.getSlackMention();

        log.info("📋 Slack 설정 확인: instanceId={}, enabled={}, webhookUrl={}, channel={}, mention={}", 
                instanceId, enabled, webhookUrl != null ? "설정됨" : "미설정", channel, mention);

        if (!enabled) {
            log.warn("⚠️ Slack 알림이 비활성화되어 있습니다: instanceId={}, instanceName={}", instanceId, instanceName);
            return;
        }

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("❌ Slack Webhook URL이 설정되지 않았습니다: instanceId={}, instanceName={}", instanceId, instanceName);
            return;
        }

        log.info("✅ Slack 알림 전송 진행: instanceId={}, instanceName={}, channel={}", instanceId, instanceName, channel);
        sendSlackNotification(webhookUrl, channel, mention, title, severity, description, instanceName, databaseName);
    }

    /**
     * 기본 설정으로 Slack 알림 전송 (하위 호환성)
     */
    public void sendAlarmNotification(String title, String severity,
                                      String description, String instanceName,
                                      String databaseName) {
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
    private void sendSlackNotification(String webhookUrl, String channel, String mention,
                                       String title, String severity,
                                       String description, String instanceName,
                                       String databaseName) {
        try {
            String color = getColorBySeverity(severity);
            String emoji = getEmojiBySeverity(severity);

            // 채널과 멘션을 포함한 텍스트 구성
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
            
            // 채널이 설정되어 있으면 추가
            if (channel != null && !channel.isEmpty()) {
                payload.put("channel", channel);
            }

            log.info("📨 Slack Webhook 호출: webhookUrl={}, channel={}, title={}", 
                    webhookUrl.substring(0, Math.min(50, webhookUrl.length())) + "...", channel, title);
            
            webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            response -> log.info("✅ Slack 알림 전송 성공: instanceName={}, channel={}, response={}", 
                                    instanceName, channel, response != null ? response.substring(0, Math.min(100, response.length())) : "null"),
                            error -> log.error("❌ Slack 알림 전송 실패: instanceName={}, channel={}, error={}", 
                                    instanceName, channel, error.getMessage(), error)
                    );

        } catch (Exception e) {
            log.error("❌ Slack 알림 전송 중 예외 발생: instanceName={}, error={}", instanceName, e.getMessage(), e);
        }
    }

    private String getColorBySeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#dc3545"; // 빨강
            case "WARNING" -> "#ffc107";  // 노랑
            case "NOTICE" -> "#17a2b8";   // 파랑
            default -> "#6c757d";         // 회색
        };
    }

    private String getEmojiBySeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "🚨";
            case "WARNING" -> "⚠️";
            case "NOTICE" -> "ℹ️";
            default -> "📢";
        };
    }
}