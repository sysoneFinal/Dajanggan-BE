package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.DatabaseDto;
import com.dajanggan.domain.instance.dto.InstanceDto;
import com.dajanggan.domain.instance.dto.InstanceWithDatabasesDto;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;

    @Transactional
    public Long register(InstanceDto dto) {
        Instance entity = Instance.builder()
                .instanceName(dto.getInstanceName())
                .host(dto.getHost())
                .dbname(dto.getDbname())
                .port(dto.getPort())
                .username(dto.getUsername())
                .secretRef(dto.getSecretRef())
                .sslmode(dto.getSslmode())
                .isEnabled(dto.getIsEnabled())
                .slackEnabled(dto.getSlackEnabled())
                .slackChannel(dto.getSlackChannel())
                .slackMention(dto.getSlackMention())
                .slackWebhookUrl(dto.getSlackWebhookUrl())
                .collectionInterval(dto.getCollectionInterval())
                .build();

        instanceRepository.insertInstance(entity);
        return entity.getInstanceId();
    }

    // 검색
    public Instance findOne(Long id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("인스턴스를 찾을 수 없습니다. id=" + id));
    }

    // 조회
    public List<Instance> findAll() {
        return instanceRepository.findAll();
    }

    // 수정
    @Transactional
    public void update(Long id, InstanceDto dto) {
        Instance current = findOne(id);
        Instance changed = Instance.builder()
                .instanceId(current.getInstanceId())
                .instanceName(dto.getInstanceName())
                .host(dto.getHost())
                .dbname(dto.getDbname())
                .port(dto.getPort())
                .username(dto.getUsername())
                .secretRef(dto.getSecretRef())
                .sslmode(dto.getSslmode())
                .isEnabled(dto.getIsEnabled())
                .slackEnabled(dto.getSlackEnabled())
                .slackChannel(dto.getSlackChannel())
                .slackMention(dto.getSlackMention())
                .slackWebhookUrl(dto.getSlackWebhookUrl())
                .collectionInterval(dto.getCollectionInterval())
                .build();
        instanceRepository.updateInstance(changed);
    }

    // 삭제
    @Transactional
    public void delete(Long id) {
        instanceRepository.deleteById(id);
    }

    public List<InstanceWithDatabasesDto> findAllWithDatabases() {
        // ✅ 1) 인스턴스 조회 (엔티티)
        List<Instance> instances = instanceRepository.findAll();
        if (instances.isEmpty()) return List.of();

        // 2) id 모으기
        List<Long> ids = instances.stream()
                .map(Instance::getInstanceId)
                .toList();

        // 3) 모든 DB 한 번에 조회 (엔티티 반환 가정)
        //    없다면 N+1로 findByInstanceId(...)를 루프 돌려도 됨
        var allDbs = databaseRepository.findByInstanceIds(ids);

        // 4) instanceId -> List<Database> 그룹핑
        Map<Long, List<com.dajanggan.domain.instance.domain.Database>> dbMap =
                allDbs.stream().collect(Collectors.groupingBy(com.dajanggan.domain.instance.domain.Database::getInstanceId));

        // 5) 합본 DTO 조립 (DtoMappers 활용)
        return instances.stream()
                .map(i -> DtoMappers.toInstanceWithDbDto(i, dbMap.getOrDefault(i.getInstanceId(), List.of())))
                .toList();
    }

    public List<DatabaseDto> findDatabases(Long instanceId) {
        return databaseRepository.findByInstanceId(instanceId);
        // 엔티티 반환인 경우:
        // return databaseRepository.findByInstanceId(instanceId)
        //         .stream().map(DtoMappers::toDatabaseDto).toList();
    }
}
