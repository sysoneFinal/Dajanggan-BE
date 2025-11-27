package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.InstanceResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 인스턴스 Repository
 *
 * 주요 책임:
 * 
 *   PostgreSQL 인스턴스 정보 CRUD
 *   Slack 알림 설정 관리
 *   메트릭 수집을 위한 인스턴스 조회
 *   인스턴스 이름 기반 조회 (OS Metric Agent 연동)
 * 
 *
 * 반환 타입 원칙:
 * 
 *   조회(Read): Entity 반환 후 Service에서 DTO 변환
 *   CUD: Entity 사용
 *   특수 케이스: 민감 정보 포함 조회는 별도 메서드
 * 
 *
 * 보안:
 * 
 *   기본 조회: secretRef(암호화된 비밀번호) 제외
 *   메트릭 수집용: secretRef 포함 (별도 메서드)
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-10-23  김민서    1. 최초작성자
 *      2025-11-06  김민서    2. 인스턴스 등록
 *      2025-11-21  김민서    3. 슬랙 연동 설정
 *
 */
@Mapper
public interface InstanceRepository {

    // ========== 생성 (Create) ==========

    /**
     * 인스턴스 생성
     *
     * MyBatis useGeneratedKeys로 ID 자동 생성
     *
     * 생성 후 instance 객체의 instanceId가 자동 설정됨
     *
     * 필수 필드:
     * 
     *   instanceName - 인스턴스 이름
     *   host - 호스트 주소
     *   port - 포트 번호
     *   userName - 사용자명
     *   secretRef - 암호화된 비밀번호
     *   sslmode - SSL 모드 (기본값: disable)
     * 
     *
     * @param instance Instance 엔티티 (instanceId는 null)
     */
    void createInstance(Instance instance);

    // ========== 조회 (Read) - 기본 조회 ==========

    /**
     * 인스턴스 ID로 조회
     *
     * 보안: secretRef(암호화된 비밀번호) 제외
     *
     * 용도: 일반 조회 API
     *
     * @param id 인스턴스 ID
     * @return Instance 엔티티 (secretRef 제외)
     */
    Optional<Instance> findById(@Param("id") Long id);

    /**
     * 모든 인스턴스 조회
     *
     * 보안: secretRef(암호화된 비밀번호) 제외
     *
     * 용도: 인스턴스 목록 API
     *
     * @return Instance 엔티티 목록 (secretRef 제외)
     */
    List<Instance> findAll();

    /**
     * 인스턴스 이름으로 ID 조회
     *
     * 용도:
     * 
     *   OS Metric Agent 자동 매핑
     *   인스턴스 이름 기반 Slack 설정 업데이트
     * 
     *
     * 사용 예시:
     * <pre>
     * // OS Metric Agent가 전송한 instanceName으로 매핑
     * Optional&lt;Long&gt; instanceId = findIdByInstanceName("prod-db-01");
     * </pre>
     *
     * @param instanceName 인스턴스 이름
     * @return 인스턴스 ID (없으면 Optional.empty())
     */
    Optional<Long> findIdByInstanceName(@Param("instanceName") String instanceName);

    // ========== 조회 (Read) - 민감 정보 포함 ==========

    /**
     * 메트릭 수집을 위한 인스턴스 조회 (secretRef 포함)
     *
     * 보안 주의:
     * 
     *   secretRef(암호화된 비밀번호) 포함
     *   메트릭 수집 스케줄러 전용
     *   Controller에서 호출 금지!
     * 
     *
     * 용도:
     * 
     *   PostgreSQL 연결하여 메트릭 수집
     *   EXPLAIN ANALYZE 실행
     * 
     *
     * 처리 흐름:
     * <ol>
     *   인스턴스 목록 조회 (secretRef 포함)
     *   AesGcmService로 secretRef 복호화
     *   PostgreSQL 연결하여 메트릭 수집
     * </ol>
     *
     * @param instanceIds 인스턴스 ID 목록 (null이면 전체 조회)
     * @return Instance 엔티티 목록 (secretRef 포함)
     */
    List<Instance> findAllWithSecrets(@Param("instanceIds") List<Long> instanceIds);

    // ========== 수정 (Update) - 기본 정보 ==========

    /**
     * 인스턴스 기본 정보 업데이트
     *
     * 업데이트 가능 필드:
     * 
     *   instanceName - 인스턴스 이름
     *   host - 호스트 주소
     *   port - 포트 번호
     *   userName - 사용자명
     *   secretRef - 암호화된 비밀번호 (선택)
     *   sslmode - SSL 모드
     *   status - 상태
     *   version - PostgreSQL 버전
     * 
     *
     * 주의: secretRef는 Service에서 암호화 후 전달
     *
     * @param instance Instance 엔티티 (instanceId 필수)
     * @return 업데이트된 레코드 수 (정상: 1, 실패: 0)
     */
    int updateInstance(Instance instance);

