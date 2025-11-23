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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 기반 쿼리 분석 서비스 (캐싱 + 토큰 절감 전처리 기능)
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

    // 토큰 절감 설정
    private static final int MAX_SQL_LENGTH = 500;  // SQL 최대 길이
    private static final int MAX_STRING_LITERAL_LENGTH = 30;  // 문자열 리터럴 최대 길이
    private static final boolean ENABLE_TOKEN_OPTIMIZATION = true;  // 토큰 최적화 활성화

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
            LocalDateTime cacheCreatedAt = firstSuggestion.getCreatedAt();
            LocalDateTime expiryDate = cacheCreatedAt.plusDays(CACHE_VALIDITY_DAYS);

            // 캐시가 유효한지 확인
            if (LocalDateTime.now().isBefore(expiryDate)) {
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
            // 토큰 절감을 위한 전처리
            String preprocessedSql = sql;
            String preprocessedExplain = explainResult.getExplainPlan();

            if (ENABLE_TOKEN_OPTIMIZATION) {
                log.info("🔧 토큰 절감 전처리 시작...");

                int originalSqlLength = sql.length();
                int originalExplainLength = explainResult.getExplainPlan().split("\n").length;

                preprocessedSql = preprocessSqlForAI(sql);
                preprocessedExplain = summarizeExplainPlan(explainResult.getExplainPlan());

                int processedSqlLength = preprocessedSql.length();
                int processedExplainLines = preprocessedExplain.split("\n").length;

                log.info("  📊 SQL 전처리: {} → {} chars ({} 절감)",
                        originalSqlLength, processedSqlLength,
                        String.format("%.1f%%", (1 - (double)processedSqlLength/originalSqlLength) * 100));
                log.info("  📊 EXPLAIN 전처리: {} → {} lines ({} 절감)",
                        originalExplainLength, processedExplainLines,
                        String.format("%.1f%%", (1 - (double)processedExplainLines/originalExplainLength) * 100));
            }

            String prompt = buildOptimizedPrompt(preprocessedSql, preprocessedExplain, explainResult);

            // 프롬프트 토큰 수 추정 (대략 1 토큰 ≈ 4 글자)
            int estimatedTokens = prompt.length() / 4;
            log.info("  💡 예상 프롬프트 토큰: ~{} tokens", estimatedTokens);
            log.info("Prompt 생성 완료, OpenAI 호출 시작...");

            String aiResponse = callOpenAI(prompt);
            log.info("OpenAI 응답 수신 완료 (길이: {} chars)", aiResponse.length());

            // ⭐ OpenAI 원본 응답 로그
            log.debug("========== OpenAI 원본 응답 시작 ==========");
            log.debug(aiResponse);
            log.debug("========== OpenAI 원본 응답 끝 ==========");

            List<QuerySuggestion> suggestions = parseSuggestions(
                    databaseId, sql, queryHash, aiResponse, explainResult);
            log.info("AI 제안 파싱 완료: {} 개", suggestions.size());

            // 제안이 비어있으면 fallback 사용
            if (suggestions.isEmpty()) {
                log.warn("AI 제안이 비어있음. Fallback 사용.");
                suggestions = createFallbackSuggestions(databaseId, sql, queryHash, explainResult);
            }

            // AI 모델 정보 설정 (토큰 수 추정)
            int estimatedTokensUsed = (prompt.length() + aiResponse.length()) / 4;
            suggestions.forEach(s -> {
                s.setAiModel(model);
                s.setTokenUsed(estimatedTokensUsed);
                s.setIsFromCache(false);
                s.onCreate(); // createdAt 자동 설정

                // 필수 필드 검증
                if (s.getDatabaseId() == null) {
                    throw new IllegalStateException("databaseId는 필수입니다. suggestion: " + s.getSuggestionTitle());
                }
                if (s.getQueryHash() == null || s.getQueryHash().isEmpty()) {
                    throw new IllegalStateException("queryHash는 필수입니다. suggestion: " + s.getSuggestionTitle());
                }
                if (s.getCreatedAt() == null) {
                    log.warn("createdAt이 NULL입니다. 수동 설정합니다.");
                    s.setCreatedAt(LocalDateTime.now());
                }
            });

            log.info("DB 저장 시작: {} 개 제안 (사용된 토큰: ~{})", suggestions.size(), estimatedTokensUsed);

            // 첫 번째 제안 샘플 로그
            if (!suggestions.isEmpty()) {
                QuerySuggestion first = suggestions.get(0);
                log.info("첫 번째 제안 샘플:");
                log.info("  - databaseId: {}", first.getDatabaseId());
                log.info("  - queryHash: {}", first.getQueryHash());
                log.info("  - createdAt: {}", first.getCreatedAt());
                log.info("  - level: {}", first.getSuggestionLevel());
                log.info("  - type: {}", first.getSuggestionType());
                log.info("  - title: {}", first.getSuggestionTitle());
                log.info("  - improvement: {}%", first.getExpectedImprovementPercent());
            }

            try {
                suggestionRepository.insertAll(suggestions);
                log.info("✅ DB 저장 완료 (캐싱됨)");
            } catch (Exception dbException) {
                log.error("❌❌❌ DB 저장 실패 ❌❌❌");
                log.error("에러 메시지: {}", dbException.getMessage());
                log.error("에러 클래스: {}", dbException.getClass().getName());
                log.error("저장 시도한 제안 개수: {}", suggestions.size());

                // 첫 번째 제안의 상세 정보 로깅
                if (!suggestions.isEmpty()) {
                    QuerySuggestion first = suggestions.get(0);
                    log.error("첫 번째 제안 상세 정보:");
                    log.error("  - databaseId: {}", first.getDatabaseId());
                    log.error("  - queryHash: {}", first.getQueryHash());
                    log.error("  - createdAt: {}", first.getCreatedAt());
                    log.error("  - hasTuningSuggestion: {}", first.getHasTuningSuggestion());
                    log.error("  - suggestionLevel: {}", first.getSuggestionLevel());
                    log.error("  - suggestionType: {}", first.getSuggestionType());
                    log.error("  - suggestionTitle: {}", first.getSuggestionTitle());
                    log.error("  - aiModel: {}", first.getAiModel());
                    log.error("  - tokenUsed: {}", first.getTokenUsed());
                    log.error("  - isFromCache: {}", first.getIsFromCache());
                }

                log.error("전체 스택 트레이스:", dbException);
                throw new RuntimeException("쿼리 제안 저장 실패: " + dbException.getMessage(), dbException);
            }

            log.info("=== AI 쿼리 분석 완료 ===");
            return suggestions;

        } catch (Exception e) {
            log.error("❌ AI 분석 실패: {}", e.getMessage(), e);
            log.info("Fallback 제안 생성 중...");
            List<QuerySuggestion> fallback = createFallbackSuggestions(
                    databaseId, sql, queryHash, explainResult);

            // Fallback도 저장 (차후 캐싱 사용)
            fallback.forEach(s -> {
                s.setAiModel("fallback");
                s.setTokenUsed(0);
                s.setIsFromCache(false);
                s.onCreate();

                // 필수 필드 검증
                if (s.getCreatedAt() == null) {
                    s.setCreatedAt(LocalDateTime.now());
                }
            });

            try {
                log.info("Fallback 제안 DB 저장 시작: {} 개", fallback.size());
                suggestionRepository.insertAll(fallback);
                log.info("✅ Fallback 제안 DB 저장 완료: {} 개", fallback.size());
            } catch (Exception dbError) {
                log.error("❌ Fallback 저장 실패: {}", dbError.getMessage(), dbError);
                log.error("Fallback 제안 개수: {}", fallback.size());
                if (!fallback.isEmpty()) {
                    QuerySuggestion first = fallback.get(0);
                    log.error("Fallback 첫 번째 제안: databaseId={}, queryHash={}, createdAt={}",
                            first.getDatabaseId(), first.getQueryHash(), first.getCreatedAt());
                }
            }

            log.info("Fallback 제안 {} 개 생성됨", fallback.size());
            return fallback;
        }
    }

    /**
     * SQL 전처리 - 토큰 절감
     */
    private String preprocessSqlForAI(String sql) {
        String processed = sql
                .replaceAll("--[^\n]*", "")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("\\s+", " ")
                .replaceAll("'([^']{" + MAX_STRING_LITERAL_LENGTH + "})[^']*'", "'$1...'")
                .trim();

        if (processed.length() > MAX_SQL_LENGTH) {
            processed = processed.substring(0, MAX_SQL_LENGTH) + "... [truncated]";
        }

        return processed;
    }

    /**
     * EXPLAIN 결과 요약 - 토큰 절감
     */
    private String summarizeExplainPlan(String plan) {
        if (plan == null || plan.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        String[] lines = plan.split("\n");

        for (String line : lines) {
            if (line.contains("Seq Scan") ||
                    line.contains("Index Scan") ||
                    line.contains("Bitmap") ||
                    line.contains("Hash Join") ||
                    line.contains("Nested Loop") ||
                    line.contains("Sort") ||
                    line.contains("cost=") ||
                    line.contains("rows=") ||
                    line.contains("Execution Time") ||
                    line.contains("Planning Time")) {

                summary.append(line.trim()).append("\n");
            }
        }

        return summary.toString().isEmpty() ? plan : summary.toString();
    }

    /**
     * 쿼리 정규화
     */
    private String normalizeQuery(String sql) {
        return sql
                .replaceAll("\\s+", " ")
                .replaceAll("'[^']*'", "?")
                .replaceAll("\\d+", "?")
                .trim()
                .toLowerCase();
    }

    /**
     * 최적화된 프롬프트 생성
     */
    private String buildOptimizedPrompt(String sql, String explainPlan, ExplainAnalyzeResult result) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("당신은 PostgreSQL 쿼리 최적화 전문가입니다.\n\n");
        prompt.append("다음 쿼리를 분석하고 최적화 제안을 제공해주세요.\n\n");

        // SQL
        prompt.append("## SQL 쿼리\n");
        prompt.append("```sql\n");
        prompt.append(sql);
        prompt.append("\n```\n\n");

        // EXPLAIN 결과
        if (explainPlan != null && !explainPlan.isEmpty()) {
            prompt.append("## EXPLAIN 결과\n");
            prompt.append("```\n");
            prompt.append(explainPlan);
            prompt.append("\n```\n\n");
        }

        // 실행 시간 정보
        if (result.getExecutionTimeMs() != null) {
            prompt.append(String.format("실행 시간: %.2f ms\n\n", result.getExecutionTimeMs()));
        }

        // 응답 형식
        prompt.append("## ⚠️ 필수 응답 형식 ⚠️\n");
        prompt.append("반드시 아래 형식을 정확히 따라주세요. 각 제안은 대괄호 안에 값을 넣어야 합니다:\n\n");
        prompt.append("[제안 1]\n");
        prompt.append("레벨: [높음/경고/정보 중 하나]\n");
        prompt.append("유형: [인덱스 추가/쿼리 구조 개선/성능 최적화 중 하나]\n");
        prompt.append("제목: [20자 이내 짧은 제목]\n");
        prompt.append("설명: [50자 이상 상세 설명]\n");
        prompt.append("개선 SQL: [실행 가능한 SQL 또는 DDL]\n");
        prompt.append("예상 개선율: [0-100 사이 숫자만]\n\n");

        prompt.append("⚠️ 주의사항:\n");
        prompt.append("- 반드시 대괄호 [ ] 안에 값을 넣으세요\n");
        prompt.append("- 각 항목은 한 줄에 작성하세요\n");
        prompt.append("- 최대 3개 제안만 작성하세요\n");
        prompt.append("- 다른 설명은 넣지 마세요\n");

        return prompt.toString();
    }

    /**
     * OpenAI 호출
     */
    private String callOpenAI(String prompt) {
        ChatMessage systemMessage = new ChatMessage("system",
                "당신은 PostgreSQL 쿼리 최적화 전문가입니다.");
        ChatMessage userMessage = new ChatMessage("user", prompt);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(List.of(systemMessage, userMessage))
                .temperature(0.3)
                .maxTokens(1500)
                .build();

        ChatCompletionResult completion = openAiService.createChatCompletion(request);
        return completion.getChoices().get(0).getMessage().getContent();
    }

    /**
     * AI 응답 파싱 (강화된 버전)
     */
    private List<QuerySuggestion> parseSuggestions(Long databaseId, String sql, String queryHash,
                                                   String aiResponse, ExplainAnalyzeResult explainResult) {
        List<QuerySuggestion> suggestions = new ArrayList<>();

        // [제안 1], [제안 2] 등으로 분리
        Pattern sectionPattern = Pattern.compile("\\[제안 \\d+\\]([\\s\\S]*?)(?=\\[제안 \\d+\\]|$)");
        Matcher sectionMatcher = sectionPattern.matcher(aiResponse);

        int count = 0;
        while (sectionMatcher.find()) {
            count++;
            String section = sectionMatcher.group(1).trim();

            // ⭐ 파싱 디버그 로그
            log.debug("========== 제안 {} 파싱 시작 ==========", count);
            log.debug(section);
            log.debug("========================================");

            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);

            String level = extractValue(section, "레벨:\\s*\\[([^\\]]+)\\]");
            String type = extractValue(section, "유형:\\s*\\[([^\\]]+)\\]");
            String title = extractValue(section, "제목:\\s*\\[([^\\]]+)\\]");
            String desc = extractValue(section, "설명:\\s*\\[([^\\]]+)\\]");
            String sqlText = extractValue(section, "개선 SQL:\\s*\\[([^\\]]+)\\]");
            String improvementStr = extractValue(section, "예상 개선율:\\s*\\[([^\\]]+)\\]");

            // ⭐ 파싱 결과 로그
            log.debug("파싱 결과: level={}, type={}, title={}", level, type, title);

            // ⭐ 빈 값 체크
            if (level.isEmpty() || type.isEmpty() || title.isEmpty()) {
                log.warn("❌ 제안 {} 파싱 실패: 필수 필드가 비어있음", count);
                log.warn("   level='{}', type='{}', title='{}'", level, type, title);
                continue;
            }

            suggestion.setSuggestionLevel(level);
            suggestion.setSuggestionType(type);
            suggestion.setSuggestionTitle(title);
            suggestion.setSuggestionDescription(desc);
            suggestion.setSuggestionSql(sqlText);

            if (!improvementStr.isEmpty()) {
                try {
                    suggestion.setExpectedImprovementPercent(
                            new BigDecimal(improvementStr.replaceAll("[^0-9.]", "")));
                } catch (NumberFormatException e) {
                    log.warn("개선율 파싱 실패: {}", improvementStr);
                    suggestion.setExpectedImprovementPercent(BigDecimal.ZERO);
                }
            } else {
                suggestion.setExpectedImprovementPercent(BigDecimal.ZERO);
            }

            suggestions.add(suggestion);
        }

        log.info("✅ 총 {} 개 제안 파싱 완료 (성공: {})", count, suggestions.size());
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

    /**
     * Fallback 제안 생성
     */
    private List<QuerySuggestion> createFallbackSuggestions(Long databaseId,
                                                            String sql,
                                                            String queryHash,
                                                            ExplainAnalyzeResult explainResult) {
        List<QuerySuggestion> fallback = new ArrayList<>();
        String explainPlan = explainResult.getExplainPlan();
        String upperSql = sql.toUpperCase();

        log.info("Fallback 제안 생성 중...");
        log.info("  - SQL 길이: {}", sql.length());
        log.info("  - EXPLAIN 결과 길이: {}", explainPlan != null ? explainPlan.length() : 0);

        // EXPLAIN 결과 기반 분석
        boolean hasExplainResult = explainPlan != null && !explainPlan.isEmpty() &&
                !explainPlan.contains("분석할 수 없습니다") &&
                !explainPlan.contains("구조 분석");

        if (hasExplainResult) {
            // 1. Seq Scan 체크
            if (explainPlan.contains("Seq Scan") || explainPlan.contains("Sequential Scan")) {
                QuerySuggestion suggestion = new QuerySuggestion();
                suggestion.setDatabaseId(databaseId);
                suggestion.setQueryHash(queryHash);
                suggestion.setHasTuningSuggestion(true);
                suggestion.setSuggestionLevel("높음");
                suggestion.setSuggestionType("인덱스 추가");
                suggestion.setSuggestionTitle("Seq Scan 발견");
                suggestion.setSuggestionDescription(
                        "전체 테이블 스캔이 발생하고 있습니다. WHERE 절의 조건 컬럼에 인덱스를 추가하면 성능이 크게 개선될 수 있습니다.");
                suggestion.setExpectedImprovementPercent(new BigDecimal("70"));
                fallback.add(suggestion);
                log.info("  ✅ Seq Scan 제안 추가");
            }

            // 2. Sort 체크
            if (explainPlan.contains("Sort")) {
                QuerySuggestion suggestion = new QuerySuggestion();
                suggestion.setDatabaseId(databaseId);
                suggestion.setQueryHash(queryHash);
                suggestion.setHasTuningSuggestion(true);
                suggestion.setSuggestionLevel("경고");
                suggestion.setSuggestionType("인덱스 추가");
                suggestion.setSuggestionTitle("정렬 작업 발견");
                suggestion.setSuggestionDescription(
                        "메모리에서 정렬 작업이 수행되고 있습니다. ORDER BY 절의 컬럼에 인덱스를 추가하면 정렬 비용을 줄일 수 있습니다.");
                suggestion.setExpectedImprovementPercent(new BigDecimal("40"));
                fallback.add(suggestion);
                log.info("  ✅ Sort 제안 추가");
            }

            // 3. 실행 시간 기반 제안
            Double executionTime = explainResult.getExecutionTimeMs();
            if (executionTime != null && executionTime > 10) {
                QuerySuggestion suggestion = new QuerySuggestion();
                suggestion.setDatabaseId(databaseId);
                suggestion.setQueryHash(queryHash);
                suggestion.setHasTuningSuggestion(true);

                if (executionTime > 1000) {
                    suggestion.setSuggestionLevel("높음");
                    suggestion.setSuggestionType("성능 최적화");
                    suggestion.setSuggestionTitle("슬로우 쿼리 감지");
                    suggestion.setSuggestionDescription(
                            String.format("현재 쿼리 실행 시간이 %.2fms로 매우 느립니다. 인덱스 추가, 불필요한 컬럼 제거 등을 통해 성능을 개선해야 합니다.", executionTime));
                    suggestion.setExpectedImprovementPercent(new BigDecimal("60"));
                } else if (executionTime > 500) {
                    suggestion.setSuggestionLevel("경고");
                    suggestion.setSuggestionType("성능 개선");
                    suggestion.setSuggestionTitle("쿼리 실행 시간 개선 필요");
                    suggestion.setSuggestionDescription(
                            String.format("현재 쿼리 실행 시간이 %.2fms입니다. 불필요한 컬럼 조회를 제거하거나 WHERE 조건을 추가하여 성능을 개선할 수 있습니다.", executionTime));
                    suggestion.setExpectedImprovementPercent(new BigDecimal("40"));
                } else {
                    suggestion.setSuggestionLevel("정보");
                    suggestion.setSuggestionType("쿼리 최적화");
                    suggestion.setSuggestionTitle("쿼리 성능 모니터링");
                    suggestion.setSuggestionDescription(
                            String.format("현재 쿼리 실행 시간이 %.2fms입니다. 데이터가 증가하면 성능이 저하될 수 있으니 모니터링이 필요합니다.", executionTime));
                    suggestion.setExpectedImprovementPercent(new BigDecimal("20"));
                }

                fallback.add(suggestion);
                log.info("  ✅ 실행 시간 제안 추가 ({}ms)", executionTime);
            }
        }

        // SQL 쿼리 자체 분석
        log.info("  🔍 SQL 구문 분석 시작...");

        // 1. SELECT * 사용 체크
        if (upperSql.contains("SELECT *") || upperSql.matches(".*SELECT\\s+\\*\\s+FROM.*")) {
            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);
            suggestion.setSuggestionLevel("경고");
            suggestion.setSuggestionType("쿼리 구조 개선");
            suggestion.setSuggestionTitle("SELECT * 사용 감지");
            suggestion.setSuggestionDescription(
                    "SELECT *는 모든 컬럼을 조회하여 불필요한 데이터 전송과 메모리 사용을 유발합니다. 실제로 필요한 컬럼만 명시적으로 선택하면 성능이 개선됩니다.");
            suggestion.setSuggestionSql("SELECT column1, column2 FROM table_name WHERE ...");
            suggestion.setExpectedImprovementPercent(new BigDecimal("30"));
            fallback.add(suggestion);
            log.info("  ✅ SELECT * 제안 추가");
        }

        // 2. WHERE 절 없는 SELECT 체크
        if (upperSql.startsWith("SELECT") && !upperSql.contains("WHERE") && !upperSql.contains("LIMIT")) {
            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(true);
            suggestion.setSuggestionLevel("높음");
            suggestion.setSuggestionType("쿼리 구조 개선");
            suggestion.setSuggestionTitle("WHERE 절 없는 쿼리");
            suggestion.setSuggestionDescription(
                    "WHERE 절이 없어 전체 데이터를 조회하고 있습니다. 테이블 크기가 클 경우 심각한 성능 저하가 발생할 수 있습니다.");
            suggestion.setSuggestionSql("SELECT ... FROM table_name WHERE condition LIMIT 1000");
            suggestion.setExpectedImprovementPercent(new BigDecimal("80"));
            fallback.add(suggestion);
            log.info("  ✅ WHERE 절 없음 제안 추가");
        }

        // 최종 결과
        if (fallback.isEmpty()) {
            log.warn("⚠️ Fallback 제안을 생성할 수 없습니다.");

            QuerySuggestion suggestion = new QuerySuggestion();
            suggestion.setDatabaseId(databaseId);
            suggestion.setQueryHash(queryHash);
            suggestion.setHasTuningSuggestion(false);
            suggestion.setSuggestionLevel("정보");
            suggestion.setSuggestionType("모니터링");
            suggestion.setSuggestionTitle("쿼리 모니터링");
            suggestion.setSuggestionDescription(
                    "현재 쿼리는 구조적으로 큰 문제가 없어 보입니다. 데이터 증가에 따른 성능 변화를 지속적으로 모니터링하세요.");
            suggestion.setExpectedImprovementPercent(BigDecimal.ZERO);
            fallback.add(suggestion);
        }

        log.info("총 {} 개의 Fallback 제안 생성됨", fallback.size());
        return fallback;
    }

    /**
     * Helper 메서드 - 문자열에서 패턴 출현 횟수 세기
     */
    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}