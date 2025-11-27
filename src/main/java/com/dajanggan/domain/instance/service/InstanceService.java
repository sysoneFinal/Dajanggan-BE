package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.*;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.crypto.AesGcmService;
import com.dajanggan.global.exception.ExceptionMessage;
import com.dajanggan.global.exception.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 인스턴스 서비스
 *
 * 주요 책임:
 * 
 *   인스턴스 CRUD 작업
 *   인스턴스와 데이터베이스 목록 조회
 *   PostgreSQL 연결 테스트
 * 
 *
 * 위임된 책임:
 * 
 *   PostgreSQL 연결 관리 → {@link PostgresConnectionService}
 *   데이터베이스 동기화 → {@link DatabaseSyncService}
 *   Slack 설정 관리 → {@link SlackSettingsService}
 * 
 *
 * DTO 반환 원칙 준수:
 * 
 *   Controller에는 반드시 DTO 반환
 *   Repository로부터 Entity를 받아 내부에서 사용
 *   다른 Service에는 DTO 또는 primitive 타입 전달
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-10-23  김민서    1. 최초작성자
 *      2025-11-04  김민서    2. 데이터베이스 연결
 *      2025-11-13  김민서    3. postgreSQL 연동 로직
 *      2025-11-21  김민서    4. 슬랙 연동
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstanceService {

    // ========== Dependencies ==========
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final AesGcmService aesGcmService;

    // 위임 서비스
    private final PostgresConnectionService postgresConnectionService;
    private final DatabaseSyncService databaseSyncService;
    private final SlackSettingsService slackSettingsService;

    // ========== 상수 ==========
    /** 기본 SSL 모드 */
    private static final String DEFAULT_SSL_MODE = "disable";

    // ========== 생성 (Create) ==========

    /**
     * 인스턴스 생성
     *
     * 처리 흐름:
     * <ol>
     *   비밀번호 암호화
     *   인스턴스 레코드 저장
     *   PostgreSQL 연결 및 데이터베이스 목록 조회
     *   데이터베이스 레코드 생성
     *   기본 대시보드 자동 생성
     * </ol>
     *
     * @param dto 인스턴스 생성 요청 DTO
     * @return 생성된 인스턴스 정보 DTO
     * @throws RuntimeException 데이터베이스 동기화 실패 시
     */
    @Transactional
    public InstanceResponse create(InstanceCreateRequest dto) {
        log.info("=== 인스턴스 등록 시작 ===");
        log.info("Host: {}, Port: {}, UserName: {}", dto.getHost(), dto.getPort(), dto.getUserName());

        // 1. 엔티티 생성 및 비밀번호 암호화
        Instance entity = buildInstanceEntity(dto);
        log.info("비밀번호 암호화 완료");

        // 2. 인스턴스 저장
        instanceRepository.createInstance(entity);
        log.info("인스턴스 저장 완료. instanceId: {}", entity.getInstanceId());

        // 3. 비밀번호 복호화 (DB 동기화에 필요)
        String decryptedPassword = aesGcmService.decryptToString(entity.getSecretRef());

        // 4. 데이터베이스 동기화 (primitive 타입과 DTO 전달)
        databaseSyncService.syncDatabasesOnCreate(
                entity.getInstanceId(),
                entity.getHost(),
                entity.getPort(),
                entity.getUserName(),
                decryptedPassword,
                entity.getSslmode()
        );

        log.info("=== 인스턴스 등록 완료 ===");

        // 5. DTO 반환 (Entity → DTO 변환)
        return InstanceResponse.from(entity);
    }

    /**
     * PostgreSQL 연결 테스트
     *
     * 인스턴스 생성 전 연결 가능 여부를 확인하는 용도
     *
     * @param dto 인스턴스 생성 요청 DTO
     * @return 연결 테스트 결과 (success, message, version, errorCode)
     */
    public Map<String, Object> testConnection(InstanceCreateRequest dto) {
        log.info("연결 테스트 시작: Host={}, Port={}", dto.getHost(), dto.getPort());

        // DTO에서 필요한 정보만 추출하여 전달
        Map<String, Object> result = postgresConnectionService.testConnection(
                dto.getHost(),
                dto.getPort(),
                dto.getUserName(),
                dto.getSecretRef(),  // 평문 비밀번호
                dto.getSslmode()
        );

        log.info("연결 테스트 완료: success={}", result.get("success"));
        return result;
    }

    // ========== 조회 (Read) ==========

    /**
     * 인스턴스 단건 조회
     *
     * @param id 인스턴스 ID
     * @return 인스턴스 정보 DTO
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     */
    public InstanceResponse findOne(Long id) {
        log.debug("인스턴스 조회: id={}", id);

        // Repository에서 Entity 조회
        Instance entity = findInstanceById(id);

        // Entity → DTO 변환하여 반환
        return InstanceResponse.from(entity);
    }

    /**
     * 인스턴스 전체 조회
     *
     * @return 전체 인스턴스 목록 DTO
     */
    public List<InstanceResponse> findAll() {
        log.debug("인스턴스 전체 조회");

        // Repository에서 Entity 목록 조회
        List<Instance> entities = instanceRepository.findAll();

        // Entity → DTO 변환하여 반환
        return entities.stream()
                .map(InstanceResponse::from)
                .toList();
    }

    /**
     * 인스턴스와 데이터베이스 목록 함께 조회
     *
     * 성능 최적화:
     * 
     *   N+1 문제 방지를 위해 데이터베이스 목록을 일괄 조회
     *   인스턴스 ID별로 그룹핑하여 메모리에서 조합
     * 
     *
     * @return 인스턴스와 데이터베이스 목록 DTO
     */
    public List<InstanceWithDatabasesDto> findAllWithDatabases() {
        log.debug("인스턴스 + 데이터베이스 목록 조회");

        // 1. Repository에서 DTO 직접 조회 (성능 최적화)
        List<InstanceResponse> instanceDtos = instanceRepository.findAll()
                .stream()
                .map(InstanceResponse::from)
                .toList();

        if (instanceDtos.isEmpty()) {
            return List.of();
        }

        // 2. 인스턴스 ID 목록 추출
        List<Long> instanceIds = instanceDtos.stream()
                .map(InstanceResponse::getInstanceId)
                .toList();

        // 3. 데이터베이스 일괄 조회 (N+1 방지) - Repository에서 DTO 반환
        List<DatabaseResponse> allDatabases = databaseRepository.findByInstanceIds(instanceIds);

        // 4. 인스턴스 ID별로 데이터베이스 그룹핑
        Map<Long, List<DatabaseResponse>> databaseMap = allDatabases.stream()
                .collect(Collectors.groupingBy(DatabaseResponse::getInstanceId));

        // 5. DTO 조합하여 반환
        return instanceDtos.stream()
                .map(instanceDto -> {
                    List<DatabaseResponse> databases = databaseMap.getOrDefault(
                            instanceDto.getInstanceId(),
                            List.of()
                    );
                    return DtoMappers.toInstanceWithDbDto(instanceDto, databases);
                })
                .toList();
    }

    // ========== 수정 (Update) ==========

    /**
     * 인스턴스 수정
     *
     * 처리 흐름:
     * <ol>
     *   인스턴스 기본 정보 업데이트
     *   비밀번호 제공 시 암호화하여 업데이트
     *   연결 정보 변경 시 데이터베이스 재동기화
     * </ol>
     *
     * @param id 인스턴스 ID
     * @param req 인스턴스 수정 요청 DTO
     * @return 수정된 인스턴스 정보 DTO
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     * @throws IllegalStateException 업데이트 실패 시
     */
    @Transactional
    public InstanceResponse update(Long id, @Valid InstanceUpdateRequest req) {
        log.info("=== 인스턴스 수정 시작 ===");
        log.info("instanceId: {}, Host: {}, Port: {}", id, req.getHost(), req.getPort());

        // 1. 기존 인스턴스 조회 (Repository에서 Entity 반환)
        Instance entity = findInstanceById(id);

        // 2. 기본 정보 업데이트
        updateBasicInfo(entity, req);

        // 3. 비밀번호 업데이트 (제공된 경우에만)
        boolean passwordUpdated = updatePasswordIfProvided(entity, req);

        // 4. 인스턴스 업데이트 실행
        executeInstanceUpdate(entity, id);

        // 5. 연결 정보 변경 시 데이터베이스 재동기화
        if (passwordUpdated) {
            String decryptedPassword = aesGcmService.decryptToString(entity.getSecretRef());

            databaseSyncService.syncDatabasesOnUpdate(
                    entity.getInstanceId(),
                    entity.getHost(),
                    entity.getPort(),
                    entity.getUserName(),
                    decryptedPassword,
                    entity.getSslmode()
            );
        } else {
            log.info("=== 인스턴스 수정 완료 (데이터베이스 동기화 스킵) ===");
        }

        // 6. DTO 반환
        return InstanceResponse.from(entity);
    }

    // ========== 삭제 (Delete) ==========

    /**
     * 인스턴스 삭제
     *
     * 연관 데이터 삭제 순서:
     * <ol>
     *   해당 인스턴스의 모든 데이터베이스 레코드 삭제
     *   인스턴스 레코드 삭제
     * </ol>
     *
     * @param id 인스턴스 ID
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     */
    @Transactional
    public void delete(Long id) {
        log.info("=== 인스턴스 삭제 시작 ===");

        // 1. 인스턴스 존재 확인
        Instance instance = findInstanceById(id);
        log.info("인스턴스 삭제: instanceId={}, instanceName={}",
                id, instance.getInstanceName());

        // 2. 연관된 데이터베이스 조회 (Repository에서 DTO 반환)
        List<DatabaseResponse> databases = databaseRepository.findByInstanceId(id);

        // 3. 데이터베이스 레코드 삭제
        if (!databases.isEmpty()) {
            List<Long> databaseIds = databases.stream()
                    .map(DatabaseResponse::getDatabaseId)
                    .collect(Collectors.toList());

            log.info("삭제할 데이터베이스 개수: {}, IDs: {}", databaseIds.size(), databaseIds);
            databaseRepository.deleteByInstanceId(id);
            log.info("데이터베이스 레코드 삭제 완료");
        }

        // 4. 인스턴스 삭제
        instanceRepository.deleteById(id);
        log.info("=== 인스턴스 삭제 완료: instanceId={} ===", id);
    }

    // ========== Slack 설정 위임 메서드 ==========

    /**
     * Slack 설정 업데이트 (인스턴스 이름 기준)
     */
    @Transactional
    public void updateSlackSettings(String instanceName, SlackSettingsRequest request) {
        slackSettingsService.updateSlackSettings(instanceName, request);
    }

    /**
     * Slack 설정 업데이트 (인스턴스 ID 기준)
     */
    @Transactional
    public void updateSlackSettingsById(Long instanceId, SlackSettingsRequest request) {
        slackSettingsService.updateSlackSettingsById(instanceId, request);
    }

    /**
     * Slack 설정 조회 (인스턴스 이름 기준)
     */
    public SlackSettingsRequest getSlackSettings(String instanceName) {
        return slackSettingsService.getSlackSettings(instanceName);
    }

    /**
     * Slack 설정 조회 (인스턴스 ID 기준)
     */
    public SlackSettingsRequest getSlackSettingsById(Long instanceId) {
        return slackSettingsService.getSlackSettingsById(instanceId);
    }

    /**
     * Slack 설정 삭제 (인스턴스 이름 기준)
     */
    @Transactional
    public void deleteSlackSettings(String instanceName) {
        slackSettingsService.deleteSlackSettings(instanceName);
    }

    /**
     * Slack 설정 삭제 (인스턴스 ID 기준)
     */
    @Transactional
    public void deleteSlackSettingsById(Long instanceId) {
        slackSettingsService.deleteSlackSettingsById(instanceId);
    }

    /**
     * 모든 인스턴스의 Slack 설정 목록 조회
     */
    public List<InstanceSlackSettingsResponse> getAllSlackSettings() {
        return slackSettingsService.getAllSlackSettings();
    }

    // ========== Private Helper Methods ==========

    /**
     * 인스턴스 ID로 엔티티 조회
     *
     * Service 내부 메서드이므로 Entity 반환 가능
     *
     * @param id 인스턴스 ID
     * @return Instance 엔티티
     * @throws NotFoundException 인스턴스를 찾을 수 없는 경우
     */
    private Instance findInstanceById(Long id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));
    }

    /**
     * DTO로부터 Instance 엔티티 생성 (암호화 포함)
     */
    private Instance buildInstanceEntity(InstanceCreateRequest dto) {
        Instance entity = dto.toEntity();

        // 비밀번호 암호화
        String encryptedPassword = aesGcmService.encryptString(dto.getSecretRef());
        entity.setSecretRef(encryptedPassword);

        // SSL 모드 기본값 설정
        entity.setSslmode(DEFAULT_SSL_MODE);

        return entity;
    }

    /**
     * 인스턴스 기본 정보 업데이트
     */
    private void updateBasicInfo(Instance entity, InstanceUpdateRequest req) {
        entity.setInstanceName(req.getInstanceName());
        entity.setHost(req.getHost());
        entity.setPort(req.getPort());
        entity.setUserName(req.getUserName());
    }

    /**
     * 비밀번호 업데이트 (제공된 경우에만)
     */
    private boolean updatePasswordIfProvided(Instance entity, InstanceUpdateRequest req) {
        if (req.getSecretRef() != null && !req.getSecretRef().trim().isEmpty()) {
            String encryptedPassword = aesGcmService.encryptString(req.getSecretRef());
            entity.setSecretRef(encryptedPassword);
            log.info("비밀번호 업데이트 완료");
            return true;
        }
        return false;
    }

    /**
     * 인스턴스 업데이트 실행 및 결과 검증
     */
    private void executeInstanceUpdate(Instance entity, Long id) {
        int updatedRows = instanceRepository.updateInstance(entity);

        if (updatedRows != 1) {
            throw new IllegalStateException("업데이트 대상이 없거나 변경 실패: id=" + id);
        }

        log.info("인스턴스 업데이트 완료. instanceId: {}", id);
    }
}