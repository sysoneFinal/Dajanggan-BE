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
        int hours = request.getHours() != null ? request.getHours() : 168; // 기본 7일
        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum History 조회: databaseId={}, {} ~ {}, status={}",
                request.getDatabaseId(), startTime, endTime, request.getStatus());

        List<VacuumHistoryDto.Raw> rawList = vacuumHistoryMapper.getVacuumHistoryList(
                request.getDatabaseId(), startTime, endTime);

        return rawList.stream()
                .map(raw -> convertToHistoryDto(raw, hours))
                .filter(dto -> filterByStatus(dto, request.getStatus()))
                .collect(Collectors.toList());
    }

    private VacuumHistoryDto.Response convertToHistoryDto(VacuumHistoryDto.Raw raw, int hours) {
        // 빈도 계산
        Integer frequency = vacuumHistoryMapper.getVacuumFrequency(raw.getDatabaseId(), hours);
        String frequencyStr = formatFrequency(frequency, hours);

        // 상태 판단 (bloat 비율 > 5% 또는 dead tuples > 100K → 주의)
        String status = determineStatus(raw);

        return VacuumHistoryDto.Response.builder()
                .table(raw.getTableName() != null ? raw.getTableName() : "DB_" + raw.getDatabaseId())
                .lastVacuum(formatDateTime(raw.getLastVacuum()))
                .lastAutovacuum(formatDateTime(raw.getLastAutovacuum()))
                .deadTuples(formatTuples(raw.getDeadTuples()))
                .modSinceAnalyze(formatTuples(raw.getModSinceAnalyze()))
                .bloatRatio(formatBloatRatio(raw.getBloatRatio()))
                .frequency(frequencyStr)
                .status(status)
                .build();
    }

    private boolean filterByStatus(VacuumHistoryDto.Response dto, String statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return true;
        }
        return dto.getStatus().equals(statusFilter);
    }

    private String determineStatus(VacuumHistoryDto.Raw raw) {
        // bloat 비율 > 5% 또는 dead tuples > 100K → 주의
        if (raw.getBloatRatio() != null && raw.getBloatRatio() > 0.05) {
            return "주의";
        }
        if (raw.getDeadTuples() != null && raw.getDeadTuples() > 100_000) {
            return "주의";
        }
        return "정상";
    }

    private String formatFrequency(Integer count, int hours) {
        if (count == null || count == 0) {
            return "0회";
        }

        // 일 단위 빈도 계산
        if (hours >= 24) {
            int perDay = (int) Math.round(count * 24.0 / hours);
            return perDay + "회/일";
        }

        return count + "회/" + hours + "h";
    }

    private String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) return "-";
        return timestamp.toLocalDateTime().format(DATETIME_FORMATTER);
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
