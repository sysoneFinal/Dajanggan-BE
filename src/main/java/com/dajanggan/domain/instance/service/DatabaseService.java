package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.dto.DatabaseResponse;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final DatabaseRepository databaseRepository;

    // 인스턴스별 DB 목록
    public List<DatabaseResponse> getByInstanceId(Long instanceId) {
        return databaseRepository.findByInstanceId(instanceId);
    }
}
