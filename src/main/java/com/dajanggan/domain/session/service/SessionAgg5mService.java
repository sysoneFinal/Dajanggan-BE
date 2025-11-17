package com.dajanggan.domain.session.service;

import com.dajanggan.domain.session.dto.agg5m.*;
import com.dajanggan.domain.session.repository.SessionAgg5mRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SessionAgg5mService {

    private final SessionAgg5mRepository sessionAgg5mRepository;

    public SessionAgg5mService(SessionAgg5mRepository sessionAgg5mRepository){
        this.sessionAgg5mRepository = sessionAgg5mRepository;
    }

    /** 지표 : 요약카드 4개 (단일 DB - Details 페이지) */
    public SessionSummaryDto findLatestSummary(Map<String, Object> params) {
        SessionSummaryDto result = sessionAgg5mRepository.findLatestSummary(params);
        log.info("findLatestSummary 결과: {}", result);
        return result;
    }
    /** 지표 : 데드락 요약카드 1개*/
    public DeadLockSummaryDto findDeadLockCount(Map<String, Object> params) {
        DeadLockSummaryDto result = sessionAgg5mRepository.findLatestDeadLock(params);
        // 데이터가 없을 경우 기본값 반환
        if (result == null) {
            return DeadLockSummaryDto.builder()
                    .deadlockCount(0L)
                    .build();
        }
        return result;
    }

    /** 커넥션 사용량 조회 */
    public ConnectionDto findConnectionUsage(Map<String, Object> params){
        ConnectionDto result = sessionAgg5mRepository.findConnectionUsage(params);
        log.debug("findConnectionUsage 결과: {}", result);
        return result;
    }

    /** 세션 최다 사용자 추적 */
    public TopUserSessionDto findTopUserSession(Map<String, Object> params){
        TopUserSessionDto result = sessionAgg5mRepository.findTopUserSession(params);
        log.debug("findTopUserSession 결과: {}", result);
        return result;
    }

    /** 데드락 수 추이 */
    public List<DeadLockCountDto> findDeadLockTrend(Map<String, Object> params){
        List<DeadLockCountDto> result = sessionAgg5mRepository.findDeadLockTrend(params);
        log.debug("findDeadLockTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 데드락 리스트 3개*/
    public List<DeadLockListDto> findDeadLockList(Map<String, Object> params){
        List<DeadLockListDto> result = sessionAgg5mRepository.findDeadLockList(params);
        log.debug("findDeadLockList 결과 개수 :: {}",result !=null? result.size() : 0);
        return result;
    }

    /** 데드락 상세 모달 */
    public DeadLockDetailDto findDeadLockDetail(Map<String,Object> params){
        // 상세 정보
        DeadLockDetailDto result = sessionAgg5mRepository.getDeadlockDetail(params);

        if (result == null) {
            throw new IllegalArgumentException("데드락 정보를 찾을 수 없습니다.");
        }
        Map<String, Object> countParams = new HashMap<>();
        countParams.put("databaseId", params.get("databaseId"));
        countParams.put("tableName", result.getTableName());
        // 24시간 내 반복횟수가 있는지
        int recurrenceCount = sessionAgg5mRepository.getDeadlockRecurrenceCount(countParams);

        result.setRecurrenceCount(recurrenceCount);

        return result;
    }
}
