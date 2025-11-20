package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.domain.QueryMetricsRaw;
import com.dajanggan.domain.query.dto.QueryMetricsRawDto;
import com.dajanggan.domain.query.repository.QueryMetricsRawRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 쿼리 메트릭스 원시 데이터 서비스 구현체
 * Singleton으로 관리됨 (Spring의 기본 빈 스코프)
 *
 * ✅ 수정사항:
 * - getQueryMetricsByDatabaseIdAndDays 메서드 추가
 *
 * @author 이해든
 */
@Service
@Transactional(readOnly = true)
public class QueryMetricsRawServiceImpl implements QueryMetricsRawService {

    private static final Logger logger = LoggerFactory.getLogger(QueryMetricsRawServiceImpl.class);

    private final QueryMetricsRawRepository queryMetricsRawRepository;

    /**
     * 생성자 주입 (권장 방식)
     * Spring이 자동으로 Singleton으로 관리
     */
    public QueryMetricsRawServiceImpl(QueryMetricsRawRepository queryMetricsRawRepository) {
        this.queryMetricsRawRepository = queryMetricsRawRepository;
        logger.info("QueryMetricsRawServiceImpl 인스턴스 생성됨 (Singleton)");
    }

    /**
     * 전체 쿼리 메트릭스 조회
     */
    @Override
    public List<QueryMetricsRawDto> getAllQueryMetrics() {
        logger.info("전체 쿼리 메트릭스 조회 시작");

        try {
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findAll();
            List<QueryMetricsRawDto> dtos = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("전체 쿼리 메트릭스 조회 완료: {} 건", dtos.size());
            return dtos;

        } catch (Exception e) {
            logger.error("전체 쿼리 메트릭스 조회 중 오류 발생", e);
            throw new RuntimeException("쿼리 메트릭스 조회 실패", e);
        }
    }

    /**
     * ID로 쿼리 메트릭스 조회
     */
    @Override
    public QueryMetricsRawDto getQueryMetricById(Long queryMetricId) {
        logger.info("쿼리 메트릭스 조회 시작: ID = {}", queryMetricId);

        if (queryMetricId == null) {
            logger.warn("queryMetricId가 null입니다");
            throw new IllegalArgumentException("queryMetricId는 필수입니다");
        }

        try {
            QueryMetricsRaw entity = queryMetricsRawRepository.findById(queryMetricId);

            if (entity == null) {
                logger.warn("쿼리 메트릭스를 찾을 수 없습니다: ID = {}", queryMetricId);
                return null;
            }

            QueryMetricsRawDto dto = QueryMetricsRawDto.from(entity);
            logger.info("쿼리 메트릭스 조회 완료: ID = {}", queryMetricId);
            return dto;

        } catch (Exception e) {
            logger.error("쿼리 메트릭스 조회 중 오류 발생: ID = {}", queryMetricId, e);
            throw new RuntimeException("쿼리 메트릭스 조회 실패", e);
        }
    }

    /**
     * 데이터베이스 ID로 쿼리 메트릭스 목록 조회 (전체)
     */
    @Override
    public List<QueryMetricsRawDto> getQueryMetricsByDatabaseId(Long databaseId) {
        logger.info("데이터베이스별 쿼리 메트릭스 조회 시작: databaseId = {}", databaseId);

        if (databaseId == null) {
            logger.warn("databaseId가 null입니다");
            throw new IllegalArgumentException("databaseId는 필수입니다");
        }

        try {
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findByDatabaseId(databaseId);
            List<QueryMetricsRawDto> dtos = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("데이터베이스별 쿼리 메트릭스 조회 완료: databaseId = {}, 조회 건수 = {}",
                    databaseId, dtos.size());
            return dtos;

        } catch (Exception e) {
            logger.error("데이터베이스별 쿼리 메트릭스 조회 중 오류 발생: databaseId = {}", databaseId, e);
            throw new RuntimeException("쿼리 메트릭스 조회 실패", e);
        }
    }

    /**
     * ✅ 신규: 데이터베이스 ID와 기간으로 쿼리 메트릭스 조회
     */
    @Override
    public List<QueryMetricsRawDto> getQueryMetricsByDatabaseIdAndDays(Long databaseId, Integer days) {
        logger.info("📊 데이터베이스별 기간 조회 - databaseId: {}, days: {}", databaseId, days);

        if (databaseId == null) {
            logger.warn("databaseId가 null입니다");
            throw new IllegalArgumentException("databaseId는 필수입니다");
        }

        if (days == null || days < 0) {
            logger.warn("days가 유효하지 않습니다: {}", days);
            days = 1; // 기본값 1일
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("databaseId", databaseId);
            params.put("days", days);

            // ✅ Repository는 Entity를 반환하므로 DTO로 변환 필요
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findByDatabaseIdAndDays(params);
            List<QueryMetricsRawDto> result = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("✅ 조회 완료: databaseId = {}, days = {}, 조회 건수 = {}",
                    databaseId, days, result.size());

            return result;

        } catch (Exception e) {
            logger.error("❌ 데이터베이스별 기간 조회 중 오류 발생: databaseId = {}, days = {}", databaseId, days, e);
            throw new RuntimeException("쿼리 메트릭스 조회 실패", e);
        }
    }

