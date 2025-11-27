/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.service;

import com.dajanggan.domain.session.dto.SessionActive;
import com.dajanggan.domain.session.repository.SessionActiveRepository;
import com.dajanggan.global.exception.DajangganException;
import com.dajanggan.global.exception.ExceptionMessage;
import com.dajanggan.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SessionActiveService {

    private final SessionActiveRepository sessionActiveRepository;

    public SessionActiveService(SessionActiveRepository sessionActiveRepository) {
        this.sessionActiveRepository = sessionActiveRepository;
    }

    /** 필터 옵션 조회 */
    public List<String> getDistinctDatabases(Long instanceId) {
        return sessionActiveRepository.findDistinctDatabases(instanceId);
    }

    public List<String> getDistinctStates(Long instanceId) {
        return sessionActiveRepository.findDistinctStates(instanceId);
    }

    public List<String> getDistinctWaitEventTypes(Long instanceId) {
        return sessionActiveRepository.findDistinctWaitEventTypes(instanceId);
    }

    /** 요약 카드용 (최근 수집 시점) */
    public Map<String, Object> getRecentSummary(Long instanceId) {
        return sessionActiveRepository.getRecentSummary(instanceId);
    }

    /** Active Session 리스트 조회 */
    public Map<String, Object> getSessionList(Map<String, Object> params) {
        Long instanceId = (Long) params.get("instanceId");
        
        List<SessionActive> data = sessionActiveRepository.getSessionList(params);
        int totalCount = sessionActiveRepository.getSessionListCount(params);

        return Map.of(
                "data", data,
                "totalCount", totalCount,
                "page", params.get("page"),
                "size", params.get("pageSize")
        );
    }

    /** 세션 상세 정보 조회 */
    public SessionActive getSessionDetail(Long databaseId, Integer pid) {
        Map<String, Object> params = Map.of(
                "databaseId", databaseId,
                "pid", pid
        );
        
        SessionActive session = sessionActiveRepository.getSessionDetail(params);
        
        if (session == null) {
            throw new DajangganException(ExceptionMessage.SESSION_DATA_NOT_FOUND);
        }
        
        return session;
    }
}
