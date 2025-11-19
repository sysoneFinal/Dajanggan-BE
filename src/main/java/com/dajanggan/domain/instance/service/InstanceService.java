package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.*;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.overview.service.OverviewService; // 🔥 추가
import com.dajanggan.global.crypto.AesGcmService;
import com.dajanggan.global.exception.ExceptionMessage;
import com.dajanggan.global.exception.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstanceService {
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final AesGcmService aesGcmService;
    private final OverviewService overviewService; // 🔥 추가

    // (cud는 도메인 반환)
    @Transactional
    public InstanceResponse create(InstanceCreateRequest dto) {
        log.info("=== 인스턴스 등록 시작 ===");
        log.info("Host: {}, Port: {}, UserName: {}", dto.getHost(), dto.getPort(), dto.getUserName());

        // 1. 비밀번호 암호화
        Instance entity = dto.toEntity();
        String encryptedPassword = aesGcmService.encryptString(dto.getSecretRef());
        entity.setSecretRef(encryptedPassword);
        log.info("비밀번호 암호화 완료");

        // SSL 모드 기본값 설정 (null이거나 빈 값이면 disable로)
        entity.setSslmode("disable");


        // 2. 인스턴스 저장
        instanceRepository.createInstance(entity);
        log.info("인스턴스 저장 완료. instanceId: {}", entity.getInstanceId());

        // 3. PostgreSQL 연결하여 DB 이름 조회
        try {
            String password = aesGcmService.decryptToString(entity.getSecretRef());
            log.info("비밀번호 복호화 완료");

            List<String> dbNames = fetchDatabaseNames(entity, password);
            log.info("조회된 DB 개수: {}, DB 목록: {}", dbNames.size(), dbNames);

            // 4. Database 레코드 생성
            List<Database> createdDatabases = new ArrayList<>();
            for (String dbName : dbNames) {
                Database database = new Database();
                database.setInstanceId(entity.getInstanceId());
                database.setDatabaseName(dbName);
                database.setIsEnabled(true);

                log.info("DB 저장 시도: instanceId={}, dbName={}", entity.getInstanceId(), dbName);
                databaseRepository.insert(database);
                log.info("DB 저장 완료: databaseId={}", database.getDatabaseId());
                
                createdDatabases.add(database);
            }

            // 🔥 5. 디폴트 대시보드 자동 생성
            if (!createdDatabases.isEmpty()) {
                overviewService.createDefaultDashboard(entity.getInstanceId(), createdDatabases);
                log.info("디폴트 대시보드 생성 완료");
            }

            log.info("=== 인스턴스 등록 완료 ===");
        } catch (Exception e) {
            log.error("DB 목록 조회/저장 실패", e);
            throw new RuntimeException("DB 목록 처리 실패: " + e.getMessage(), e);
        }

        return InstanceResponse.from(entity);
    }

    // fetchDatabaseNames 메서드 수정 (sslMode 기본값 처리 강화)
    private List<String> fetchDatabaseNames(Instance instance, String password) {
        // sslMode가 null이거나 빈 문자열이면 "disable"로 설정
        String sslMode = (instance.getSslmode() != null && !instance.getSslmode().trim().isEmpty())
                ? instance.getSslmode()
                : "disable";

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres?sslmode=%s",
                instance.getHost(), instance.getPort(), sslMode);

        log.info("PostgreSQL 연결 시도: {} (sslMode: {})", jdbcUrl, sslMode);

        String query = """
        SELECT datname 
        FROM pg_database 
        WHERE datistemplate = false
        AND datname NOT IN ('template0', 'template1')
        """;

        List<String> result = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, instance.getUserName(), password)) {
            log.info("PostgreSQL 연결 성공");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    String dbName = rs.getString("datname");
                    result.add(dbName);
                    log.debug("DB 발견: {}", dbName);
                }
            }

            log.info("총 {}개 DB 조회 완료", result.size());
        } catch (SQLException e) {
            log.error("PostgreSQL 연결/조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch database names: " + e.getMessage(), e);
        }

        return result;
    }

    // testConnection 메서드도 DTO의 sslMode를 존중하도록 수정
    public Map<String, Object> testConnection(InstanceCreateRequest dto) {
        Map<String, Object> result = new HashMap<>();

        // sslMode 처리 (DTO에 없으면 disable 사용)
        String sslMode = (dto.getSslmode() != null && !dto.getSslmode().trim().isEmpty())
                ? dto.getSslmode()
                : "disable";

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres?sslmode=%s",
                dto.getHost(), dto.getPort(), sslMode);

        log.info("연결 테스트 시작: {} (sslMode: {})", jdbcUrl, sslMode);

        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                dto.getUserName(),
                dto.getSecretRef())) {

            // 버전 정보 조회
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version()")) {

                if (rs.next()) {
                    String version = rs.getString(1);
                    log.info("연결 성공! PostgreSQL 버전: {}", version);

                    result.put("success", true);
                    result.put("message", "연결 성공!");
                    result.put("version", version);
                }
            }

        } catch (SQLException e) {
            log.error("연결 실패: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "연결 실패: " + e.getMessage());
            result.put("errorCode", e.getSQLState());
        }

        return result;
    }


    // 하나 조회 (dto 반환)
    public InstanceResponse findOne(Long id) {
        // 1. entity 조회
        Instance entity = instanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));
        return InstanceResponse.from(entity);  // DTO로 변환
    }

    // 조회 (dto 반환)
    public List<InstanceResponse> findAll() {
        // 1. entity 리스트 조회
        List<Instance> entities = instanceRepository.findAll();

        // 2. entity -> dto 변환
        return entities.stream()
                .map(InstanceResponse::from)
                .toList();
    }


    @Transactional
    public InstanceResponse update(Long id, @Valid InstanceUpdateRequest req) {
        log.info("=== 인스턴스 수정 시작 ===");
        log.info("instanceId: {}, Host: {}, Port: {}, UserName: {}", id, req.getHost(), req.getPort(), req.getUserName());

        // 1. DB에서 entity 조회
        Instance entity = instanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));

        // 2. 기본 정보 수정
        entity.setInstanceName(req.getInstanceName());
        entity.setHost(req.getHost());
        entity.setPort(req.getPort());
        entity.setUserName(req.getUserName());

        // 3. 비밀번호가 제공된 경우에만 암호화하여 업데이트
        if (req.getSecretRef() != null && !req.getSecretRef().trim().isEmpty()) {
            String encryptedPassword = aesGcmService.encryptString(req.getSecretRef());
            entity.setSecretRef(encryptedPassword);
            log.info("비밀번호 업데이트 완료");
        }

        // 4. 인스턴스 업데이트
        int rows = instanceRepository.updateInstance(entity);
        if (rows != 1) {
            throw new IllegalStateException("업데이트 대상이 없거나 변경 실패: id=" + id);
        }
        log.info("인스턴스 업데이트 완료. instanceId: {}", id);

        // 5. 호스트/포트/계정이 변경되었거나 비밀번호가 제공된 경우, DB 목록 재동기화
        if (isConnectionInfoChanged(req)) {
            try {
                String password = aesGcmService.decryptToString(entity.getSecretRef());
                log.info("비밀번호 복호화 완료");

                // 기존 DB 목록 조회
                List<Database> existingDbs = databaseRepository.findDatabaseEntitiesByInstanceId(id);
                Set<String> existingDbNames = existingDbs.stream()
                        .map(Database::getDatabaseName)
                        .collect(Collectors.toSet());

                // PostgreSQL에서 현재 DB 목록 조회
                List<String> currentDbNames = fetchDatabaseNames(entity, password);
                log.info("조회된 DB 개수: {}, DB 목록: {}", currentDbNames.size(), currentDbNames);

                // 새로 추가된 DB만 insert
                for (String dbName : currentDbNames) {
                    if (!existingDbNames.contains(dbName)) {
                        Database database = new Database();
                        database.setInstanceId(id);
                        database.setDatabaseName(dbName);
                        database.setIsEnabled(true);

                        log.info("새 DB 저장 시도: instanceId={}, dbName={}", id, dbName);
                        databaseRepository.insert(database);
                        log.info("새 DB 저장 완료: databaseId={}", database.getDatabaseId());
                    }
                }

                // 삭제된 DB는 비활성화 (실제 삭제는 하지 않음 - 메트릭 데이터 보존)
                Set<String> currentDbNameSet = new HashSet<>(currentDbNames);
                for (Database existingDb : existingDbs) {
                    if (!currentDbNameSet.contains(existingDb.getDatabaseName()) && existingDb.getIsEnabled()) {
                        existingDb.setIsEnabled(false);
                        log.info("DB 비활성화: databaseId={}, dbName={}",
                                existingDb.getDatabaseId(), existingDb.getDatabaseName());
                    }
                }

                log.info("=== 인스턴스 수정 및 DB 동기화 완료 ===");
            } catch (Exception e) {
                log.error("DB 목록 동기화 실패 (인스턴스 수정은 완료됨)", e);
                // DB 동기화 실패해도 인스턴스 업데이트는 유지
            }
        } else {
            log.info("=== 인스턴스 수정 완료 (DB 동기화 스킵) ===");
        }

        return InstanceResponse.from(entity);
    }

    /**
     * 연결 정보가 변경되었는지 확인
     */
    private boolean isConnectionInfoChanged(InstanceUpdateRequest req) {
        // 호스트, 포트, 사용자명, 비밀번호 중 하나라도 변경되면 true
        // 실제로는 이전 값과 비교해야 하지만, 간단하게 비밀번호 제공 여부로 판단
        return req.getSecretRef() != null && !req.getSecretRef().trim().isEmpty();
    }

    @Transactional
    public void delete(Long id) {
        log.info("=== 인스턴스 삭제 시작 ===");

        // 존재 확인
        Instance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));

        log.info("인스턴스 삭제: instanceId={}, instanceName={}", id, instance.getInstanceName());

        // 1. 해당 인스턴스의 모든 데이터베이스 조회
        List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(id);

        if (!databases.isEmpty()) {
            List<Long> databaseIds = databases.stream()
                    .map(Database::getDatabaseId)
                    .collect(Collectors.toList());

            log.info("삭제할 데이터베이스 개수: {}, IDs: {}", databaseIds.size(), databaseIds);


            // 3. 데이터베이스 레코드 삭제
            databaseRepository.deleteByInstanceId(id);
            log.info("데이터베이스 레코드 삭제 완료: instanceId={}", id);
        }

        // 4. 인스턴스 삭제
        instanceRepository.deleteById(id);
        log.info("=== 인스턴스 삭제 완료: instanceId={} ===", id);
    }

    // (entity -> dto 변환)
    public List<InstanceWithDatabasesDto> findAllWithDatabases() {
        // 1. 인스턴스 조회 (dto)
        List<Instance> instances = instanceRepository.findAll();
        if (instances.isEmpty()) return List.of();

        // 2. id 모으기
        List<Long> instanceIds = instances.stream()
                .map(Instance::getInstanceId)
                .toList();

        // 3. 모든 DB 한 번에 조회
        var allDbs = databaseRepository.findByInstanceIds(instanceIds);

        // 4) instanceId -> List<Database> 그룹핑
        Map<Long, List<com.dajanggan.domain.instance.domain.Database>> dbMap =
                allDbs.stream().collect(Collectors.groupingBy(com.dajanggan.domain.instance.domain.Database::getInstanceId));

        // 5. Entity + Database -> DTO 조합
        return instances.stream()
                .map(instance -> {
                    InstanceResponse instanceDto = InstanceResponse.from(instance);
                    List<com.dajanggan.domain.instance.domain.Database> databases =
                            dbMap.getOrDefault(instance.getInstanceId(), List.of());
                    return DtoMappers.toInstanceWithDbDto(instanceDto, databases);
                })
                .toList();
    }
}
