package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.domain.QuerySuggestion;
import com.dajanggan.domain.query.dto.ExplainAnalyzeResult;
import com.dajanggan.domain.query.repository.QuerySuggestionRepository;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 기반 쿼리 분석 서비스 (캐싱 기능 추가)
 *
 * @author 이해든
 */
@Service
@Slf4j
public class QueryAIAnalysisService {

    private final OpenAiService openAiService;
    private final String model;
    private final QuerySuggestionRepository suggestionRepository;

    // 캐시 유효 기간 (일 단위)
    private static final int CACHE_VALIDITY_DAYS = 7;

    @Autowired
    public QueryAIAnalysisService(OpenAiService openAiService,
                                  @Qualifier("openAiModel") String model,
                                  QuerySuggestionRepository suggestionRepository) {
        this.openAiService = openAiService;
        this.model = model;
        this.suggestionRepository = suggestionRepository;
    }

    /**
     * 쿼리 분석 (캐싱 적용)
     * 1. 캐시 확인
     * 2. 캐시 히트 -> 반환
     * 3. 캐시 미스 -> AI 호출 -> 저장
     */
    public List<QuerySuggestion> analyzeQuery(Long databaseId, String sql,
                                              ExplainAnalyzeResult explainResult) {
        log.info("=== AI 쿼리 분석 시작 ===");
        log.info("Database ID: {}", databaseId);
        log.info("SQL 길이: {} chars", sql.length());

        // 1. 쿼리 해시 생성
        String queryHash = generateQueryHash(normalizeQuery(sql));
        log.info("Query Hash: {}", queryHash);

        // 2. 캐시 확인
        List<QuerySuggestion> cachedSuggestions = suggestionRepository
                .findLatestByDatabaseIdAndQueryHash(databaseId, queryHash);

        if (!cachedSuggestions.isEmpty()) {
            QuerySuggestion firstSuggestion = cachedSuggestions.get(0);
            ZonedDateTime cacheCreatedAt = firstSuggestion.getCreatedAt();
            ZonedDateTime expiryDate = cacheCreatedAt.plusDays(CACHE_VALIDITY_DAYS);

            // 캐시가 유효한지 확인
            if (ZonedDateTime.now().isBefore(expiryDate)) {
                log.info("✅ 캐시 히트! {} 개의 제안 반환 (생성일: {})",
                        cachedSuggestions.size(), cacheCreatedAt);
                log.info("💰 OpenAI API 호출 생략 - 비용 절감!");

                // 캐시 플래그 설정
                cachedSuggestions.forEach(s -> s.setIsFromCache(true));
                return cachedSuggestions;
            } else {
                log.info("⏰ 캐시 만료됨 ({}일 경과). 새로 분석합니다.", CACHE_VALIDITY_DAYS);
            }
        } else {
            log.info("❌ 캐시 미스. AI 분석을 시작합니다.");
        }

        // 3. 캐시 미스 -> AI 호출
        try {
            String prompt = buildPrompt(sql, explainResult);
            log.info("Prompt 생성 완료, OpenAI 호출 시작...");

            String aiResponse = callOpenAI(prompt);
            log.info("OpenAI 응답 수신 완료 (길이: {} chars)", aiResponse.length());

            List<QuerySuggestion> suggestions = parseSuggestions(
                    databaseId, sql, queryHash, aiResponse, explainResult);
            log.info("AI 제안 파싱 완료: {} 개", suggestions.size());

            // 제안이 비어있으면 fallback 사용
            if (suggestions.isEmpty()) {
                log.warn("AI 제안이 비어있음. Fallback 사용.");
                suggestions = createFallbackSuggestions(databaseId, sql, queryHash, explainResult);
            }

            // AI 모델 정보 설정
            suggestions.forEach(s -> {
                s.setAiModel(model);
                s.setIsFromCache(false);
                s.onCreate();
            });

            log.info("DB 저장 시작: {} 개 제안", suggestions.size());
            suggestionRepository.insertAll(suggestions);
            log.info("✅ DB 저장 완료 (캐싱됨)");
            log.info("=== AI 쿼리 분석 완료 ===");

            return suggestions;

        } catch (Exception e) {
            log.error("❌ AI 분석 실패: {}", e.getMessage(), e);
            log.info("Fallback 제안 생성 중...");
            List<QuerySuggestion> fallback = createFallbackSuggestions(
                    databaseId, sql, queryHash, explainResult);
            log.info("Fallback 제안 {} 개 생성됨", fallback.size());
            return fallback;
        }
    }

