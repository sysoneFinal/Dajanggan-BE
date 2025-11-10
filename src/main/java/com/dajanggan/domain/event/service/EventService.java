package com.dajanggan.domain.event.service;

import com.dajanggan.domain.event.dto.EventLog;
import com.dajanggan.domain.event.repository.EventRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final InstanceRepository instanceRepository;

    public EventService(EventRepository eventRepository, InstanceRepository instanceRepository){
        this.eventRepository = eventRepository;
        this.instanceRepository = instanceRepository;
    }

    public List<String> getDistinctDatabases(Long instanceId) {
        return eventRepository.findDistinctDatabases(instanceId);
    }
    public List<String> getDistinctCategories(Long instanceId) {
        return eventRepository.findDistinctCategories(instanceId);
    }

    /** 요약 카드용 (최근 15분) */
    public Map<String, Object> getRecentSummary(Long instanceId) {
        return eventRepository.getRecentSummary(instanceId);
    }

    /**  로그 조회 */
    public Map<String, Object> getEventList(Map<String, Object> params) {
        Long instanceId = (Long) params.get("instanceId");
        // 인스턴스 있는지 확인
        boolean exists = instanceRepository.findById(instanceId).isPresent();
        if (!exists) {
            log.info("해당 인스턴스에 대한 event log가 없습니다. instanceId: {}", instanceId);
            return Map.of("data", List.of(), "totalCount", 0);
        }

        List<EventLog> data = eventRepository.getEventList(params);
        int totalCount = eventRepository.getEventListCount(params);

        return Map.of(
                "data", data,
                "totalCount", totalCount,
                "page", params.get("page"),
                "size", params.get("pageSize")
        );
    }
}
