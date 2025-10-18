package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.dto.InstanceDto;
import com.dajanggan.domain.instance.service.InstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceService service;

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> register(@Validated @RequestBody InstanceDto req) {
        Long id = service.register(req);
        return ResponseEntity.ok().body(java.util.Map.of("id", id));
    }
}