    // ========== 수정 (Update) - Slack 설정 ==========

    /**
     * 인스턴스 이름으로 Slack 설정 업데이트
     *
     * 용도: OS Metric Agent가 인스턴스 이름으로 Slack 설정
     *
     * 업데이트 필드:
     * 
     *   slack_enabled - Slack 알림 활성화 여부
     *   slack_webhook_url - Webhook URL
     *   slack_channel - 채널명
     *   slack_mention - 멘션 대상
     * 
     *
     * @param instanceName 인스턴스 이름
     * @param enabled Slack 알림 활성화 여부
     * @param webhookUrl Webhook URL
     * @param defaultChannel 기본 채널명
     * @param mention 멘션 대상
     * @return 업데이트된 레코드 수 (정상: 1, 실패: 0)
     */
    int updateSlackSettings(
            @Param("instanceName") String instanceName,
            @Param("enabled") Boolean enabled,
            @Param("webhookUrl") String webhookUrl,
            @Param("defaultChannel") String defaultChannel,
            @Param("mention") String mention
    );

    /**
     * 인스턴스 ID로 Slack 설정 업데이트
     *
     * 용도: 관리자 UI에서 Slack 설정
     *
     * 업데이트 필드: updateSlackSettings()와 동일
     *
     * @param instanceId 인스턴스 ID
     * @param enabled Slack 알림 활성화 여부
     * @param webhookUrl Webhook URL
     * @param defaultChannel 기본 채널명
     * @param mention 멘션 대상
     * @return 업데이트된 레코드 수 (정상: 1, 실패: 0)
     */
    int updateSlackSettingsById(
            @Param("instanceId") Long instanceId,
            @Param("enabled") Boolean enabled,
            @Param("webhookUrl") String webhookUrl,
            @Param("defaultChannel") String defaultChannel,
            @Param("mention") String mention
    );

    // ========== 삭제 (Delete) - Slack 설정 ==========

    /**
     * 인스턴스 ID로 Slack 설정 삭제/초기화
     *
     * 동작:
     * 
     *   slack_enabled = false
     *   slack_webhook_url = NULL
     *   slack_channel = NULL
     *   slack_mention = NULL
     * 
     *
     * 주의: 인스턴스 자체는 삭제하지 않음
     *
     * @param instanceId 인스턴스 ID
     * @return 업데이트된 레코드 수 (정상: 1, 실패: 0)
     */
    int deleteSlackSettingsById(@Param("instanceId") Long instanceId);

    /**
     * 인스턴스 이름으로 Slack 설정 삭제/초기화
     *
     * 동작: deleteSlackSettingsById()와 동일
     *
     * @param instanceName 인스턴스 이름
     * @return 업데이트된 레코드 수 (정상: 1, 실패: 0)
     */
    int deleteSlackSettingsByName(@Param("instanceName") String instanceName);

    // ========== 삭제 (Delete) - 인스턴스 ==========

    /**
     * 인스턴스 삭제
     *
     * 동작: 물리적 삭제 (DELETE)
     *
     * 연관 데이터 처리:
     * 
     *   데이터베이스: Service에서 먼저 삭제 필요
     *   메트릭 데이터: CASCADE 설정 확인 필요
     *   대시보드: CASCADE 설정 확인 필요
     * 
     *
     * 주의: 연관 데이터 정리 후 호출
     *
     * @param id 인스턴스 ID
     * @return 삭제된 레코드 수 (정상: 1, 실패: 0)
     */
    int deleteById(@Param("id") Long id);

    // ========== 통계/집계 (Optional) ==========

    /**
     * 활성 인스턴스 개수 조회
     *
     * 용도: 대시보드 통계
     *
     * @return 활성 인스턴스 개수
     */
    int countActive();

    /**
     * 인스턴스별 데이터베이스 개수 조회
     *
     * 용도: 대시보드 통계
     *
     * 반환: Map&lt;instanceId, databaseCount&gt;
     *
     * @return 인스턴스별 데이터베이스 개수
     */
    List<InstanceDatabaseCount> countDatabasesByInstance();

    /**
     * 인스턴스-데이터베이스 개수 DTO
     */
    class InstanceDatabaseCount {
        private Long instanceId;
        private String instanceName;
        private Integer databaseCount;

        // Getters, Setters, Constructor
    }
}