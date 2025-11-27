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
     * 특정 인스턴스의 데이터베이스 목록 조회
     *
     * 조회되는 정보:
     * 
     *   데이터베이스 ID 및 이름
     *   현재 연결 수
     *   데이터베이스 크기 (바이트)
     *   캐시 히트율
     *   최종 업데이트 시간
     * 
     *
     * @param instanceId 인스턴스 ID
     * @return 데이터베이스 정보 리스트 (DatabaseResponse DTO)
     */
    public List<DatabaseResponse> getByInstanceId(Long instanceId) {
        log.debug("데이터베이스 목록 조회: instanceId={}", instanceId);

        List<DatabaseResponse> databases = databaseRepository.findByInstanceId(instanceId);

        log.debug("조회된 데이터베이스 개수: {}", databases.size());
        return databases;
    }
}