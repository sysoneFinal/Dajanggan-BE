package com.dajanggan.domain.session.repository;

import com.dajanggan.domain.session.dto.SessionActive;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface SessionActiveRepository {

    /** 필터 옵션 조회 */
    List<String> findDistinctDatabases(Long instanceId);
    List<String> findDistinctStates(Long instanceId);
    List<String> findDistinctWaitEventTypes(Long instanceId);

    /** 요약 카드 */
    Map<String, Object> getRecentSummary(Long instanceId);

    /** Active Session 리스트 조회 */
    List<SessionActive> getSessionList(Map<String, Object> filter);
    int getSessionListCount(Map<String, Object> filter);

    /** 세션 상세 정보 */
    SessionActive getSessionDetail(Map<String, Object> params);
}
