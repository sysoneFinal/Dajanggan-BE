package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumDetailRepository {

    private final VacuumDetailMapper detailMapper;

    public VacuumDetailDto.SessionInfoRaw findLatestSessionInfo(
            Long databaseId, String tableName) {
        return detailMapper.findLatestSessionInfo(databaseId, tableName);
    }

    public List<VacuumDetailDto.ProgressRaw> findProgressData(
            Long databaseId, String tableName,
            OffsetDateTime startTime, OffsetDateTime endTime) {
        return detailMapper.findProgressData(databaseId, tableName, startTime, endTime);
    }

    public List<String> findTableList(Long databaseId) {
        return detailMapper.findTableList(databaseId);
    }
}