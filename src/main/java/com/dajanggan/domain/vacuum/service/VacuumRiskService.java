package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import com.dajanggan.domain.vacuum.repository.VacuumRiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vacuum Risk Service
 * - Risk 페이지 전용 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumRiskService {

    private final VacuumRiskRepository repository;

    /**
     * Vacuum Risk 페이지 데이터 조회
     */
    public VacuumRiskDto.Response getRiskData(int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum Risk 조회: {} ~ {}", startTime, endTime);

        return VacuumRiskDto.Response.builder()
                .blockers(buildBlockersChart(startTime, endTime))
                .wraparound(buildWraparoundChart())
                .bloat(buildTopBloatTables(3))
                .vacuumblockers(buildVacuumBlockers())
                .build();
    }

    /**
     * Blockers per Hour 차트
     */
    private VacuumRiskDto.Chart buildBlockersChart(LocalDateTime start, LocalDateTime end) {
        List<VacuumRiskDto.BlockersPerHourRaw> blockers = repository.getBlockersPerHour(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        for (VacuumRiskDto.BlockersPerHourRaw b : blockers) {
            labels.add(b.getHourLabel());
            data.add(b.getBlockersCount() != null ? b.getBlockersCount().doubleValue() : 0.0);
        }

        return VacuumRiskDto.Chart.builder()
                .data(Collections.singletonList(data))
                .labels(labels)
                .build();
    }

    /**
     * Wraparound Progress 차트
     */
    private VacuumRiskDto.Chart buildWraparoundChart() {
        List<VacuumRiskDto.WraparoundProgressRaw> wraparound = repository.getWraparoundProgress();

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        for (VacuumRiskDto.WraparoundProgressRaw w : wraparound) {
            labels.add(String.valueOf(w.getDatabaseId()));
            data.add(w.getWraparoundProgressPct() != null ? w.getWraparoundProgressPct() : 0.0);
        }

        return VacuumRiskDto.Chart.builder()
                .data(Collections.singletonList(data))
                .labels(labels)
                .build();
    }

    /**
     * Top Bloat Tables 목록
     */
    private List<VacuumRiskDto.TopBloatTable> buildTopBloatTables(int limit) {
        List<VacuumRiskDto.TopBloatRaw> rawList = repository.getTopBloatTables(limit);

        return rawList.stream()
                .map(this::convertToTopBloatDto)
                .collect(Collectors.toList());
    }

    /**
     * Vacuum Blockers 목록
     */
    private List<VacuumRiskDto.VacuumBlocker> buildVacuumBlockers() {
        List<VacuumRiskDto.VacuumBlockerDetailRaw> rawList = repository.getVacuumBlockers();

        return rawList.stream()
                .map(this::convertToVacuumBlockerDto)
                .collect(Collectors.toList());
    }

    /**
     * Raw → TopBloatTable 변환
     */
    private VacuumRiskDto.TopBloatTable convertToTopBloatDto(VacuumRiskDto.TopBloatRaw raw) {
        return VacuumRiskDto.TopBloatTable.builder()
                .table(String.valueOf(raw.getDatabaseId()))
                .bloat(formatBloatPct(raw.getBloatRatio()))
                .deadTuple(formatTuples(raw.getDeadTuples()))
                .build();
    }

    /**
     * Raw → VacuumBlocker 변환
     */
    private VacuumRiskDto.VacuumBlocker convertToVacuumBlockerDto(VacuumRiskDto.VacuumBlockerDetailRaw raw) {
        return VacuumRiskDto.VacuumBlocker.builder()
                .table(String.valueOf(raw.getDatabaseId()))
                .pid(String.valueOf(raw.getPid()))
                .lockType(raw.getLockType() != null ? raw.getLockType() : "Unknown")
                .txAge(formatDuration(raw.getTransactionAge()))
                .blocked_seconds(formatDuration(raw.getBlockDuration()))
                .status(raw.getQueryState() != null ? raw.getQueryState() : "unknown")
                .build();
    }

    /**
     * Duration 포맷팅 (초 → "Xh Ym" 형식)
     */
    private String formatDuration(Long seconds) {
        if (seconds == null || seconds == 0) return "0s";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0 && minutes > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh", hours);
        } else if (minutes > 0) {
            return String.format("%dm", minutes);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Bloat 비율 포맷팅
     */
    private String formatBloatPct(Double ratio) {
        if (ratio == null) return "0.0%";
        return String.format("%.1f%%", ratio * 100);
    }

    /**
     * 바이트 크기 포맷팅
     */
    private String formatBytes(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes >= 1_073_741_824) return String.format("%.0f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576) return String.format("%.0f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024) return String.format("%.0f KB", bytes / 1_024.0);
        return bytes + " B";
    }

    /**
     * Tuples 포맷팅
     */
    private String formatTuples(Long count) {
        if (count == null) return "0";
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.0fK", count / 1_000.0);
        return String.valueOf(count);
    }
}