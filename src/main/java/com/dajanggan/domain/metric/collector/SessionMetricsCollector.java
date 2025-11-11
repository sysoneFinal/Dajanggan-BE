//package com.dajanggan.domain.metric.collector;
//
//import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
//import com.dajanggan.domain.session.repository.SessionRawRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.jdbc.core.JdbcTemplate;
//
//import org.springframework.stereotype.Service;
//
//import java.time.OffsetDateTime;
//import java.util.*;
//
//@RequiredArgsConstructor
//@Service
//public class SessionMetricsCollector {
//
//
//    private final SessionRawRepository sessionRawRepository;
//
//
//    /** 세션 원시 지표 수집기 */
//    public void collect(DatabaseInstance instance, OffsetDateTime collectedAt) {
//        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance);
//        List<SessionRawMetricDto> sessions = sessionRawRepository.getActiveSessions(jdbc);
//
//        // ① 현재 세션 조회 (pg_stat_activity)
//        List<SessionRawMetricDto> sessions = sessionRawRepository.getActiveSessions();
//
//        // ② 세션별 가공
//        for (SessionRawMetricDto dto : sessions) {
//            OffsetDateTime queryStart = dto.getQueryStart();
//            if (queryStart != null) {
//                dto.setQueryAgeSec(
//                        (System.currentTimeMillis() - queryStart.toInstant().toEpochMilli()) / 1000.0
//                );
//            }
//
//            String query = dto.getQuery();
//            if (query != null && !query.isBlank()) {
//                dto.setQueryType(query.trim().split("\\s+")[0].toUpperCase(Locale.ROOT));
//            }
//
//            // 단순 임시 영향도 계산
//            String state = Optional.ofNullable(dto.getState()).orElse("").toLowerCase(Locale.ROOT);
//            if (state.equals("active")) dto.setImpactLevel("LOW");
//            else if (state.contains("idle in transaction")) dto.setImpactLevel("MEDIUM");
//            else if (state.equals("waiting")) dto.setImpactLevel("HIGH");
//            else dto.setImpactLevel("NONE");
//
//            dto.setCollectedAt(now);
//            dto.setCreatedAt(now);
//        }
//
//        // ③ 락 정보 매핑 (별도 mapper 호출)
//        Map<Integer, String> lockMap = sessionRawRepository.getCurrentLocks();
//        for (SessionRawMetricDto dto : sessions) {
//            if (lockMap.containsKey(dto.getPid())) {
//                dto.setLockType(lockMap.get(dto.getPid()));
//                dto.setWaitDurationSec(1.0);
//            }
//        }
//
//        // ④ 저장
//        if (!sessions.isEmpty()) {
//            sessionRawRepository.insertSessionMetrics(sessions);
//            System.out.printf("✅ [%s] Collected %d session metrics%n", now, sessions.size());
//        } else {
//            System.out.printf("⚠ [%s] No session metrics found%n", now);
//        }
//    }
//}
