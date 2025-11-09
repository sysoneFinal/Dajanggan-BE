package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import com.dajanggan.domain.vacuum.repository.VacuumRiskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumRiskService {

    private final VacuumRiskMapper mapper;

    public VacuumRiskDto.Response getRiskData(int hours) {
        OffsetDateTime endTime = OffsetDateTime.now(ZoneId.systemDefault());
        OffsetDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum Risk 조회: {} ~ {}", startTime, endTime);

        try {
            return VacuumRiskDto.Response.builder()
                    .blockers(buildBlockersChart(startTime, endTime))
                    .wraparound(buildWraparoundChart())
                    .bloat(buildTopBloatTables(3))
                    .vacuumblockers(buildVacuumBlockers())
                    .build();
        } catch (Exception e) {
            log.error("Risk 데이터 조회 실패", e);
            throw new RuntimeException("Risk 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    private VacuumRiskDto.Chart buildBlockersChart(OffsetDateTime start, OffsetDateTime end) {
        try {
            List<VacuumRiskDto.BlockersPerHourRaw> blockers = mapper.getBlockersPerHour(start, end, 24);

            log.info("Blockers 조회 결과: {} 건", blockers != null ? blockers.size() : 0);

            if (blockers == null || blockers.isEmpty()) {
                log.warn("Blockers 데이터가 비어있습니다.");
                return VacuumRiskDto.Chart.builder()
                        .data(Collections.singletonList(Collections.emptyList()))
                        .labels(Collections.emptyList())
                        .build();
            }

            List<String> labels = new ArrayList<>();
            List<Double> data = new ArrayList<>();

            for (VacuumRiskDto.BlockersPerHourRaw b : blockers) {
                labels.add(b.getHourLabel() != null ? b.getHourLabel() : "N/A");
                data.add(b.getBlockersCount() != null ? b.getBlockersCount().doubleValue() : 0.0);
            }

            log.debug("Blockers Chart - labels: {}, data: {}", labels.size(), data.size());

            return VacuumRiskDto.Chart.builder()
                    .data(Collections.singletonList(data))
                    .labels(labels)
                    .build();
        } catch (Exception e) {
            log.error("Blockers Chart 생성 실패", e);
            return VacuumRiskDto.Chart.builder()
                    .data(Collections.singletonList(Collections.emptyList()))
                    .labels(Collections.emptyList())
                    .build();
        }
    }

    private VacuumRiskDto.Chart buildWraparoundChart() {
        try {
            List<VacuumRiskDto.WraparoundProgressRaw> wraparound = mapper.getWraparoundProgress();

            log.info("Wraparound 조회 결과: {} 건", wraparound != null ? wraparound.size() : 0);

            if (wraparound == null || wraparound.isEmpty()) {
                log.warn("Wraparound 데이터가 비어있습니다.");
                return VacuumRiskDto.Chart.builder()
                        .data(Collections.singletonList(Collections.emptyList()))
                        .labels(Collections.emptyList())
                        .build();
            }

            List<String> labels = new ArrayList<>();
            List<Double> data = new ArrayList<>();

            for (VacuumRiskDto.WraparoundProgressRaw w : wraparound) {
                labels.add("DB-" + (w.getDatabaseId() != null ? w.getDatabaseId() : "N/A"));
                data.add(w.getWraparoundProgressPct() != null ? w.getWraparoundProgressPct() : 0.0);
            }

            log.debug("Wraparound Chart - labels: {}, data: {}", labels.size(), data.size());

            return VacuumRiskDto.Chart.builder()
                    .data(Collections.singletonList(data))
                    .labels(labels)
                    .build();
        } catch (Exception e) {
            log.error("Wraparound Chart 생성 실패", e);
            return VacuumRiskDto.Chart.builder()
                    .data(Collections.singletonList(Collections.emptyList()))
                    .labels(Collections.emptyList())
                    .build();
        }
    }

    private List<VacuumRiskDto.TopBloatTable> buildTopBloatTables(int limit) {
        try {
            List<VacuumRiskDto.TopBloatRaw> rawList = mapper.getTopBloatTables(limit);

            log.info("Top Bloat 조회 결과: {} 건", rawList != null ? rawList.size() : 0);

            if (rawList == null || rawList.isEmpty()) {
                log.warn("Top Bloat 데이터가 비어있습니다.");
                return Collections.emptyList();
            }

            return rawList.stream()
                    .map(this::convertToTopBloatDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Top Bloat Tables 조회 실패", e);
            return Collections.emptyList();
        }
    }

    private List<VacuumRiskDto.VacuumBlocker> buildVacuumBlockers() {
        try {
            List<VacuumRiskDto.VacuumBlockerDetailRaw> rawList = mapper.getVacuumBlockers();

            log.info("Vacuum Blockers 조회 결과: {} 건", rawList != null ? rawList.size() : 0);

            if (rawList == null || rawList.isEmpty()) {
                log.warn("Vacuum Blockers 데이터가 비어있습니다.");
                return Collections.emptyList();
            }

            return rawList.stream()
                    .map(this::convertToVacuumBlockerDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Vacuum Blockers 조회 실패", e);
            return Collections.emptyList();
        }
    }

    private VacuumRiskDto.TopBloatTable convertToTopBloatDto(VacuumRiskDto.TopBloatRaw raw) {
        String tableName = raw.getTableName() != null ? raw.getTableName() : "unknown";
        return VacuumRiskDto.TopBloatTable.builder()
                .table(tableName)
                .bloat(formatBloatPct(raw.getBloatRatio()))
                .deadTuple(formatTuples(raw.getDeadTuples()))
                .build();
    }

    private VacuumRiskDto.VacuumBlocker convertToVacuumBlockerDto(VacuumRiskDto.VacuumBlockerDetailRaw raw) {
        String tableName = raw.getTableName() != null ? raw.getTableName() : "unknown";
        return VacuumRiskDto.VacuumBlocker.builder()
                .table(tableName)
                .pid(String.valueOf(raw.getPid() != null ? raw.getPid() : 0))
                .lockType(raw.getLockType() != null ? raw.getLockType() : "Unknown")
                .txAge(formatDuration(raw.getTransactionAge()))
                .blocked_seconds(formatDuration(raw.getBlockDuration()))
                .status(raw.getQueryState() != null ? raw.getQueryState() : "unknown")
                .build();
    }

    private String formatDuration(Long seconds) {
        if (seconds == null || seconds == 0) return "0s";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0 && minutes > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh", hours);
        } else if (minutes > 0) {
            return String.format("%dm", minutes);
        } else {
            return String.format("%ds", secs);
        }
    }

    private String formatBloatPct(Double ratio) {
        if (ratio == null) return "0.0%";
        return String.format("%.1f%%", ratio * 100);
    }

    private String formatTuples(Long count) {
        if (count == null || count == 0) return "0";
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }
}