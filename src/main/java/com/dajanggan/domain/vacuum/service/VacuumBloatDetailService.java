package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDetailDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import com.dajanggan.domain.vacuum.repository.VacuumBloatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacuumBloatDetailService {

    private final VacuumBloatRepository bloatRepository;

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 전체 대시보드 데이터 조회
     */
    public VacuumBloatDetailDto.Response getBloatDetail(
            Long databaseId, Long instanceId, String tableName) {

        log.info("🔍 Fetching bloat detail: db={}, instance={}, table={}",
                databaseId, instanceId, tableName);

        return VacuumBloatDetailDto.Response.builder()
                .kpi(getKpi(databaseId, instanceId, tableName))
                .bloatTrend(getBloatTrend(databaseId, instanceId, tableName, 30))
                .deadTuplesTrend(getDeadTuplesTrend(databaseId, instanceId, tableName, 30))
                .indexBloatTrend(getIndexBloatTrend(databaseId, instanceId, tableName, 30))
                .build();
    }

    /**
     * KPI 데이터 (Raw 데이터 최신값)
     */
    public VacuumBloatDetailDto.Kpi getKpi(Long databaseId, Long instanceId, String tableName) {
        VacuumRawMetricDto latest = bloatRepository.getLatestTableMetrics(
                databaseId, instanceId, tableName);

        if (latest == null) {
            log.warn("⚠️ No latest metrics found for table: {}", tableName);
            return VacuumBloatDetailDto.Kpi.builder()
                    .bloatPct("0%")
                    .tableSize("0 B")
                    .wastedSpace("0 B")
                    .build();
        }

        // Bloat %
        String bloatPct = "0%";
        if (latest.getBloatRatio() != null) {
            bloatPct = String.format("%.1f%%", latest.getBloatRatio() * 100);
        }

        // Table Size
        String tableSize = formatBytes(latest.getRelsizeTotalBytes());

        // Wasted Space (Bloat Bytes)
        String wastedSpace = formatBytes(latest.getBloatBytes());

        log.info("📊 KPI: bloatPct={}, tableSize={}, wastedSpace={}",
                bloatPct, tableSize, wastedSpace);

        return VacuumBloatDetailDto.Kpi.builder()
                .bloatPct(bloatPct)
                .tableSize(tableSize)
                .wastedSpace(wastedSpace)
                .build();
    }

    /**
     * Bloat % Trend (Raw 데이터)
     */
    public VacuumBloatDetailDto.BloatTrend getBloatTrend(
            Long databaseId, Long instanceId, String tableName, int days) {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(days);

        List<VacuumRawMetricDto> data = bloatRepository.getTableBloatTrend(
                databaseId, instanceId, tableName, startDate, now);

        List<Double> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // null 체크 추가! ✨
        if (data == null || data.isEmpty()) {
            log.warn("⚠️ No bloat trend data for table: {}", tableName);
            return VacuumBloatDetailDto.BloatTrend.builder()
                    .data(values)
                    .labels(labels)
                    .build();
        }

        // 날짜별 그룹화 (일별 평균) - null 필터링 추가! ✨
        Map<String, List<VacuumRawMetricDto>> groupedByDate = data.stream()
                .filter(m -> m != null && m.getCollectedAt() != null)  // ✨ null 체크!
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(LABEL_FORMATTER)));

        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());

                    // 일별 평균 bloat ratio
                    double avgRatio = entry.getValue().stream()
                            .filter(m -> m != null && m.getBloatRatio() != null)  // ✨ null 체크!
                            .mapToDouble(VacuumRawMetricDto::getBloatRatio)
                            .average()
                            .orElse(0.0);

                    values.add(avgRatio * 100); // 퍼센트로 변환
                });

        log.info("📈 Bloat Trend: {} data points", values.size());

        return VacuumBloatDetailDto.BloatTrend.builder()
                .data(values)
                .labels(labels)
                .build();
    }

    /**
     * Dead Tuples Trend (Raw 데이터)
     */
    public VacuumBloatDetailDto.DeadTuplesTrend getDeadTuplesTrend(
            Long databaseId, Long instanceId, String tableName, int days) {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(days);

        List<VacuumRawMetricDto> data = bloatRepository.getTableBloatTrend(
                databaseId, instanceId, tableName, startDate, now);

        List<Long> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // null 체크 추가! ✨
        if (data == null || data.isEmpty()) {
            log.warn("⚠️ No dead tuples trend data for table: {}", tableName);
            return VacuumBloatDetailDto.DeadTuplesTrend.builder()
                    .data(values)
                    .labels(labels)
                    .build();
        }

        // 날짜별 그룹화 - null 필터링 추가! ✨
        Map<String, List<VacuumRawMetricDto>> groupedByDate = data.stream()
                .filter(m -> m != null && m.getCollectedAt() != null)  // ✨ null 체크!
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(LABEL_FORMATTER)));

        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());

                    // 일별 평균 dead tuples
                    long avgDeadTuples = (long) entry.getValue().stream()
                            .filter(m -> m != null && m.getNDeadTup() != null)  // ✨ null 체크!
                            .mapToLong(VacuumRawMetricDto::getNDeadTup)
                            .average()
                            .orElse(0.0);

                    values.add(avgDeadTuples);
                });

        log.info("📈 Dead Tuples Trend: {} data points", values.size());

        return VacuumBloatDetailDto.DeadTuplesTrend.builder()
                .data(values)
                .labels(labels)
                .build();
    }

    /**
     * Index Bloat Trend (Raw 데이터 - JSON 파싱)
     */
    public VacuumBloatDetailDto.IndexBloatTrend getIndexBloatTrend(
            Long databaseId, Long instanceId, String tableName, int days) {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(days);

        List<VacuumRawMetricDto> data = bloatRepository.getTableBloatTrend(
                databaseId, instanceId, tableName, startDate, now);

        // index_bloat_info JSON 파싱
        // 구조: [{"name": "idx_users_email", "bytes": 12345, "ratio": 0.05}, ...]

        Map<String, List<Double>> indexDataMap = new HashMap<>();
        List<String> labels = new ArrayList<>();

        // null 체크 추가! ✨
        if (data == null || data.isEmpty()) {
            log.warn("⚠️ No index bloat trend data for table: {}", tableName);
            return VacuumBloatDetailDto.IndexBloatTrend.builder()
                    .data(new ArrayList<>())
                    .labels(labels)
                    .names(new ArrayList<>())
                    .build();
        }

        // 날짜별 그룹화 - null 필터링 추가! ✨
        Map<String, List<VacuumRawMetricDto>> groupedByDate = data.stream()
                .filter(m -> m != null && m.getCollectedAt() != null)  // ✨ null 체크!
                .collect(Collectors.groupingBy(m ->
                        m.getCollectedAt().toLocalDate().format(LABEL_FORMATTER)));

        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    labels.add(entry.getKey());

                    // 각 날짜의 인덱스 bloat 정보 파싱
                    entry.getValue().stream()
                            .filter(m -> m != null && m.getIndexBloatInfo() != null)  // ✨ null 체크!
                            .forEach(m -> {
                                // TODO: JSON 파싱 로직
                                // ObjectMapper를 사용하여 index_bloat_info 파싱
                                // 현재는 빈 데이터 반환
                            });
                });

        // 인덱스별 데이터 시리즈 생성
        List<String> indexNames = new ArrayList<>(indexDataMap.keySet());
        List<List<Double>> seriesData = indexNames.stream()
                .map(indexDataMap::get)
                .collect(Collectors.toList());

        log.info("📈 Index Bloat Trend: {} indexes, {} data points",
                indexNames.size(), labels.size());

        return VacuumBloatDetailDto.IndexBloatTrend.builder()
                .data(seriesData)
                .labels(labels)
                .names(indexNames)
                .build();
    }

    /**
     * 테이블 목록 조회
     */
    public List<String> getTableList(Long databaseId, Long instanceId) {
        log.info("🔍 Fetching table list for db={}, instance={}", databaseId, instanceId);

        List<String> tables = bloatRepository.getTableList(databaseId, instanceId);

        log.info("✅ Found {} tables", tables.size());
        return tables != null ? tables : new ArrayList<>();  // ✨ null 체크!
    }

    // ========== 유틸리티 메서드 ==========

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";

        long absBytes = Math.abs(bytes);

        if (absBytes < 1024) return absBytes + " B";

        int exp = (int) (Math.log(absBytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";

        return String.format("%.1f %sB", absBytes / Math.pow(1024, exp), pre);
    }
}