package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.dto.DatabaseResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 데이터베이스 Repository
 *
 * 주요 책임:
 * 
 *   데이터베이스 정보 CRUD
 *   인스턴스별 데이터베이스 목록 조회
 *   메트릭 수집 대상 데이터베이스 조회
 *   N+1 문제 방지를 위한 일괄 조회
 * 
 *
 * 반환 타입 원칙:
 * 
 *   조회(Read): DTO 반환 (성능 최적화, 필요한 컬럼만)
 *   CUD: Entity 반환 (영속성 관리)
 *   내부 로직용: Entity 반환 (메트릭 수집 등)
 * 
 *
 *  ----------  ------  --------------------------------------------------
 *  작업일자      작성자    Description
 *  ----------  ------  --------------------------------------------------
 *  2025-11-04  김민서    1. 최초작성자
 *  2025-11-13  김민서    2. 데이터베이스 조회
 *
 */
@Mapper
public interface DatabaseRepository {

    // ========== 조회 (Read) - DTO 반환 ==========

    /**
     * 인스턴스 ID로 데이터베이스 목록 조회
     *
     * 활성화된 데이터베이스만 조회 (is_enabled = true)
     *
     * 반환 타입: DTO (API 응답용)
     *
     * @param instanceId 인스턴스 ID
     * @return 데이터베이스 응답 DTO 목록
     */
    List<DatabaseResponse> findByInstanceId(@Param("instanceId") Long instanceId);

    /**
     * 여러 인스턴스의 데이터베이스 목록 일괄 조회 (N+1 방지)
     *
     * 성능 최적화:
     * 
     *   여러 인스턴스의 데이터베이스를 한 번의 쿼리로 조회
     *   WHERE instance_id IN (...) 사용
     * 
     *
     * 반환 타입: DTO (API 응답용)
     *
     * 사용 예시:
     * <pre>
     * // ❌ N+1 문제 발생
     * for (Instance instance : instances) {
     *     findByInstanceId(instance.getId());  // N번 쿼리
     * }
     *
     * // ✅ 한 번의 쿼리로 해결
     * List&lt;Long&gt; ids = instances.stream()
     *     .map(Instance::getId)
     *     .toList();
     * findByInstanceIds(ids);  // 1번 쿼리
     * </pre>
     *
     * @param instanceIds 인스턴스 ID 목록
     * @return 데이터베이스 응답 DTO 목록
     */
    List<DatabaseResponse> findByInstanceIds(@Param("instanceIds") List<Long> instanceIds);

    // ========== 내부 로직용 - Entity 반환 ==========

    /**
     * 인스턴스 ID로 데이터베이스 엔티티 목록 조회
     *
     * 용도: Service 내부 로직 전용
     * 
     *   메트릭 수집
     *   데이터베이스 동기화
     *   대시보드 생성
     * 
     *
     * 반환 타입: Entity (영속성 관리)
     *
     * 주의: Controller에 직접 반환 금지!
     *
     * @param instanceId 인스턴스 ID
     * @return Database 엔티티 목록
     */
    List<Database> findDatabaseEntitiesByInstanceId(@Param("instanceId") Long instanceId);

    /**
     * 메트릭 수집이 활성화된 모든 데이터베이스 조회
     *
     * 조건: is_enabled = true
     *
     * 용도: 메트릭 수집 스케줄러 전용
     *
     * 반환 타입: Entity (메트릭 수집 로직에서 사용)
     *
     * @return 활성화된 Database 엔티티 목록
     */
    List<Database> findAllEnabled();

    /**
     * 데이터베이스 ID로 단건 조회
     *
     * 용도:
     * 
     *   EXPLAIN ANALYZE 쿼리 실행
     *   메트릭 수집
     * 
     *
     * 반환 타입: Entity
     *
     * @param databaseId 데이터베이스 ID
     * @return Database 엔티티 (없으면 Optional.empty())
     */
    Optional<Database> findById(@Param("databaseId") Long databaseId);

    // ========== 생성/수정/삭제 (CUD) - Entity 사용 ==========

    /**
     * 데이터베이스 레코드 삽입
     *
     * MyBatis useGeneratedKeys로 ID 자동 생성
     *
     * 생성 후 database 객체의 databaseId가 자동 설정됨
     *
     * @param database Database 엔티티 (databaseId는 null)
     */
    void insert(Database database);

    /**
     * 여러 데이터베이스 비활성화 (일괄)
     *
     * 동작:
     * 
     *   is_enabled = false로 업데이트
     *   실제 삭제는 하지 않음 (메트릭 데이터 보존)
     * 
     *
     * 용도: 인스턴스 수정 시 삭제된 DB 비활성화
     *
     * @param databaseIds 비활성화할 데이터베이스 ID 목록
     * @return 업데이트된 레코드 수
     */
    int deactivateByIds(@Param("databaseIds") List<Long> databaseIds);

    /**
     * 인스턴스에 속한 모든 데이터베이스 삭제
     *
     * 동작: 물리적 삭제 (DELETE)
     *
     * 용도: 인스턴스 삭제 시 연관 데이터 정리
     *
     * 주의: CASCADE 설정 확인 필요
     *
     * @param instanceId 인스턴스 ID
     * @return 삭제된 레코드 수
     */
    int deleteByInstanceId(@Param("instanceId") Long instanceId);

    /**
     * 데이터베이스 레코드 업데이트
     *
     * 업데이트 가능 필드:
     * 
     *   connections (연결 수)
     *   sizeBytes (크기)
     *   cacheHitRate (캐시 히트율)
     *   status (상태)
     *   isEnabled (활성화 여부)
     * 
     *
     * @param database Database 엔티티 (databaseId 필수)
     * @return 업데이트된 레코드 수 (정상: 1, 실패: 0)
     */
    int update(Database database);
}