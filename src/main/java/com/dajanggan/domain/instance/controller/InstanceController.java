package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.InstanceDto;
import com.dajanggan.domain.instance.dto.InstanceWithDatabasesDto;
import com.dajanggan.domain.instance.service.DatabaseService;
import com.dajanggan.domain.instance.service.InstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

    // 등록
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@Valid @RequestBody InstanceDto req) {
        Long id = instanceService.register(req);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).body(Map.of("id", id));
    }

    // 하나 조회
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Instance> get(@PathVariable Long id) {
        return ResponseEntity.ok(instanceService.findOne(id));
    }

    // 모두 조회 (기본: 인스턴스만)
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = "!include")
    public ResponseEntity<List<Instance>> list() {
        return ResponseEntity.ok(instanceService.findAll());
    }

    // 모두 조회 (include=databases 인 경우: 인스턴스 + DB 목록까지)
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = "include=databases")
    public ResponseEntity<List<InstanceWithDatabasesDto>> listWithDatabases() {
        return ResponseEntity.ok(instanceService.findAllWithDatabases());
    }

    // 수정
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody InstanceDto req) {
        instanceService.update(id, req);

        return ResponseEntity.noContent().build();
    }

    // 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        instanceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