    /**
     * 최근 N분간의 쿼리 메트릭스 조회
     */
    @Override
    public List<QueryMetricsRawDto> getRecentMetrics(Long databaseId, Integer minutes) {
        logger.info("최근 {}분 데이터 조회 시작: databaseId = {}", minutes, databaseId);

        if (databaseId == null) {
            logger.warn("databaseId가 null입니다");
            throw new IllegalArgumentException("databaseId는 필수입니다");
        }

        if (minutes == null || minutes <= 0) {
            logger.warn("minutes가 유효하지 않습니다: {}", minutes);
            minutes = 5; // 기본값
        }

        try {
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findRecentByDatabaseId(databaseId, minutes);
            List<QueryMetricsRawDto> dtos = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("최근 {}분 데이터 조회 완료: databaseId = {}, 조회 건수 = {}",
                    minutes, databaseId, dtos.size());
            return dtos;

        } catch (Exception e) {
            logger.error("최근 {}분 데이터 조회 중 오류 발생: databaseId = {}", minutes, databaseId, e);
            throw new RuntimeException("최근 데이터 조회 실패", e);
        }
    }

    /**
     * 쿼리 타입별 조회
     */
    @Override
    public List<QueryMetricsRawDto> getQueryMetricsByType(String queryType) {
        logger.info("쿼리 타입별 메트릭스 조회 시작: queryType = {}", queryType);

        if (queryType == null || queryType.trim().isEmpty()) {
            logger.warn("queryType이 비어있습니다");
            throw new IllegalArgumentException("queryType은 필수입니다");
        }

        try {
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findByQueryType(queryType);
            List<QueryMetricsRawDto> dtos = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("쿼리 타입별 메트릭스 조회 완료: queryType = {}, 조회 건수 = {}",
                    queryType, dtos.size());
            return dtos;

        } catch (Exception e) {
            logger.error("쿼리 타입별 메트릭스 조회 중 오류 발생: queryType = {}", queryType, e);
            throw new RuntimeException("쿼리 메트릭스 조회 실패", e);
        }
    }

    /**
     * 슬로우 쿼리 조회
     */
    @Override
    public List<QueryMetricsRawDto> getSlowQueries(Double thresholdMs) {
        logger.info("슬로우 쿼리 조회 시작: thresholdMs = {}", thresholdMs);

        if (thresholdMs == null || thresholdMs < 0) {
            logger.warn("thresholdMs가 유효하지 않습니다: {}", thresholdMs);
            throw new IllegalArgumentException("thresholdMs는 0 이상이어야 합니다");
        }

        try {
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findSlowQueries(thresholdMs);
            List<QueryMetricsRawDto> dtos = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("슬로우 쿼리 조회 완료: thresholdMs = {}, 조회 건수 = {}",
                    thresholdMs, dtos.size());
            return dtos;

        } catch (Exception e) {
            logger.error("슬로우 쿼리 조회 중 오류 발생: thresholdMs = {}", thresholdMs, e);
            throw new RuntimeException("슬로우 쿼리 조회 실패", e);
        }
    }

    /**
     * CPU 사용량 상위 N개 조회
     */
    @Override
    public List<QueryMetricsRawDto> getTopByCpuUsage(Integer limit) {
        logger.info("CPU 사용량 상위 쿼리 조회 시작: limit = {}", limit);

        if (limit == null || limit <= 0) {
            logger.warn("limit이 유효하지 않습니다: {}", limit);
            limit = 10; // 기본값
        }

        try {
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findTopByCpuUsage(limit);
            List<QueryMetricsRawDto> dtos = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("CPU 사용량 상위 쿼리 조회 완료: limit = {}, 조회 건수 = {}",
                    limit, dtos.size());
            return dtos;

        } catch (Exception e) {
            logger.error("CPU 사용량 상위 쿼리 조회 중 오류 발생: limit = {}", limit, e);
            throw new RuntimeException("CPU 사용량 조회 실패", e);
        }
    }

