package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumHistoryRepository {

    private final VacuumHistoryMapper historyMapper;

    public List<VacuumHistoryDto.Raw> getVacuumHistoryList(
            Long databaseId, OffsetDateTime start, OffsetDateTime end) {
        return historyMapper.getVacuumHistoryList(databaseId, start, end);
    }

    public Integer getVacuumFrequency(Long databaseId, int hours) {
        return historyMapper.getVacuumFrequency(databaseId, hours);
    }
}