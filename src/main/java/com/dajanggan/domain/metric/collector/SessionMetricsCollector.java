package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.session.dto.raw.LockSessionDto;
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

    private final SessionRawRepository sessionRawRepository;
    private final SessionRawRepositoryImpl sessionRawRepositoryImpl;
    private final DataSourceFactory dataSourceFactory;

    /** 세션 원시 지표 수집기 (Database 단위) */
    public void collect(Instance instance, Database database, OffsetDateTime collectedAt) {
        // JdbcTemplate 생성
        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());

        // 현재 세션 조회 (pg_stat_activity)
        List<SessionRawMetricDto> allSessions = sessionRawRepositoryImpl.getActiveSessions(jdbc);

        // 해당 데이터베이스의 세션만 필터링
        List<SessionRawMetricDto> sessions = allSessions.stream()
                .filter(s -> database.getDatabaseName().equals(s.getDatabasename()))
                .collect(Collectors.toList());

        if (sessions.isEmpty()) {
            log.debug("[{}] 활성화된 세션이 없습니다 : {}", collectedAt, database.getDatabaseName());
            return;
        }

        // 락 정보 조회
        Map<Integer, LockSessionDto> lockMap = sessionRawRepositoryImpl.getWaitingLocks(jdbc);

        // 세션별 가공
        for (SessionRawMetricDto dto : sessions) {
            // 기본 정보 설정
            dto.setDatabaseId(database.getDatabaseId());
            dto.setInstanceId(instance.getInstanceId());
            dto.setCollectedAt(collectedAt);

            // 쿼리 실행 시간 계산
            calculateQueryAge(dto);

            // 쿼리 타입 추출
            extractQueryType(dto);

            // 락 정보 매핑
            LockSessionDto lockInfo = lockMap.get(dto.getPid());
            if (lockInfo != null) {
                dto.setLockType(lockInfo.getLockType());
                dto.setTableName(lockInfo.getTableName());
                dto.setWaitDurationSec(lockInfo.getWaitDurationSec());
            }

            // 영향도 계산 (통합 - 한 번만!)
            dto.setImpactLevel(calculateImpactLevel(dto, lockInfo));
        }

        // 저장 - 모니터링 DB에 INSERT
        sessionRawRepository.insertSessionMetrics(sessions);
        log.info("[{}] Collected {} 세션 지표 for 데이터베이스: {} (instance: {}:{})",
                collectedAt,
                sessions.size(),
                database.getDatabaseName(),
                instance.getHost(),
                instance.getPort());
    }



    /** 쿼리 실행 시간 계산 */
    private void calculateQueryAge(SessionRawMetricDto dto) {
        OffsetDateTime queryStart = dto.getQueryStart();
        if (queryStart != null) {
            dto.setQueryAgeSec(
                    (System.currentTimeMillis() - queryStart.toInstant().toEpochMilli()) / 1000.0
            );
        }
    }



    /** 쿼리 타입 추출 (SELECT, INSERT, UPDATE 등) */
    private void extractQueryType(SessionRawMetricDto dto) {
        String query = dto.getQuery();
        if (query != null && !query.isBlank()) {
            dto.setQueryType(query.trim().split("\\s+")[0].toUpperCase(Locale.ROOT));
        }
    }



    /** 영향도 계산  */
    private String calculateImpactLevel(SessionRawMetricDto dto, LockSessionDto lockInfo) {
        String state = Optional.ofNullable(dto.getState()).orElse("").toLowerCase(Locale.ROOT);
        String waitEventType = Optional.ofNullable(dto.getWaitEventType()).orElse("").toLowerCase(Locale.ROOT);
        Integer blockingPid = dto.getBlockingPid();

        // CRITICAL 조건들
        if (blockingPid != null) {
            return "CRITICAL";
        }
        if ("lock".equals(waitEventType) || "lwlock".equals(waitEventType)) {
            return "CRITICAL";
        }

        // 락 mode 기반 판단
        if (lockInfo != null) {
            String mode = lockInfo.getMode();
            if ("AccessExclusiveLock".equals(mode) || "ExclusiveLock".equals(mode)) {
                return "CRITICAL";
            }
        }

        // WARN 조건들
        if (state.contains("idle in transaction")) {
            return "WARN";
        }
        if ("active".equals(state) && waitEventType.contains("io")) {
            return "WARN";
        }

        // 락 mode 기반 WARN
        if (lockInfo != null) {
            String mode = lockInfo.getMode();
            if ("RowExclusiveLock".equals(mode) || "ShareLock".equals(mode)) {
                return "WARN";
            }
        }

        // 기본: INFO
        return "INFO";
    }
}