    /**
     * 쿼리 정규화 (캐싱 효율 향상)
     * - 공백, 대소문자 통일
     * - 리터럴 값을 플레이스홀더로 치환
     */
    private String normalizeQuery(String sql) {
        return sql.trim()
                .replaceAll("\\s+", " ")          // 연속 공백 제거
                .toUpperCase()                     // 대문자 통일
                .replaceAll("'[^']*'", "?")       // 문자열 리터럴 -> ?
                .replaceAll("\\b\\d+\\b", "?");   // 숫자 리터럴 -> ?
    }

    private String buildPrompt(String sql, ExplainAnalyzeResult explainResult) {
        Double executionTime = explainResult.getExecutionTimeMs();
        Double planningTime = explainResult.getPlanningTimeMs();

        return String.format("""
            당신은 PostgreSQL 성능 최적화 전문가입니다.
            다음 쿼리와 실행 계획을 분석하고 개선 방안을 제시해주세요.
            
            [SQL 쿼리]
            %s
            
            [실행 계획]
            %s
            
            [실행 통계]
            - 실행 시간: %.2fms
            - 계획 시간: %.2fms
            
            다음 형식으로 최대 3개의 개선 제안을 해주세요:
            
            제안1:
            수준: [높음/경고/정보]
            유형: [인덱스 추가/쿼리 재작성/파티셔닝 검토]
            제목: [간단한 제목]
            설명: [상세 설명]
            개선 SQL: [구체적인 SQL 문]
            예상 개선율: [숫자만, 예: 93]
            
            제안2:
            ...
            """,
                sql,
                explainResult.getExplainPlan(),
                executionTime != null ? executionTime : 0.0,
                planningTime != null ? planningTime : 0.0
        );
    }

    private String callOpenAI(String prompt) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(List.of(
                        new ChatMessage("system", "당신은 PostgreSQL 성능 최적화 전문가입니다."),
                        new ChatMessage("user", prompt)
                ))
                .temperature(0.3)
                .maxTokens(2000)
                .build();

