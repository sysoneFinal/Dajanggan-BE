package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.dto.InstanceCreateRequest;
import com.dajanggan.domain.instance.dto.InstanceResponse;
import com.dajanggan.domain.instance.dto.InstanceUpdateRequest;
import com.dajanggan.domain.instance.dto.InstanceWithDatabasesDto;
import com.dajanggan.domain.instance.service.InstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceService instanceService;

    // 인스턴스 등록
    @PostMapping
    public ResponseEntity<InstanceResponse> create(@Valid @RequestBody InstanceCreateRequest req) {
        InstanceResponse response = instanceService.create(req);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getInstanceId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    // 연결 테스트 API 추가
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@Valid @RequestBody InstanceCreateRequest req) {
        Map<String, Object> response = instanceService.testConnection(req);
        return ResponseEntity.ok(response);
    }

    // 하나 조회
    @GetMapping(value = "/{id}")
    public ResponseEntity<InstanceResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(instanceService.findOne(id));
    }

    // 모두 조회 (기본: 인스턴스만)
    @GetMapping(params = "!include")
    public ResponseEntity<List<InstanceResponse>> list() {
        return ResponseEntity.ok(instanceService.findAll());
    }

    // 모두 조회 (include=databases 인 경우: 인스턴스 + DB 목록까지)
    @GetMapping(params = "include=databases")
    public ResponseEntity<List<InstanceWithDatabasesDto>> listWithDatabases() {
        return ResponseEntity.ok(instanceService.findAllWithDatabases());
    }

    // 수정
    @PutMapping(value = "/{id}")
    public ResponseEntity<InstanceResponse> update(@PathVariable Long id, @Valid @RequestBody InstanceUpdateRequest req) {
        InstanceResponse response =instanceService.update(id, req);

        return ResponseEntity.ok(response);
    }

    // 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        instanceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
