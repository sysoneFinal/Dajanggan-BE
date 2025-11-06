package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.dto.DatabaseDto;
import com.dajanggan.domain.instance.dto.InstanceWithDatabasesDto;
import com.dajanggan.domain.instance.service.DatabaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instances/{instanceId}/databases")
class DatabaseController {

    private final DatabaseService databaseService;

    // 인스턴스별 DB 목록 조회
    @GetMapping
    public List<DatabaseDto> list(@PathVariable Long instanceId) {
        return databaseService.getByInstanceId(instanceId);
    }
}
