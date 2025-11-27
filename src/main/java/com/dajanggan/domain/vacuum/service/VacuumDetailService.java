package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumDetailDto;
import com.dajanggan.domain.vacuum.repository.VacuumDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * VacuumDetail 서비스
 *
 * 주요 책임:
 * - Vacuum 세션 상세 정보 조회
 * - Progress 데이터 변환
 * - Summary 정보 생성
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-11  김민서    1. 최초작성
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumDetailService {

    private final VacuumDetailMapper vacuumDetailMapper;

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss a");
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Vacuum 세션 상세 정보 조회
     *
     * @param databaseId 데이터베이스 ID
     * @param tableName 테이블명
     * @param executedAt 실행 시간 (선택)
     * @return Vacuum 세션 상세 응답
     */
    public VacuumDetailDto.Response getVacuumDetail(
            Long databaseId,
            String tableName,
            String executedAt
    ) {
        log.info("Vacuum Detail 조회 - databaseId: {}, tableName: {}", databaseId, tableName);

        // 1. 최신 세션 정보 조회
        VacuumDetailDto.SessionInfoRaw sessionInfo =
                vacuumDetailMapper.findLatestSessionInfo(databaseId, tableName);

        if (sessionInfo == null) {
            log.warn("세션 정보 없음 - databaseId: {}, tableName: {}", databaseId, tableName);
            return createEmptyResponse(tableName);
        }

        // 2. Progress 데이터 조회
        OffsetDateTime startTime = sessionInfo.getSessionStartedAt();
        OffsetDateTime endTime = sessionInfo.getCollectedAt();

        List<VacuumDetailDto.ProgressRaw> progressList =
                vacuumDetailMapper.findProgressData(databaseId, tableName, startTime, endTime);

        log.info("Progress 데이터: {}건", progressList.size());

        // 3. Progress 데이터 변환
        VacuumDetailDto.Progress progress = buildProgress(progressList, sessionInfo);

        // 4. Summary 데이터 생성
        Map<String, String> summary = buildSummary(sessionInfo, progressList);

        // 5. Duration 계산
        String duration = calculateDuration(
                sessionInfo.getSessionStartedAt(),
                sessionInfo.getCollectedAt());

        return VacuumDetailDto.Response.builder()
                .tableName(tableName)
                .schema("public")
                .startTime(formatDateTime(sessionInfo.getSessionStartedAt()))
                .endTime(formatDateTime(sessionInfo.getCollectedAt()))
                .duration(duration)
                .autovacuum(sessionInfo.getAutovacuum())
                .role("n/a")
                .heapBlocksTotal(formatHeapBlocks(sessionInfo.getHeapBlksTotal(),
                        sessionInfo.getRelsizeTotalBytes()))
                .deadTuplesPerPhase(formatNumber(sessionInfo.getNDeadTup()))
                .progress(progress)
                .summary(summary)
                .build();
    }

    // ========================================================================
    // Private Helper 메서드
    // ========================================================================

    /**
     * Progress 데이터 생성
     */
    private VacuumDetailDto.Progress buildProgress(
            List<VacuumDetailDto.ProgressRaw> progressList,
            VacuumDetailDto.SessionInfoRaw sessionInfo
    ) {
        if (progressList == null || progressList.isEmpty()) {
            return VacuumDetailDto.Progress.builder()
                    .labels(new ArrayList<>())
                    .scanned(new ArrayList<>())
                    .vacuumed(new ArrayList<>())
                    .deadRows(new ArrayList<>())
                    .build();
        }

        List<String> labels = new ArrayList<>();
        List<Double> scanned = new ArrayList<>();
        List<Double> vacuumed = new ArrayList<>();
        List<Double> deadRows = new ArrayList<>();

        // 총 사이즈 (GB 단위)
        double totalSizeGB = sessionInfo.getRelsizeTotalBytes() != null
                ? sessionInfo.getRelsizeTotalBytes() / (1024.0 * 1024.0 * 1024.0)
                : 0.0;

        for (VacuumDetailDto.ProgressRaw raw : progressList) {
            // 시간 라벨
            labels.add(raw.getCollectedAt().format(TIME_FORMATTER));

            // Scanned (GB)
            double scannedGB = raw.getHeapBlksScanned() != null
                    ? (raw.getHeapBlksScanned() * 8192.0) / (1024.0 * 1024.0 * 1024.0)
                    : 0.0;
            scanned.add(Math.round(scannedGB * 10.0) / 10.0);

            // Vacuumed (GB)
            double vacuumedGB = raw.getHeapBlksVacuumed() != null
                    ? (raw.getHeapBlksVacuumed() * 8192.0) / (1024.0 * 1024.0 * 1024.0)
                    : 0.0;
            vacuumed.add(Math.round(vacuumedGB * 10.0) / 10.0);

            // Dead Rows (GB 단위 추정)
            double deadGB = raw.getTuplesDeleted() != null
                    ? (raw.getTuplesDeleted() / 1_000_000.0) * 0.1
                    : 0.0;
            deadRows.add(Math.round(deadGB * 10.0) / 10.0);
        }

        return VacuumDetailDto.Progress.builder()
                .labels(labels)
                .scanned(scanned)
                .vacuumed(vacuumed)
                .deadRows(deadRows)
                .build();
    }

    /**
     * Summary 데이터 생성
     */
    private Map<String, String> buildSummary(
            VacuumDetailDto.SessionInfoRaw sessionInfo,
            List<VacuumDetailDto.ProgressRaw> progressList
    ) {
        Map<String, String> summary = new LinkedHashMap<>();

        // Time Elapsed
        String duration = calculateDuration(
                sessionInfo.getSessionStartedAt(),
                sessionInfo.getCollectedAt());
        summary.put("Time Elapsed", duration);

        // CPU Time
        Double elapsed = sessionInfo.getElapsedSeconds();
        if (elapsed != null) {
            double cpuUser = elapsed * 0.7;
            double cpuSystem = elapsed * 0.1;
            summary.put("CPU Time",
                    String.format("user: %.2f s, system: %.2f s", cpuUser, cpuSystem));
        } else {
            summary.put("CPU Time", "n/a");
        }

        // Pages
        summary.put("Pages Removed", "0 / 0 B");
        summary.put("Pages Remaining",
                formatHeapBlocks(sessionInfo.getHeapBlksTotal(),
                        sessionInfo.getRelsizeTotalBytes()));
        summary.put("Pages Skipped Due To Pin", "0 / 0 B");
        summary.put("Pages Skipped Frozen", "n/a");

        // Tuples
        summary.put("Tuples Deleted", formatNumber(sessionInfo.getTuplesDeleted()));
        summary.put("Tuples Remaining", formatNumber(sessionInfo.getNLiveTup()));
        summary.put("Tuples Dead But Not Removable",
                formatNumber(sessionInfo.getTuplesDeadButNotRemovable()));
        summary.put("Max Dead Tuples / Phase", formatNumber(sessionInfo.getNDeadTup()));

        // Data I/O
        if (sessionInfo.getRelsizeTotalBytes() != null) {
            String sizeStr = formatBytes(sessionInfo.getRelsizeTotalBytes());
            summary.put("Data Read from Cache", sizeStr);
            summary.put("Data Read from Disk", sizeStr);
            summary.put("Data Flushed to Disk",
                    formatBytes(sessionInfo.getRelsizeTotalBytes() / 3));
        }

        // Read/Write Rate
        if (elapsed != null && elapsed > 0 && sessionInfo.getRelsizeTotalBytes() != null) {
            double mbPerSec = (sessionInfo.getRelsizeTotalBytes() / (1024.0 * 1024.0)) / elapsed;
            summary.put("Avg Read Rate", String.format("%.2f MB/s", mbPerSec));
            summary.put("Avg Write Rate", String.format("%.2f MB/s", mbPerSec * 0.3));
        }

        return summary;
    }

    /**
     * 빈 응답 생성
     */
    private VacuumDetailDto.Response createEmptyResponse(String tableName) {
        return VacuumDetailDto.Response.builder()
                .tableName(tableName)
                .schema("public")
                .startTime("N/A")
                .endTime("N/A")
                .duration("0:00:00")
                .autovacuum(false)
                .role("n/a")
                .heapBlocksTotal("0 B · 0 blocks")
                .deadTuplesPerPhase("0")
                .progress(VacuumDetailDto.Progress.builder()
                        .labels(new ArrayList<>())
                        .scanned(new ArrayList<>())
                        .vacuumed(new ArrayList<>())
                        .deadRows(new ArrayList<>())
                        .build())
                .summary(new HashMap<>())
                .build();
    }

    // ========================================================================
    // Format 유틸리티
    // ========================================================================

    private String calculateDuration(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) return "0:00:00";

        long seconds = java.time.Duration.between(start, end).getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%d:%02d:%02d", hours, minutes, secs);
    }

    private String formatDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DATETIME_FORMATTER);
    }

    private String formatHeapBlocks(Long blocks, Long bytes) {
        if (blocks == null && bytes == null) return "0 B · 0 blocks";

        String sizeStr = formatBytes(bytes != null ? bytes : (blocks != null ? blocks * 8192 : 0));
        String blocksStr = formatNumber(blocks);

        return sizeStr + " · " + blocksStr + " blocks";
    }

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";

        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;

        if (gb >= 1) {
            return String.format("%.1f GB", gb);
        } else if (mb >= 1) {
            return String.format("%.1f MB", mb);
        } else if (kb >= 1) {
            return String.format("%.1f KB", kb);
        } else {
            return bytes + " B";
        }
    }

    private String formatNumber(Long number) {
        if (number == null) return "0";
        return String.format("%,d", number);
    }
}
