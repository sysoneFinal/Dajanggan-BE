package com.dajanggan.domain.alarm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SlackNotificationService {

    private final WebClient webClient;

    @Value("${slack.webhook.url}")
    private String webhookUrl;

    @Value("${slack.enabled:true}")
    private boolean slackEnabled;

    public SlackNotificationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void sendAlarmNotification(String title, String severity,
                                      String description, String instanceName,
                                      String databaseName) {
        if (!slackEnabled) {
            log.debug("Slack 알림이 비활성화되어 있습니다.");
            return;
        }

        try {
            String color = getColorBySeverity(severity);
            String emoji = getEmojiBySeverity(severity);

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("title", emoji + " " + title);
            attachment.put("text", description);
            attachment.put("fields", new Object[]{
                    Map.of("title", "심각도", "value", severity, "short", true),
                    Map.of("title", "인스턴스", "value", instanceName, "short", true),
                    Map.of("title", "데이터베이스", "value", databaseName, "short", true)
            });
            attachment.put("footer", "DaJangGan Alarm System");
            attachment.put("ts", System.currentTimeMillis() / 1000);

            Map<String, Object> payload = new HashMap<>();
            payload.put("attachments", new Object[]{attachment});

            webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            response -> log.info("Slack 알림 전송 성공"),
                            error -> log.error("Slack 알림 전송 실패: {}", error.getMessage())
                    );

        } catch (Exception e) {
            log.error("Slack 알림 전송 중 오류: {}", e.getMessage(), e);
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