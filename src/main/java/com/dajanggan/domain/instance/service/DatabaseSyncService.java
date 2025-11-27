package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.dto.DatabaseResponse;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.overview.service.OverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 데이터베이스 동기화 서비스
 *
 * 주요 책임:
 * 
 *   PostgreSQL 서버와 로컬 DB 테이블 간 데이터베이스 목록 동기화
 *   신규 데이터베이스 등록
 *   삭제된 데이터베이스 비활성화 처리
 *   기본 대시보드 자동 생성
 * 
 *
 * DTO 반환 원칙:
 * 
 *   외부로부터는 primitive 타입이나 DTO만 받음
 *   내부에서만 Entity 사용
 *   같은 Service 레이어 간에는 Entity 전달 가능
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-04  김민서    1. 최초작성자
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseSyncService {

    private final DatabaseRepository databaseRepository;
    private final PostgresConnectionService postgresConnectionService;
    private final OverviewService overviewService;

    /**
     * 인스턴스 생성 시 초기 데이터베이스 동기화
     *
     * PostgreSQL 서버의 모든 데이터베이스를 조회하여 등록하고,
     * 기본 대시보드를 자동으로 생성함
     *
     * @param instanceId 인스턴스 ID
     * @param host 호스트 주소
     * @param port 포트 번호
     * @param username 사용자명
     * @param encryptedPassword 암호화된 비밀번호
     * @param sslMode SSL 모드
     * @return 생성된 데이터베이스 DTO 목록
     * @throws RuntimeException 데이터베이스 조회 또는 저장 실패 시
     */
    @Transactional
    public List<DatabaseResponse> syncDatabasesOnCreate(
            Long instanceId,
            String host,
            Integer port,
            String username,
            String encryptedPassword,
            String sslMode) {

        log.info("=== 초기 데이터베이스 동기화 시작: instanceId={} ===", instanceId);

        try {
            // 1. PostgreSQL에서 데이터베이스 목록 조회
            List<String> dbNames = postgresConnectionService.fetchDatabaseNames(
                    host, port, username, encryptedPassword, sslMode);
            log.info("조회된 데이터베이스 개수: {}, 목록: {}", dbNames.size(), dbNames);

            // 2. 데이터베이스 레코드 생성 (내부에서 Entity 사용)
            List<Database> createdDatabases = createDatabaseRecords(instanceId, dbNames);
            log.info("데이터베이스 레코드 생성 완료: {}개", createdDatabases.size());

            // 3. 기본 대시보드 자동 생성 (같은 레이어 서비스 간에는 Entity 전달 가능)
            if (!createdDatabases.isEmpty()) {
                overviewService.createDefaultDashboard(instanceId, createdDatabases);
                log.info("기본 대시보드 생성 완료");
            }

            log.info("=== 초기 데이터베이스 동기화 완료 ===");

            // 4. DTO로 변환하여 반환
            return createdDatabases.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("데이터베이스 동기화 실패: instanceId={}", instanceId, e);
            throw new RuntimeException("데이터베이스 목록 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 인스턴스 수정 시 데이터베이스 동기화
     *
     * 연결 정보가 변경된 경우에만 실행됨:
     * 
     *   신규 데이터베이스: 활성 상태로 등록
     *   삭제된 데이터베이스: 비활성 상태로 변경 (메트릭 데이터 보존)
     *   기존 데이터베이스: 상태 유지
     * 
     *
     * @param instanceId 인스턴스 ID
     * @param host 호스트 주소
     * @param port 포트 번호
     * @param username 사용자명
     * @param encryptedPassword 암호화된 비밀번호
     * @param sslMode SSL 모드
     * @throws RuntimeException 데이터베이스 조회 또는 업데이트 실패 시
     */
    @Transactional
    public void syncDatabasesOnUpdate(
            Long instanceId,
            String host,
            Integer port,
            String username,
            String encryptedPassword,
            String sslMode) {

        log.info("=== 데이터베이스 동기화 시작: instanceId={} ===", instanceId);

        try {
            // 1. 기존 데이터베이스 목록 조회
            List<DatabaseResponse> existingDatabases = databaseRepository
                    .findByInstanceId(instanceId);
            Set<String> existingDbNames = existingDatabases.stream()
                    .map(DatabaseResponse::getDatabaseName)
                    .collect(Collectors.toSet());
            log.debug("기존 데이터베이스 개수: {}", existingDbNames.size());

            // 2. PostgreSQL에서 현재 데이터베이스 목록 조회
            List<String> currentDbNames = postgresConnectionService.fetchDatabaseNames(
                    host, port, username, encryptedPassword, sslMode);
            log.info("현재 데이터베이스 개수: {}, 목록: {}", currentDbNames.size(), currentDbNames);

            // 3. 신규 데이터베이스 추가
            List<String> newDatabases = currentDbNames.stream()
                    .filter(dbName -> !existingDbNames.contains(dbName))
                    .collect(Collectors.toList());

            if (!newDatabases.isEmpty()) {
                createDatabaseRecords(instanceId, newDatabases);
                log.info("신규 데이터베이스 추가 완료: {}개 ({})",
                        newDatabases.size(), newDatabases);
            }

            // 4. 삭제된 데이터베이스 비활성화
            Set<String> currentDbNameSet = new HashSet<>(currentDbNames);
            List<Long> deactivateIds = existingDatabases.stream()
                    .filter(db -> !currentDbNameSet.contains(db.getDatabaseName()))
                    .map(DatabaseResponse::getDatabaseId)
                    .collect(Collectors.toList());

            if (!deactivateIds.isEmpty()) {
                databaseRepository.deactivateByIds(deactivateIds);
                log.info("비활성화된 데이터베이스: {}개", deactivateIds.size());
            }

            log.info("=== 데이터베이스 동기화 완료 ===");

        } catch (Exception e) {
            log.error("데이터베이스 동기화 실패 (인스턴스 수정은 완료됨): instanceId={}",
                    instanceId, e);
            // 동기화 실패 시에도 인스턴스 업데이트는 유지
        }
    }

    /**
     * 데이터베이스 레코드 생성
     *
     * Service 내부 메서드이므로 Entity 사용 가능
     *
     * @param instanceId 인스턴스 ID
     * @param databaseNames 데이터베이스 이름 목록
     * @return 생성된 Database 엔티티 목록
     */
    private List<Database> createDatabaseRecords(Long instanceId, List<String> databaseNames) {
        List<Database> createdDatabases = new ArrayList<>();

        for (String dbName : databaseNames) {
            Database database = new Database();
            database.setInstanceId(instanceId);
            database.setDatabaseName(dbName);
            database.setIsEnabled(true);

            log.debug("데이터베이스 저장 시도: instanceId={}, dbName={}", instanceId, dbName);
            databaseRepository.insert(database);
            log.debug("데이터베이스 저장 완료: databaseId={}", database.getDatabaseId());

            createdDatabases.add(database);
        }

        return createdDatabases;
    }

    /**
     * Entity를 DTO로 변환
     *
     * @param database Database 엔티티
     * @return DatabaseResponse DTO
     */
    private DatabaseResponse toDto(Database database) {
        return DatabaseResponse.builder()
                .databaseId(database.getDatabaseId())
                .instanceId(database.getInstanceId())
                .databaseName(database.getDatabaseName())
                .status(database.getStatus())
                .connections(database.getConnections())
                .sizeBytes(database.getSizeBytes())
                .cacheHitRate(database.getCacheHitRate())
                .updatedAt(database.getUpdatedAt())
                .build();
    }
}