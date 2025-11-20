package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import com.dajanggan.domain.vacuum.repository.VacuumHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumHistoryService {

    private final VacuumHistoryMapper vacuumHistoryMapper;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<VacuumHistoryDto.Response> getVacuumHistory(VacuumHistoryDto.Request request) {
        int hours = request.getHours() != null ? request.getHours() : 168;
        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum History 조회: databaseId={}, {} ~ {}",
                request.getDatabaseId(), startTime, endTime);

        List<VacuumHistoryDto.Entity> historyList = vacuumHistoryMapper.getVacuumHistoryFromTable(
                request.getDatabaseId(), startTime, endTime);

        return historyList.stream()
                .map(this::convertToResponse)
                .filter(dto -> filterByStatus(dto, request.getStatus()))
                .collect(Collectors.toList());
    }

    private VacuumHistoryDto.Response convertToResponse(VacuumHistoryDto.Entity entity) {
        return VacuumHistoryDto.Response.builder()
                .table(entity.getTableName())
                .executedAt(formatDateTime(entity.getExecutedAt()))
                .vacuumType(entity.getVacuumType())
                .durationSeconds(entity.getDurationSeconds())
                .deadTuples(formatTuples(entity.getDeadTuplesBefore()))
                .bloatRatio(formatBloatRatio(entity.getBloatRatioBefore()))
                .status(entity.getStatus())
                .build();
    }

    private boolean filterByStatus(VacuumHistoryDto.Response dto, String statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return true;
        }
        return dto.getStatus().equals(statusFilter);
    }

    private String formatDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DATETIME_FORMATTER);
    }

    private String formatBloatRatio(Double ratio) {
        if (ratio == null) return "0.0%";
        return String.format("%.1f%%", ratio * 100);
    }

    private String formatTuples(Long count) {
        if (count == null) return "0";
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.0fK", count / 1_000.0);
        return String.valueOf(count);
    }
}