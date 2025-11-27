// 작성자 : 김동현
package com.dajanggan.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ExceptionMessage {


    // 일반 오류
    INTERNAL_SERVER_ERROR("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    INVALID_REQUEST("잘못된 요청입니다."),
    RESOURCE_NOT_FOUND("요청한 리소스를 찾을 수 없습니다."),

    // DB 연결 및 설정 관련
    DB_CONNECTION_FAILED("데이터베이스 연결에 실패했습니다."),
    DB_QUERY_EXECUTION_FAILED("쿼리 실행 중 오류가 발생했습니다."),
    INVALID_DB_CONFIGURATION("유효하지 않은 데이터베이스 설정입니다."),
    INSTANCE_NOT_FOUND("인스턴스를 찾을 수 없습니다."),
    DATABASE_NOT_FOUND("데이터베이스를 찾을 수 없습니다."),

    // DB 모니터링 데이터 수집 관련
    DB_METRIC_COLLECTION_FAILED("DB 메트릭 수집 중 오류가 발생했습니다."),
    DB_MONITORING_DATA_NOT_FOUND("모니터링 데이터를 찾을 수 없습니다."),
    STATISTICS_CALCULATION_FAILED("통계 계산 중 오류가 발생했습니다."),

    // Engine Insight 관련 (디스크 I/O, IOPS, Throughput 등)
    DISK_IO_DATA_NOT_FOUND("디스크 I/O 데이터를 찾을 수 없습니다."),
    IOPS_DATA_NOT_FOUND("IOPS 데이터를 찾을 수 없습니다."),
    THROUGHPUT_DATA_NOT_FOUND("Throughput 데이터를 찾을 수 없습니다."),
    LATENCY_DATA_NOT_FOUND("Latency 데이터를 찾을 수 없습니다."),

    // Checkpoint 관련
    CHECKPOINT_DATA_NOT_FOUND("체크포인트 데이터를 찾을 수 없습니다."),
    CHECKPOINT_ANALYSIS_FAILED("체크포인트 분석 중 오류가 발생했습니다."),
    WAL_DATA_NOT_FOUND("WAL 데이터를 찾을 수 없습니다."),

    // BGWriter 관련
    BGWRITER_DATA_NOT_FOUND("BGWriter 데이터를 찾을 수 없습니다."),
    BGWRITER_STATS_COLLECTION_FAILED("BGWriter 통계 수집 중 오류가 발생했습니다."),
    BUFFER_STATS_NOT_FOUND("버퍼 통계를 찾을 수 없습니다."),

    // Vacuum 관련
    VACUUM_DATA_NOT_FOUND("Vacuum 데이터를 찾을 수 없습니다."),
    AUTOVACUUM_DATA_NOT_FOUND("AutoVacuum 데이터를 찾을 수 없습니다."),
    VACUUM_STATS_COLLECTION_FAILED("Vacuum 통계 수집 중 오류가 발생했습니다."),


    // Storage/Hotspot 관련
    STORAGE_DATA_NOT_FOUND("스토리지 데이터를 찾을 수 없습니다."),
    HOTSPOT_DATA_NOT_FOUND("Hotspot 데이터를 찾을 수 없습니다."),
    STORAGE_USAGE_EXCEEDED("스토리지 사용량이 임계값을 초과했습니다."),

    // System Resource 관련 (CPU, Memory)
    CPU_DATA_NOT_FOUND("CPU 데이터를 찾을 수 없습니다."),
    MEMORY_DATA_NOT_FOUND("메모리 데이터를 찾을 수 없습니다."),
    SHARED_BUFFERS_DATA_NOT_FOUND("Shared Buffers 데이터를 찾을 수 없습니다."),
    WORK_MEMORY_DATA_NOT_FOUND("Work Memory 데이터를 찾을 수 없습니다."),
    TEMP_FILE_DATA_NOT_FOUND("Temp File 데이터를 찾을 수 없습니다."),

    // Query/Transaction 관련
    QUERY_DATA_NOT_FOUND("쿼리 데이터를 찾을 수 없습니다."),
    TRANSACTION_DATA_NOT_FOUND("트랜잭션 데이터를 찾을 수 없습니다."),
    SLOW_QUERY_DATA_NOT_FOUND("느린 쿼리 데이터를 찾을 수 없습니다."),
    QUERY_PLAN_NOT_FOUND("쿼리 실행 계획을 찾을 수 없습니다."),

    // Session 관련
    SESSION_DATA_NOT_FOUND("세션 데이터를 찾을 수 없습니다."),
    ACTIVE_SESSION_COUNT_EXCEEDED("활성 세션 수가 임계값을 초과했습니다."),
    SESSION_TIMEOUT("세션 시간이 만료되었습니다."),

    // 알림 및 임계값 관련
    MONITORING_THRESHOLD_EXCEEDED("모니터링 임계값을 초과했습니다."),
    ALERT_CREATION_FAILED("알림 생성에 실패했습니다."),
    ALERT_SEND_FAILED("알림 전송에 실패했습니다."),

    // 시간 및 파라미터 검증 관련
    INVALID_TIME_RANGE("유효하지 않은 시간 범위입니다."),
    INVALID_METRIC_TYPE("유효하지 않은 메트릭 타입입니다."),
    INVALID_PARAMETER("유효하지 않은 파라미터입니다."),

    // 내보내기 관련
    CSV_EXPORT_FAILED("CSV 내보내기에 실패했습니다."),
    REPORT_GENERATION_FAILED("리포트 생성에 실패했습니다."),

    // 데이터 처리 관련
    DATA_PARSING_FAILED("데이터 파싱 중 오류가 발생했습니다."),
    DATA_SERIALIZATION_FAILED("데이터 직렬화 중 오류가 발생했습니다."),

    // Dashboard 관련 예외
    DASHBOARD_NOT_FOUND("대시보드를 찾을 수 없습니다."),
    INVALID_DASHBOARD_JSON("대시보드 JSON 형식이 유효하지 않습니다."),
    INVALID_WIDGET_STRUCTURE("위젯 구조가 올바르지 않습니다."),
    WIDGET_COUNT_EXCEEDED("허용된 위젯 개수를 초과했습니다."),

    // 지표 정의 예외
    METRIC_NOT_FOUND("해당 지표정보를 찾을 수 없습니다.");


    private final String message;
}
