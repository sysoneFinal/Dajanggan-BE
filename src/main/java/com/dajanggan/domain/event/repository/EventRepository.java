package com.dajanggan.domain.event.repository;

import com.dajanggan.domain.event.dto.EventLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface EventRepository {

    List<String> findDistinctDatabases(Long instanceId);
    List<String> findDistinctCategories(Long instanceId);

    Map<String, Object> getRecentSummary(Long instanceId);

    /** 이벤트 로그 조회 */
    List<EventLog> getEventList(Map<String, Object> filter);
    int getEventListCount(Map<String, Object> filter);
}
