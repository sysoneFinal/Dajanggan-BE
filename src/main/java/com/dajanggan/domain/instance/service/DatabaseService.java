package com.dajanggan.domain.instance.service;

import com.dajanggan.domain.instance.dto.DatabaseResponse;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 데이터베이스 조회 서비스
 *
 * 주요 책임:
 * 
 *   인스턴스별 데이터베이스 목록 조회
 *   데이터베이스 메트릭 정보 제공
 * 
 *
 * 역할 분리:
 * 
 *   조회 작업: DatabaseService (현재 클래스)
 *   동기화 작업: {@link DatabaseSyncService}
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-04  김민서    1. 최초작성자
 *      2025-11-06  김민서    2. 데이터베이스 정보 리스트
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseService {

    private final DatabaseRepository databaseRepository;

    /**
     * 인스턴스별 DB 목록 조회
     *
     * @param instanceId 인스턴스 ID
     * @return 데이터베이스 목록
     */
    public List<DatabaseResponse> getByInstanceId(Long instanceId) {
        return databaseRepository.findByInstanceId(instanceId);
    }

    /**
     * 활성화된 모든 데이터베이스 조회
     *
     * 용도: 알람 메트릭 수집
     *
     * @return 활성화된 데이터베이스 목록 (is_enabled = true)
     */
    public List<DatabaseResponse> findAllEnabled() {
        return databaseRepository.findAllEnabled().stream()
                .map(db -> DatabaseResponse.builder()
                        .databaseId(db.getDatabaseId())
                        .instanceId(db.getInstanceId())
                        .databaseName(db.getDatabaseName())
                        .status(db.getStatus())
                        .connections(db.getConnections())
                        .sizeBytes(db.getSizeBytes())
                        .cacheHitRate(db.getCacheHitRate())
                        .updatedAt(db.getUpdatedAt())
                        .build())
                .toList();
    }
}