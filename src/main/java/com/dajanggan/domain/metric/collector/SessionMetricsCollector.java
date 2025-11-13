package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
import com.dajanggan.domain.session.repository.SessionRawRepository;
import com.dajanggan.domain.session.repository.SessionRawRepositoryImpl;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class SessionMetricsCollector {

    private final SessionRawRepository sessionRawRepository;  // MyBatis - 모니터링 DB 저장용
    private final SessionRawRepositoryImpl sessionRawRepositoryImpl;  // JdbcTemplate - 대상 DB 조회용
    private final DataSourceFactory dataSourceFactory;

    /** 세션 원시 지표 수집기 (Database 단위) */
    public void collect(Instance instance, Database database, OffsetDateTime collectedAt) {
        //  JdbcTemplate 생성 (인스턴스 + 데이터베이스명으로 동적 연결)
        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());

        //  현재 세션 조회 (pg_stat_activity) - 대상 PostgreSQL DB에서 조회
        List<SessionRawMetricDto> allSessions = sessionRawRepositoryImpl.getActiveSessions(jdbc);

        //  해당 데이터베이스의 세션만 필터링
        List<SessionRawMetricDto> sessions = allSessions.stream()
                .filter(s -> database.getDatabaseName().equals(s.getDatabasename()))
                .collect(Collectors.toList());

        if (sessions.isEmpty()) {
            log.debug("[{}] No active sessions found for database: {}",
                    collectedAt, database.getDatabaseName());
            return;
        }

        //  세션별 가공
        for (SessionRawMetricDto dto : sessions) {
            // Database ID와 Instance ID 설정
            dto.setDatabaseId(database.getDatabaseId());
            dto.setInstanceId(instance.getInstanceId());

            // 쿼리 실행 시간 계산
            OffsetDateTime queryStart = dto.getQueryStart();
            if (queryStart != null) {
                dto.setQueryAgeSec(
                        (System.currentTimeMillis() - queryStart.toInstant().toEpochMilli()) / 1000.0
                );
            }

            // 쿼리 타입 추출 (SELECT, INSERT, UPDATE 등)
            String query = dto.getQuery();
            if (query != null && !query.isBlank()) {
                dto.setQueryType(query.trim().split("\\s+")[0].toUpperCase(Locale.ROOT));
            }

            // 단순 임시 영향도 계산
            String state = Optional.ofNullable(dto.getState()).orElse("").toLowerCase(Locale.ROOT);
            if (state.equals("active")) {
                dto.setImpactLevel("LOW");
            } else if (state.contains("idle in transaction")) {
                dto.setImpactLevel("MEDIUM");
            } else if (state.equals("waiting")) {
                dto.setImpactLevel("HIGH");
            } else {
                dto.setImpactLevel("NONE");
            }

            dto.setCollectedAt(collectedAt);
            dto.setCreatedAt(collectedAt);
        }

        //  락 정보 매핑 - 대상 PostgreSQL DB에서 조회
        Map<Integer, String> lockMap = sessionRawRepositoryImpl.getCurrentLocks(jdbc);
        for (SessionRawMetricDto dto : sessions) {
            if (lockMap.containsKey(dto.getPid())) {
                dto.setLockType(lockMap.get(dto.getPid()));
                // TODO: 실제 대기 시간 계산 로직 필요
                dto.setWaitDurationSec(1.0);
            }
        }

        //  저장 - 모니터링 DB에 INSERT
        sessionRawRepository.insertSessionMetrics(sessions);
        log.info("[{}] Collected {} session metrics for database: {} (instance: {}:{})",
                collectedAt,
                sessions.size(),
                database.getDatabaseName(),
                instance.getHost(),
                instance.getPort());
    }
}
