package com.dajanggan.global.config;

import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 설정
 *
 * @author 이해든
 */
@Slf4j
@Configuration
public class OpenAIConfig {

    @Value("${yamlopenai.api-key:}")
    private String apiKey;

    @Value("${yamlopenai.model:gpt-4o-mini}")
    private String model;

    /**
     * OpenAI 서비스 Bean
     * API 키가 설정되어 있을 때만 생성
     */
    @Bean
    @ConditionalOnProperty(name = "yamlopenai.api-key")
    public OpenAiService openAiService() {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            log.error("❌ OpenAI API 키가 올바르게 설정되지 않았습니다.");
            throw new IllegalStateException("OpenAI API key is not configured properly");
        }

        try {
            log.info("✅ OpenAI 서비스 초기화 완료");
            return new OpenAiService(apiKey);
        } catch (Exception e) {
            log.error("❌ OpenAI 서비스 초기화 실패: {}", e.getMessage());
            throw e;
        }
    }

    @Bean
    public String openAiModel() {
        return model;
    }
}