    /**
     * 메모리 사용량 상위 N개 조회
     */
    @Override
    public List<QueryMetricsRawDto> getTopByMemoryUsage(Integer limit) {
        logger.info("메모리 사용량 상위 쿼리 조회 시작: limit = {}", limit);

        if (limit == null || limit <= 0) {
            logger.warn("limit이 유효하지 않습니다: {}", limit);
            limit = 10; // 기본값
        }

        try {
            List<QueryMetricsRaw> entities = queryMetricsRawRepository.findTopByMemoryUsage(limit);
            List<QueryMetricsRawDto> dtos = entities.stream()
                    .map(QueryMetricsRawDto::from)
                    .collect(Collectors.toList());

            logger.info("메모리 사용량 상위 쿼리 조회 완료: limit = {}, 조회 건수 = {}",
                    limit, dtos.size());
            return dtos;

        } catch (Exception e) {
            logger.error("메모리 사용량 상위 쿼리 조회 중 오류 발생: limit = {}", limit, e);
            throw new RuntimeException("메모리 사용량 조회 실패", e);
        }
    }

    /**
     * 전체 쿼리 메트릭스 개수 조회
     */
    @Override
    public int getTotalCount() {
        logger.info("전체 쿼리 메트릭스 개수 조회 시작");

        try {
            int count = queryMetricsRawRepository.count();
            logger.info("전체 쿼리 메트릭스 개수: {}", count);
            return count;

        } catch (Exception e) {
            logger.error("전체 쿼리 메트릭스 개수 조회 중 오류 발생", e);
            throw new RuntimeException("개수 조회 실패", e);
        }
    }

    /**
     * 데이터베이스별 쿼리 메트릭스 개수 조회
     */
    @Override
    public int getCountByDatabaseId(Long databaseId) {
        logger.info("데이터베이스별 쿼리 메트릭스 개수 조회 시작: databaseId = {}", databaseId);

        if (databaseId == null) {
            logger.warn("databaseId가 null입니다");
            throw new IllegalArgumentException("databaseId는 필수입니다");
        }

        try {
            int count = queryMetricsRawRepository.countByDatabaseId(databaseId);
            logger.info("데이터베이스별 쿼리 메트릭스 개수: databaseId = {}, count = {}",
                    databaseId, count);
            return count;

        } catch (Exception e) {
            logger.error("데이터베이스별 쿼리 메트릭스 개수 조회 중 오류 발생: databaseId = {}", databaseId, e);
            throw new RuntimeException("개수 조회 실패", e);
        }
    }
    /**
     * ExecutionStatus용 쿼리별 집계 통계
     */
    @Override
    public List<Map<String, Object>> getExecutionStats(Long databaseId, Integer hours) {
        logger.info("📊 쿼리별 집계 통계 조회 시작 - databaseId: {}, hours: {}", databaseId, hours);

        if (databaseId == null) {
            logger.warn("databaseId가 null입니다");
            throw new IllegalArgumentException("databaseId는 필수입니다");
        }

        if (hours == null || hours < 0) {
            logger.warn("hours가 유효하지 않습니다: {}", hours);
            hours = 1; // 기본값 1시간
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("databaseId", databaseId);
            params.put("hours", hours);  // ✅ days → hours

            List<Map<String, Object>> result = queryMetricsRawRepository.findExecutionStats(params);

            logger.info("✅ 쿼리별 집계 완료: databaseId = {}, hours = {}, 집계된 쿼리 수 = {}",
                    databaseId, hours, result.size());

            return result;

        } catch (Exception e) {
            logger.error("❌ 쿼리별 집계 통계 조회 중 오류 발생: databaseId = {}, hours = {}", databaseId, hours, e);
            throw new RuntimeException("쿼리별 집계 조회 실패", e);
        }
    }
    /**
     * 시간대별 쿼리 수 분포 조회
     */
    @Override
    public List<Map<String, Object>> getHourlyDistribution(Long databaseId, Integer hours) {
        logger.info("📊 시간대별 쿼리 수 집계 조회 시작 - databaseId: {}, hours: {}", databaseId, hours);

        if (databaseId == null) {
            logger.warn("databaseId가 null입니다");
            throw new IllegalArgumentException("databaseId는 필수입니다");
        }

        if (hours == null || hours < 0) {
            logger.warn("hours가 유효하지 않습니다: {}", hours);
            hours = 5; // 기본값 5시간
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("databaseId", databaseId);
            params.put("hours", hours);

            List<Map<String, Object>> result = queryMetricsRawRepository.findHourlyDistribution(params);

            logger.info("✅ 시간대별 분포 조회 완료: databaseId = {}, hours = {}, 시간대 수 = {}",
                    databaseId, hours, result.size());

            return result;

        } catch (Exception e) {
            logger.error("❌ 시간대별 분포 조회 중 오류 발생: databaseId = {}, hours = {}", databaseId, hours, e);
            throw new RuntimeException("시간대별 분포 조회 실패", e);
        }
    }
}
