package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.DatabaseDto;
import com.dajanggan.domain.instance.dto.InstanceWithDatabasesDto;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

public final class DtoMappers {
    public static DatabaseDto toDatabaseDto(Database s) {
        DatabaseDto d = new DatabaseDto();
        d.setDatabaseId(s.getDatabaseId());
        d.setInstanceId(s.getInstanceId());
        d.setDatabaseName(s.getDatabaseName());
        d.setIsEnabled(Boolean.TRUE.equals(s.getIsEnabled()));
        d.setConnections(s.getConnections());
        d.setSizeBytes(s.getSizeBytes());
        d.setCacheHitRate(s.getCacheHitRate());
        d.setCreatedAt(s.getCreatedAt());
        d.setUpdatedAt(s.getUpdatedAt());
        return d;
    }

    public static InstanceWithDatabasesDto toInstanceWithDbDto(Instance e, List<Database> dbs) {
        InstanceWithDatabasesDto d = new InstanceWithDatabasesDto();
        d.setInstanceId(e.getInstanceId());
        d.setInstanceName(e.getInstanceName());
        d.setHost(e.getHost());
        d.setPort(e.getPort());
        d.setIsEnabled(Boolean.TRUE.equals(e.getIsEnabled()));
        d.setVersion(e.getVersion());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        if (e.getCreatedAt() != null) {
            d.setUptimeMs(Duration.between(e.getCreatedAt(), OffsetDateTime.now()).toMillis());
        }
        d.setDatabases(
                dbs == null ? List.of() :
                        dbs.stream().map(DtoMappers::toDatabaseDto).collect(Collectors.toList())
        );
        return d;
    }
}
