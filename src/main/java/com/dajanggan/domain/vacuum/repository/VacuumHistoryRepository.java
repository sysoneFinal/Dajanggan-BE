package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vacuum History Repository
 * - History 페이지 전용 Repository
 */
@Repository
@RequiredArgsConstructor
public class VacuumHistoryRepository {

    private final VacuumHistoryMapper historyMapper;

    /**
     * Vacuum History 목록 조회
     */
    public List<VacuumHistoryDto.Raw> getVacuumHistoryList(
            LocalDateTime start, LocalDateTime end) {
        return historyMapper.getVacuumHistoryList(start, end);
    }

    /**
     * 특정 테이블의 Vacuum 빈도 조회
     */
    public Integer getVacuumFrequency(Long databaseId, int hours) {
        return historyMapper.getVacuumFrequency(databaseId, hours);
    }
}