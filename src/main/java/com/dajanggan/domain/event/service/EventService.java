/** 작성자 : 서샘이 */
package com.dajanggan.domain.event.service;

import com.dajanggan.domain.event.dto.EventLog;
import com.dajanggan.domain.event.repository.EventRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.time.OffsetDateTime;
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

    /**
     * 이벤트 저장
     * - 별도 트랜잭션으로 처리하여 모니터링 로직과 분리
     * - 저장 실패 시에도 모니터링이 계속 진행되도록 예외
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEvents(List<EventLog> events) {
        if (events == null || events.isEmpty()) {
            log.debug("저장할 이벤트가 없습니다.");
            return;
        }

        try {
            // 검증 및 기본값 설정
            for (EventLog event : events) {
                validateRequired(event);
                setDefaults(event);
            }

            // 배치 INSERT
            eventRepository.insertEventsBatch(events);

            log.info("총 {} 개의 이벤트 저장 완료", events.size());

        } catch (Exception e) {
            log.error("이벤트 저장 실패 - 개수: {}, error: {}",
                    events.size(), e.getMessage(), e);
        }
    }

    /**
     * 필수 필드 검증
     */
    private void validateRequired(EventLog event) {
        if (event.getInstanceId() == null) {
            throw new IllegalArgumentException("instanceId는 필수입니다.");
        }
        if (event.getDatabaseId() == null) {
            throw new IllegalArgumentException("databaseId는 필수입니다.");
        }
        if (event.getCategory() == null || event.getCategory().isBlank()) {
            throw new IllegalArgumentException("category는 필수입니다.");
        }
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            throw new IllegalArgumentException("eventType은 필수입니다.");
        }
        if (event.getLevel() == null || event.getLevel().isBlank()) {
            throw new IllegalArgumentException("level은 필수입니다.");
        }
    }

    /**
     * 기본값 설정
     */
    private void setDefaults(EventLog event) {
        if (event.getDetectedAt() == null) {
            event.setDetectedAt(OffsetDateTime.now());
        }
    }
}