        ChatCompletionResult result = openAiService.createChatCompletion(request);
        return result.getChoices().get(0).getMessage().getContent();
    }

    private List<QuerySuggestion> parseSuggestions(Long databaseId, String sql, String queryHash,
                                                   String aiResponse,
                                                   ExplainAnalyzeResult explainResult) {
        List<QuerySuggestion> suggestions = new ArrayList<>();

        // AI 응답 파싱
        String[] sections = aiResponse.split("제안\\d+:");

        for (int i = 1; i < sections.length && i <= 3; i++) {
            String section = sections[i];
            QuerySuggestion suggestion = new QuerySuggestion();

            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);

            // 정규식으로 각 필드 추출
            suggestion.setSuggestionLevel(extractValue(section, "수준:\\s*\\[([^\\]]+)\\]"));
            suggestion.setSuggestionType(extractValue(section, "유형:\\s*\\[([^\\]]+)\\]"));
            suggestion.setSuggestionTitle(extractValue(section, "제목:\\s*\\[([^\\]]+)\\]"));
            suggestion.setSuggestionDescription(extractValue(section, "설명:\\s*\\[([^\\]]+)\\]"));
            suggestion.setSuggestionSql(extractValue(section, "개선 SQL:\\s*\\[([^\\]]+)\\]"));

            String improvementStr = extractValue(section, "예상 개선율:\\s*\\[([^\\]]+)\\]");
            if (improvementStr != null && !improvementStr.isEmpty()) {
                try {
                    suggestion.setExpectedImprovementPercent(
                            new BigDecimal(improvementStr.replaceAll("[^0-9.]", "")));
                } catch (NumberFormatException e) {
                    suggestion.setExpectedImprovementPercent(BigDecimal.ZERO);
                }
            }

            suggestions.add(suggestion);
        }

        return suggestions;
    }

    private String extractValue(String text, String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private String generateQueryHash(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sql.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(sql.hashCode());
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private List<QuerySuggestion> createFallbackSuggestions(Long databaseId,
                                                            String sql,
                                                            String queryHash,
                                                            ExplainAnalyzeResult explainResult) {
        List<QuerySuggestion> fallback = new ArrayList<>();
        String explainPlan = explainResult.getExplainPlan();

        log.info("Fallback 제안 생성 중... (explainPlan 길이: {})", explainPlan.length());

        // 1. Seq Scan 체크
        if (explainPlan.contains("Seq Scan") || explainPlan.contains("Sequential Scan")) {
            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);
            suggestion.setSuggestionLevel("높음");
            suggestion.setSuggestionType("인덱스 추가");
            suggestion.setSuggestionTitle("Seq Scan 발견 - 인덱스 추가 권장");
            suggestion.setSuggestionDescription(
                    "전체 테이블 스캔(Sequential Scan)이 발생하고 있습니다. WHERE 절의 조건 컬럼에 인덱스를 추가하면 성능이 크게 개선될 수 있습니다.");
            suggestion.setExpectedImprovementPercent(new BigDecimal("70"));
            fallback.add(suggestion);
            log.info("✅ Seq Scan 제안 추가");
        }

        // 2. Sort 체크
        if (explainPlan.contains("Sort")) {
            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);
            suggestion.setSuggestionLevel("경고");
            suggestion.setSuggestionType("인덱스 추가");
            suggestion.setSuggestionTitle("정렬 작업 발견 - ORDER BY 인덱스 검토");
            suggestion.setSuggestionDescription(
                    "메모리에서 정렬 작업이 수행되고 있습니다. ORDER BY 절의 컬럼에 인덱스를 추가하면 정렬 비용을 줄일 수 있습니다.");
            suggestion.setExpectedImprovementPercent(new BigDecimal("40"));
            fallback.add(suggestion);
            log.info("✅ Sort 제안 추가");
        }

        // 3. 실행 시간이 느린 경우
        Double executionTime = explainResult.getExecutionTimeMs();
        if (executionTime != null && executionTime > 100) {
            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);
            suggestion.setSuggestionLevel("정보");
            suggestion.setSuggestionType("쿼리 최적화");
            suggestion.setSuggestionTitle("쿼리 실행 시간 개선 권장");
            suggestion.setSuggestionDescription(
                    String.format("현재 쿼리 실행 시간이 %.2fms입니다. 불필요한 컬럼 조회를 제거하거나 WHERE 조건을 추가하여 성능을 개선할 수 있습니다.", executionTime));
            suggestion.setExpectedImprovementPercent(new BigDecimal("30"));
            fallback.add(suggestion);
            log.info("✅ 실행 시간 제안 추가");
        }

        // 4. 아무 제안도 없으면 기본 제안
        if (fallback.isEmpty()) {
            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);
            suggestion.setSuggestionLevel("정보");
            suggestion.setSuggestionType("일반 조언");
            suggestion.setSuggestionTitle("쿼리가 효율적으로 실행되고 있습니다");
            suggestion.setSuggestionDescription(
                    "현재 쿼리는 인덱스를 적절히 활용하고 있습니다. 데이터가 증가하면 성능을 재검토하는 것이 좋습니다.");
            suggestion.setExpectedImprovementPercent(new BigDecimal("10"));
            fallback.add(suggestion);
            log.info("✅ 기본 제안 추가");
        }

        log.info("총 {} 개의 Fallback 제안 생성됨", fallback.size());
        return fallback;
    }
}