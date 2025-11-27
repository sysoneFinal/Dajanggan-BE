/** 작성자 : 서샘이 */
package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.event.detector.SessionEventDetector;
import com.dajanggan.domain.event.dto.EventLevel;
import com.dajanggan.domain.event.dto.EventLog;
import com.dajanggan.domain.event.service.EventService;
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

    private final SessionRawRepositoryImpl sessionRawRepositoryImpl;
    private final DataSourceFactory dataSourceFactory;
    private final SessionEventDetector sessionEventDetector;
    private final EventService eventService;

    /** 세션 원시 지표 수집기 (Database 단위) - 복호화된 비밀번호 사용 */
    public void collect(Instance instance, Database database, String decryptedPassword, OffsetDateTime collectedAt) {
        long startTime = System.currentTimeMillis();

        // JdbcTemplate 생성 (복호화된 비밀번호 사용)
        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName(), decryptedPassword);

        // 현재 세션 조회 (pg_stat_activity)
        long queryStartTime = System.currentTimeMillis();
        List<SessionRawMetricDto> allSessions = sessionRawRepositoryImpl.getActiveSessions(jdbc);
        long queryTime = System.currentTimeMillis() - queryStartTime;

        // 해당 데이터베이스의 세션만 필터링
        List<SessionRawMetricDto> sessions = allSessions.stream()
                .filter(s -> database.getDatabaseName().equals(s.getDatabasename()))
                .collect(Collectors.toList());

        if (sessions.isEmpty()) {
            log.debug("[{}] 활성화된 세션이 없습니다 : {} (조회시간: {}ms)",
                    collectedAt, database.getDatabaseName(), queryTime);
            return;
        }

        // 락 정보 조회
        long lockQueryStartTime = System.currentTimeMillis();
        Map<Integer, LockSessionDto> lockMap = sessionRawRepositoryImpl.getWaitingLocks(jdbc);
        long lockQueryTime = System.currentTimeMillis() - lockQueryStartTime;

        // 1. 세션별 가공
        long processingStartTime = System.currentTimeMillis();
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
            dto.setLockInfo(lockInfo);
            if (lockInfo != null) {
                dto.setLockType(lockInfo.getLockType());
                dto.setTableName(lockInfo.getTableName());
                dto.setWaitDurationSec(lockInfo.getWaitDurationSec());
            }

            // 영향도 계산 (세션별)
            EventLevel impact = calculateImpactLevel(dto, lockInfo);
            dto.setImpactLevel(impact.name());
        }
        long processingTime = System.currentTimeMillis() - processingStartTime;

        // 원시 데이터 저장
        long saveStartTime = System.currentTimeMillis();
        try {
            sessionRawRepositoryImpl.insertSessionMetricsCopy(sessions);
            long saveTime = System.currentTimeMillis() - saveStartTime;
            long totalTime = System.currentTimeMillis() - startTime;

            log.info("📊 [SESSION] {}:{}/{} - {} 세션 수집 완료 (총: {}ms | 조회: {}ms, 락조회: {}ms, 가공: {}ms, 저장: {}ms)",
                    instance.getHost(),
                    instance.getPort(),
                    database.getDatabaseName(),
                    sessions.size(),
                    totalTime,
                    queryTime,
                    lockQueryTime,
                    processingTime,
                    saveTime);
        } catch (Exception e) {
            long saveTime = System.currentTimeMillis() - saveStartTime;
            long totalTime = System.currentTimeMillis() - startTime;

            log.error("❌ [SESSION] {}:{}/{} - 원시 데이터 저장 실패 (총: {}ms | 조회: {}ms, 락조회: {}ms, 가공: {}ms, 저장시도: {}ms) - 에러: {}",
                    instance.getHost(),
                    instance.getPort(),
                    database.getDatabaseName(),
                    totalTime,
                    queryTime,
                    lockQueryTime,
                    processingTime,
                    saveTime,
                    e.getMessage());
            throw new RuntimeException("세션 메트릭 저장 실패", e);
        }

        // 2. 전체 세션 대상으로 이벤트 감지
        long eventStartTime = System.currentTimeMillis();
        List<EventLog> events = sessionEventDetector.detectEvents(
                sessions,
                database.getDatabaseId(),
                instance.getInstanceId(),
                database.getDatabaseName(),
                instance.getInstanceName()
        );
        long eventTime = System.currentTimeMillis() - eventStartTime;

        // 3. 이벤트 저장
        long eventSaveStartTime = System.currentTimeMillis();
        eventService.saveEvents(events);
        long eventSaveTime = System.currentTimeMillis() - eventSaveStartTime;

        log.debug("📊 [SESSION] 이벤트 처리 완료 - 감지: {}ms, 저장: {}ms", eventTime, eventSaveTime);
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
    private EventLevel calculateImpactLevel(SessionRawMetricDto dto, LockSessionDto lockInfo) {
        String state = Optional.ofNullable(dto.getState()).orElse("").toLowerCase(Locale.ROOT);
        String waitEventType = Optional.ofNullable(dto.getWaitEventType()).orElse("").toLowerCase(Locale.ROOT);
        Integer blockingPid = dto.getBlockingPid();

        // CRITICAL 조건들
        if (blockingPid != null) {
            return EventLevel.CRITICAL;
        }
        if ("lock".equals(waitEventType) || "lwlock".equals(waitEventType)) {
            return EventLevel.CRITICAL;
        }

        // 락 mode 기반 판단
        if (lockInfo != null) {
            String mode = lockInfo.getMode();
            if ("AccessExclusiveLock".equals(mode) || "ExclusiveLock".equals(mode)) {
                return EventLevel.CRITICAL;
            }
        }

        // WARN 조건들
        if (state.contains("idle in transaction")) {
            return EventLevel.WARN;
        }
        if ("active".equals(state) && waitEventType.contains("io")) {
            return EventLevel.WARN;
        }

        // 락 mode 기반 WARN
        if (lockInfo != null) {
            String mode = lockInfo.getMode();
            if ("RowExclusiveLock".equals(mode) || "ShareLock".equals(mode)) {
                return EventLevel.WARN;
            }
        }

        // 기본: INFO
        return EventLevel.INFO;
    }
}