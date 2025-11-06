package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.*;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.exception.ExceptionMessage;
import com.dajanggan.global.exception.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstanceService {
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;

    // (cud는 도메인 반환)
    @Transactional
    public InstanceResponse create(InstanceCreateRequest dto) {
        // 1. request -> entity
        Instance entity = Instance.builder()
                .instanceName(dto.getInstanceName())
                .host(dto.getHost())
                .port(dto.getPort())
                .userName(dto.getUserName())
                .secretRef(dto.getSecretRef())
                .build();
        // 2. db 저장
        instanceRepository.createInstance(entity);

        // 3. entity -> response
        return InstanceResponse.from(entity);
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

    // 수정 (domain 반환)
    @Transactional
    public InstanceResponse update(Long id, @Valid InstanceUpdateRequest req) {
        // 1. DB에서 entity 조회
        Instance entity = instanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND));

        // 2. entity 필드를 직접 수정 (setter 사용)
        entity.setInstanceName(req.getInstanceName());
        entity.setHost(req.getHost());
        entity.setPort(req.getPort());
        entity.setUserName(req.getUserName());
        entity.setSecretRef(req.getSecretRef());

        int rows = instanceRepository.updateInstance(entity);
        if (rows != 1) {
            throw new IllegalStateException("업데이트 대상이 없거나 변경 실패: id=" + id);
        }

        return InstanceResponse.from(entity);
    }

    // 삭제 (domain 반환)
    @Transactional
    public void delete(Long id) {
        // 존재 확인
        if (instanceRepository.findById(id).isEmpty()) {
            throw new NotFoundException(ExceptionMessage.INSTANCE_NOT_FOUND);
        }

        instanceRepository.deleteById(id);
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
