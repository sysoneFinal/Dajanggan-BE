package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.InstanceDto;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;

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

}
