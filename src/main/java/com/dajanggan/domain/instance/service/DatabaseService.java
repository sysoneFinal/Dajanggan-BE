package com.dajanggan.domain.instance.service;


import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.DatabaseDto;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final DatabaseRepository databaseRepository;

    public List<DatabaseDto> findAll(){
        return databaseRepository.findAll();
    }

    // 인스턴스별 DB 목록
    public List<DatabaseDto> getByInstanceId(Long instanceId) {
        return databaseRepository.findByInstanceId(instanceId);
    }
}
