package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.dto.InstanceDto;
import com.dajanggan.domain.instance.service.InstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceService instancesService;

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> register(@Validated @RequestBody InstanceDto req) {
        Long id = instancesService.register(req);
        // 201 Created + Location 헤더
        return ResponseEntity
                .created(URI.create("/api/instances/" + id))
                .body(Map.of("id", id));    }
}
