package com.dajanggan.domain.instance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DatabaseDto {
    @NotNull
    private Long databaseId;

    @NotNull
    private Long instanceId;

    @NotBlank
    private String databaseName;

    @NotNull
    private Boolean isEnabled = true;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @NotNull
    private Integer connections;

    @NotBlank
    private String sizeBytes;

    @NotBlank
    private String cacheHitRate;

}
