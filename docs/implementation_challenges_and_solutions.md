# 구현 과정에서의 고민과 해결 사항

## 📋 목차
1. [Vacuum 도메인](#vacuum-도메인)
2. [Alarm 도메인](#alarm-도메인)
3. [Instance 도메인](#instance-도메인)
4. [Event 도메인](#event-도메인)

---

## Vacuum 도메인

### 1. 테이블 필터링 조건 개선: Vacuum 완료 후에도 데이터 유지

#### 문제 상황
- 초기 구현: `WHERE n_dead_tup > 0` 조건만 사용
- 문제: Vacuum 완료 후 `n_dead_tup`이 0이 되면 테이블이 조회에서 제외됨
- 결과: Vacuum 이력 추적 불가, Bloat 추이 데이터 손실

#### 원인 분석
- `pg_stat_all_tables`의 `n_dead_tup`은 현재 시점의 Dead Tuples 수만 반영
- Vacuum 완료 후에는 0이 되지만, `last_vacuum`/`last_autovacuum` 타임스탬프는 유지됨
- 이력 추적을 위해서는 Vacuum 실행 이력이 있는 테이블도 포함해야 함

#### 해결 과정
```sql
-- Before
WHERE st.n_dead_tup > 0

-- After
WHERE (st.n_dead_tup > 0 OR st.last_vacuum IS NOT NULL OR st.last_autovacuum IS NOT NULL)
```

#### 학습 포인트
- **이력 데이터 관리**: 현재 상태만이 아닌 이력 정보도 함께 고려해야 함
- **데이터 손실 방지**: 필터링 조건이 너무 엄격하면 유용한 데이터가 누락될 수 있음

---

### 2. Xmin Horizon 계산 정확도 개선

#### 문제 상황
- 초기 구현: `pg_stat_activity`의 모든 트랜잭션을 대상으로 계산
- 문제: 종료된 트랜잭션이나 의미 없는 트랜잭션도 포함되어 부정확함
- 결과: Xmin Horizon 값이 실제보다 크게 계산됨

#### 원인 분석
- `pg_stat_activity`에는 다양한 상태의 세션이 포함됨
- `idle`, `idle in transaction (aborted)` 등은 실제로 Vacuum을 블로킹하지 않음
- 실제로 Vacuum에 영향을 주는 것은 `active`, `idle in transaction` 상태만

#### 해결 과정
```sql
-- Before
SELECT MIN(xact_start) as oldest_xact
FROM pg_stat_activity
WHERE xact_start IS NOT NULL

-- After
SELECT MIN(xact_start) as oldest_xact
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
  AND state IN ('active', 'idle in transaction', 'idle in transaction (aborted)')
```

#### 추가 개선
- NULL 처리: 활성 트랜잭션이 없을 때 0 대신 NULL 반환
- Frontend 표시: 시간 단위(0.00h) → 분 단위(0.00분)로 변경하여 가시성 향상

#### 학습 포인트
- **도메인 지식의 중요성**: PostgreSQL의 트랜잭션 상태를 이해해야 정확한 계산 가능
- **NULL vs 0**: 데이터가 없을 때 0과 NULL의 의미가 다름 (0 = 값이 있지만 0, NULL = 데이터 없음)

---

### 3. 완료된 Vacuum 세션 감지 로직

#### 문제 상황
- `pg_stat_progress_vacuum`은 실행 중인 Vacuum만 표시
- 완료된 Vacuum은 `last_vacuum`/`last_autovacuum` 타임스탬프 변경으로만 감지 가능
- 문제: 완료 시점의 상세 정보(진행률, 처리된 블록 수 등)를 저장하지 못함

#### 해결 과정
1. **이전 수집 데이터와 비교**: `last_vacuum`/`last_autovacuum` 타임스탬프 변경 감지
2. **완료 시점 데이터 재구성**: 현재 메트릭으로 완료된 세션 DTO 생성
3. **Vacuum History 저장**: 별도 테이블에 완료 이력 저장

```java
// last_autovacuum이 변경된 경우
if (currLastAutovacuum != null &&
    (prevLastAutovacuum == null || currLastAutovacuum.isAfter(prevLastAutovacuum))) {
    VacuumRawMetricDto completed = createCompletedSessionDto(current, true, currLastAutovacuum);
    completedSessions.add(completed);
    saveVacuumHistory(database, instance, current, "autovacuum", currLastAutovacuum);
}
```

#### 학습 포인트
- **상태 변화 감지**: 타임스탬프 비교를 통한 이벤트 감지 패턴
- **데이터 재구성**: 완료 시점의 스냅샷을 저장하여 이력 관리

---

### 4. Bloat 추이 데이터 필터링: 0 값 제외

#### 문제 상황
- 집계 테이블에서 `total_bloat_bytes`가 NULL이거나 0인 레코드 포함
- 평균 계산 시 0 값이 포함되어 전체 평균이 낮아짐
- 결과: 실제 Bloat가 있는 날짜도 0.0GB로 표시됨

#### 해결 과정
```java
// total_bloat_bytes가 NULL이 아닌 데이터만 필터링
List<VacuumAgg5mDto> validMetrics = metrics.stream()
    .filter(Objects::nonNull)
    .filter(m -> m.getCollectedAt() != null)
    .filter(m -> m.getTotalBloatBytes() != null && m.getTotalBloatBytes() > 0)  // ✅ 추가
    .collect(Collectors.toList());
```

#### 학습 포인트
- **데이터 품질 관리**: 집계 시 의미 있는 데이터만 포함해야 함
- **NULL vs 0**: NULL은 데이터 없음, 0은 값이 있지만 0 (의미가 다름)

---

### 5. Index Bloat 추이 구현: 성능 최적화

#### 문제 상황
- 초기 구현: Raw 데이터에서 JSON 파싱하여 매번 계산
- 문제: 대량 데이터 처리 시 성능 저하, 매번 JSON 파싱 오버헤드
- 결과: 쿼리 실행 시간이 길어지고 서버 부하 증가

#### 해결 과정
1. **집계 테이블에 컬럼 추가**: `total_index_bloat_bytes` 컬럼 추가
2. **배치 집계 로직 구현**: 1분 집계 → 5분 집계로 자동 계산
3. **쿼리 단순화**: JSON 파싱 대신 단순 컬럼 조회

```sql
-- Migration
ALTER TABLE vacuum_metrics_agg_1m ADD COLUMN total_index_bloat_bytes BIGINT DEFAULT 0;
ALTER TABLE vacuum_metrics_agg_5m ADD COLUMN total_index_bloat_bytes BIGINT DEFAULT 0;

-- Aggregator에서 계산
SELECT 
    SUM((index_bloat_info::jsonb->>'bytes')::bigint) as total_index_bloat_bytes
FROM vacuum_raw_metrics
WHERE ...
```

#### 학습 포인트
- **성능 vs 유연성**: JSON 저장은 유연하지만 성능이 떨어짐
- **사전 집계**: 자주 조회되는 데이터는 미리 집계하여 저장
- **스키마 설계**: 쿼리 패턴을 고려한 컬럼 추가

---

## Alarm 도메인

### 6. 알람 발생 조건 설계: 윈도우 기반 + 지속 시간

#### 문제 상황
- 단순 임계치 초과만으로는 노이즈 알람이 많음
- 예: 일시적인 스파이크가 발생해도 즉시 알람 발생
- 결과: 알람 피로도 증가, 중요한 알람 놓침

#### 해결 과정
**3가지 조건을 모두 만족해야 알람 발생:**
1. **윈도우 내 발생 횟수**: 최근 N분 내에 M번 이상 발생
2. **최소 지속 시간**: 최소 N분 이상 지속
3. **레벨 변경 감지**: 레벨이 변경되면 즉시 알람

```java
private boolean shouldFireAlarm(AlarmRule rule, AlarmTracking tracking, String level, String previousLevel) {
    int requiredCount = getOccurCount(rule.getLevels(), level);
    int requiredDuration = getMinDuration(rule.getLevels(), level);
    int windowMin = getWindowMin(rule.getLevels(), level);
    
    // 1. 윈도우 내 발생 횟수 체크
    if (tracking.getConsecutiveCount() < requiredCount) return false;
    
    // 2. 최소 지속 시간 체크
    long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
    if (durationMinutes < requiredDuration) return false;
    
    // 3. 같은 레벨에서 이미 FIRED면 재발생 방지
    if ("FIRED".equals(tracking.getStatus()) && level.equals(previousLevel)) return false;
    
    return true;
}
```

#### 학습 포인트
- **알람 설계 원칙**: False Positive 최소화, 중요한 알람 놓치지 않기
- **상태 관리**: Tracking을 통한 연속 발생 추적
- **윈도우 기반 로직**: 시간 기반 슬라이딩 윈도우 패턴

---

### 7. 집계 타입별 쿼리 분기 처리

#### 문제 상황
- 다양한 집계 타입 지원 필요: `latest_avg`, `avg_5m`, `avg_15m`, `p95_15m`
- 각 타입마다 다른 테이블/쿼리 사용
- 문제: 단일 쿼리로 처리하기 어려움

#### 해결 과정
**전략 패턴 적용:**
1. `MetricConfig`에 집계 타입별 쿼리 정의
2. `collectMetricValue`에서 집계 타입에 따라 분기
3. PreparedStatement vs Statement 선택 (파라미터 필요 여부)

```java
private BigDecimal collectMetricValue(Connection conn, String metricType, 
                                      Long instanceId, Long databaseId, String aggregationType) {
    // latest_avg: 실시간 값
    if (aggregationType == null || "latest_avg".equals(aggregationType)) {
        return collectLatestValue(conn, metricType, instanceId, databaseId);
    }
    
    // 집계 타입에 따라 집계 테이블에서 조회
    String sql = metricConfig.getAggregatedMetricQuery(metricType, aggregationType);
    // PreparedStatement 또는 Statement 선택
    boolean needsParams = sql.contains("?");
    // ...
}
```

#### 학습 포인트
- **전략 패턴**: 알고리즘을 객체로 캡슐화하여 런타임에 선택
- **설정 기반 개발**: 쿼리를 설정으로 분리하여 유지보수성 향상
- **동적 SQL 처리**: 파라미터 유무에 따른 Statement 선택

---

### 8. 데이터 일관성 문제: FIRED 상태에서 Feed 없음

#### 문제 상황
- 트래킹이 `FIRED` 상태인데 `AlarmFeed`가 없는 경우 발생
- 원인: 수동 알람 발생 시 트래킹을 먼저 `FIRED`로 설정하고 Feed를 나중에 생성
- 결과: 메트릭 히스토리 저장 실패 (`alarmFeedId`가 없음)

#### 해결 과정
```java
if ("FIRED".equals(tracking.getStatus())) {
    Long feedId = selectLatestFeedIdForTracking(tracking.getAlarmTrackingId());
    if (feedId != null) {
        saveMetricHistory(feedId, currentValue);
    } else {
        // Feed가 없으면 생성
        fireAlarm(conn, rule, tracking, currentValue, triggeredLevel, metricType, false);
        feedId = selectLatestFeedIdForTracking(tracking.getAlarmTrackingId());
        if (feedId != null) {
            saveMetricHistory(feedId, currentValue);
        }
    }
}
```

#### 학습 포인트
- **트랜잭션 일관성**: 관련 데이터 간의 일관성 보장
- **예외 상황 처리**: 정상 흐름이 아닌 경우도 고려
- **데이터 복구**: 누락된 데이터를 자동으로 생성

---

### 9. JSON 파싱 및 매핑 문제

#### 문제 상황
- DB에 저장된 JSON 키: `notice`, `warning`, `critical`
- Java DTO 필드명: `info`, `warn`, `critical`
- 문제: Jackson이 자동 매핑 실패, `threshold` undefined 에러

#### 해결 과정
**@JsonProperty 어노테이션 사용:**
```java
@Data
@NoArgsConstructor
public static class Levels {
    @JsonProperty("notice")
    private ThresholdLevel notice;
    
    @JsonProperty("warning")
    private ThresholdLevel warning;
    
    @JsonProperty("critical")
    private ThresholdLevel critical;
    
    // 명시적 생성자 (Jackson이 사용)
    public Levels(ThresholdLevel notice, ThresholdLevel warning, ThresholdLevel critical) {
        this.notice = notice;
        this.warning = warning;
        this.critical = critical;
    }
}
```

#### 학습 포인트
- **JSON 직렬화/역직렬화**: 필드명과 JSON 키가 다를 때 `@JsonProperty` 사용
- **생성자 주의**: `@AllArgsConstructor`와 `@JsonProperty` 충돌 가능
- **명시적 매핑**: 암묵적 매핑보다 명시적 매핑이 안전함

---

### 10. CASCADE DELETE vs 수동 삭제

#### 문제 상황
- 알람 규칙 삭제 시 관련 데이터(트래킹, 피드, 히스토리)도 함께 삭제 필요
- 초기 구현: 애플리케이션 레벨에서 순서대로 수동 삭제
- 문제: 코드 복잡도 증가, 트랜잭션 관리 어려움

#### 고민 사항
**옵션 1: 애플리케이션 레벨 수동 삭제**
- 장점: 세밀한 제어 가능, 로깅 용이
- 단점: 코드 복잡, 순서 중요, 실수 가능성

**옵션 2: DB 레벨 CASCADE DELETE**
- 장점: 코드 간소화, 데이터 일관성 보장, 성능 우수
- 단점: 삭제 순서 제어 어려움, 복구 어려움

#### 해결 과정
**CASCADE DELETE 선택:**
```sql
ALTER TABLE alarm_tracking
ADD CONSTRAINT fk_alarm_tracking_rule_id
FOREIGN KEY (alarm_rule_id)
REFERENCES alarm_rule(alarm_rule_id)
ON DELETE CASCADE;

ALTER TABLE alarm_feed
ADD CONSTRAINT fk_alarm_feed_rule_id
FOREIGN KEY (alarm_rule_id)
REFERENCES alarm_rule(alarm_rule_id)
ON DELETE CASCADE;
```

**코드 간소화:**
```java
// Before: 5단계 수동 삭제
deleteMetricHistoryByRuleId();
deleteRelatedObjectsByRuleId();
deleteFeedsByRuleId();
deleteByRuleId();
deleteRule();

// After: 규칙만 삭제하면 자동으로 관련 데이터 삭제
deleteRule();
```

#### 학습 포인트
- **DB 제약조건 활용**: 애플리케이션 로직보다 DB 제약조건이 더 안전
- **코드 복잡도 vs 유연성**: 트레이드오프 고려
- **데이터 일관성**: DB 레벨에서 보장하는 것이 더 확실함

---

### 11. 관련 객체 동적 생성 (On-Demand)

#### 문제 상황
- 기존 알람에는 관련 객체가 저장되지 않음
- 상세 조회 시 관련 객체가 없어 빈 화면 표시
- 문제: 레거시 데이터 처리, 실시간 조회 시 성능 고려

#### 해결 과정
**On-Demand 생성 패턴:**
1. 상세 조회 시 관련 객체 확인
2. 없으면 동적으로 쿼리 실행하여 생성
3. 생성 후 DB에 저장하여 다음 조회 시 재사용

```java
private List<AlarmFeedDto.RelatedObjectRaw> generateRelatedObjectsOnDemand(AlarmFeed feed) {
    // 관련 객체가 없으면 동적으로 생성
    if (relatedObjects.isEmpty()) {
        Connection conn = createConnection(instance, database.getDatabaseName());
        String sql = metricConfig.getRelatedObjectsQuery(feed.getMetricType());
        // 쿼리 실행 및 저장
        saveRelatedObjectsToDb(feed.getAlarmFeedId(), feed.getAlarmRuleId(), rs);
    }
    return relatedObjects;
}
```

#### 학습 포인트
- **Lazy Loading 패턴**: 필요할 때만 데이터 생성
- **레거시 데이터 처리**: 기존 데이터에 대한 하위 호환성 고려
- **성능 vs 완전성**: 모든 데이터를 미리 저장 vs 필요 시 생성

---

### 12. 관련 객체 메트릭 포맷팅

#### 문제 상황
- 관련 객체의 `metric_value`가 숫자로만 표시됨
- 사용자가 의미를 파악하기 어려움
- 예: "780000" → "Dead 780K"로 표시 필요

#### 해결 과정
**지표 타입별 포맷팅:**
```java
private String formatRelatedMetric(String metricType, String metricValue, String objectType) {
    return switch (metricType) {
        case "dead_tuples" -> "Dead " + formatNumber(value);
        case "bloat_size" -> formatBytes(value);  // "1.2GB"
        case "long_running_queries" -> "Runtime: " + formatDuration(value);  // "1.2h"
        case "unused_indexes" -> "Scans: " + formatNumber(value);
        default -> formatNumber(value);
    };
}
```

#### 학습 포인트
- **사용자 경험**: 원시 데이터보다 의미 있는 포맷이 중요
- **도메인별 포맷**: 각 지표 타입에 맞는 단위 변환
- **일관성**: 전체 시스템에서 동일한 포맷 사용

---

## Instance 도메인

### 13. 인스턴스 생성 시 자동 Database 목록 조회

#### 문제 상황
- 인스턴스 등록 후 수동으로 Database를 등록해야 함
- 사용자 경험 저하, 실수 가능성

#### 해결 과정
**자동화된 프로세스:**
1. 인스턴스 저장
2. PostgreSQL 연결하여 `pg_database` 조회
3. Database 레코드 자동 생성
4. 디폴트 대시보드 자동 생성

```java
List<String> dbNames = fetchDatabaseNames(entity, password);
for (String dbName : dbNames) {
    Database database = new Database();
    database.setInstanceId(entity.getInstanceId());
    database.setDatabaseName(dbName);
    database.setIsEnabled(true);
    databaseRepository.insert(database);
}
overviewService.createDefaultDashboard(entity.getInstanceId(), createdDatabases);
```

#### 학습 포인트
- **자동화**: 반복 작업을 자동화하여 사용자 경험 향상
- **에러 처리**: 연결 실패 시 적절한 에러 메시지
- **트랜잭션 관리**: 여러 단계 작업의 원자성 보장

---

### 14. Slack 연동: 인스턴스별 설정 관리

#### 문제 상황
- 전역 Slack 설정만 지원
- 인스턴스별로 다른 Slack 채널/웹훅 필요
- 문제: 유연성 부족, 설정 관리 어려움

#### 해결 과정
**인스턴스별 설정 저장:**
1. `Instance` 엔티티에 Slack 필드 추가
2. CRUD API 구현
3. 알람 발생 시 인스턴스별 설정 조회

```java
// 인스턴스별 설정 조회
Instance instance = instanceRepository.findById(instanceId).get();
String webhookUrl = instance.getSlackWebhookUrl();
String channel = instance.getSlackChannel();
String mention = instance.getSlackMention();
```

#### 에러 처리 개선
- 404: Webhook URL이 유효하지 않음 → 상세 가이드 제공
- 403: 권한 없음/만료 → 재생성 안내
- 400: 요청 형식 오류 → 페이로드 확인 안내

#### 학습 포인트
- **다중 테넌시**: 인스턴스별 독립적인 설정 관리
- **에러 메시지**: 사용자가 해결할 수 있는 구체적인 가이드 제공
- **하위 호환성**: 기본값 제공으로 기존 코드와 호환

---

## Event 도메인

### 15. Duration 필드 Overflow 문제

#### 문제 상황
- `duration` 컬럼: `NUMERIC(8,2)` (최대 999,999.99)
- 바이트 값(예: 2GB = 2,147,483,648)이 `duration`에 들어감
- 결과: `numeric field overflow` 에러

#### 원인 분석
- `duration`은 시간(초) 단위를 위한 필드
- 바이트, 카운트, 퍼센트 값이 잘못 들어감
- 스키마 설계 시 의미를 명확히 하지 않음

#### 해결 과정
**의미에 맞는 값만 저장:**
- 시간 관련: `Transaction_Age`, `Block_Duration` → duration에 저장
- 바이트/카운트/퍼센트: `Total_Table_Bloat`, `Dead_Tuples` 등 → duration = NULL

```java
// 시간 관련 이벤트
events.add(buildEvent(..., transactionAge.doubleValue(), ...));

// 바이트/카운트/퍼센트 이벤트
events.add(buildEvent(..., null, ...));  // duration = NULL
```

#### 학습 포인트
- **스키마 의미 명확화**: 컬럼의 의미를 명확히 정의
- **타입 안전성**: 적절한 타입 선택 (바이트는 BIGINT, 시간은 NUMERIC)
- **데이터 검증**: 애플리케이션 레벨에서도 검증 필요

---

### 16. 이벤트 감지 로직 표준화

#### 문제 상황
- `VacuumEventDetector`와 `SessionEventDetector`의 패턴 불일치
- 코드 중복, 유지보수 어려움

#### 해결 과정
**표준 패턴 적용:**
1. Enum 사용: `EventCategory`, `EventType`, `ResourceType`, `EventLevel`
2. 로깅 추가: 이벤트 감지 시 로그 출력
3. 빌더 패턴: `buildEvent` 메서드 표준화

```java
// Before: 문자열 직접 사용
.category("VACUUM")
.level("WARNING")

// After: Enum 사용
.category(EventCategory.VACUUM.name())
.level(EventLevel.WARN.name())
```

#### 학습 포인트
- **코드 일관성**: 동일한 패턴 적용으로 유지보수성 향상
- **타입 안전성**: Enum 사용으로 오타 방지
- **로깅 전략**: 디버깅을 위한 적절한 로그 레벨

---

## 종합 학습 포인트

### 1. 데이터 정확성
- 단위 변환 시 반올림 문제 고려
- NULL vs 0의 의미 구분
- 필터링 조건의 엄격함 조절

### 2. 성능 최적화
- 사전 집계 vs 실시간 계산
- JSON 파싱 오버헤드 고려
- 인덱스 및 쿼리 최적화

### 3. 데이터 일관성
- 트랜잭션 관리
- 외래키 제약조건 활용
- 예외 상황 처리

### 4. 사용자 경험
- 의미 있는 데이터 포맷팅
- 자동화된 프로세스
- 명확한 에러 메시지

### 5. 코드 품질
- 일관된 패턴 적용
- 설정 기반 개발
- 적절한 추상화

---

---

## SQL 설계: 정규화 vs 반정규화

### 17. 집계 테이블 설계: 성능을 위한 반정규화

#### 문제 상황
- 원시 데이터(`vacuum_raw_metrics`)는 테이블별로 저장되어 대량
- 대시보드 조회 시 매번 집계 계산 필요 → 성능 저하
- 예: 30일 추이 조회 시 수만 건의 원시 데이터를 집계

#### 정규화 vs 반정규화 고민

**옵션 1: 완전 정규화 (원시 데이터만 저장)**
```sql
-- 원시 데이터만 저장
vacuum_raw_metrics (table_name별로 저장)
-- 조회 시 매번 집계
SELECT AVG(bloat_bytes), SUM(bloat_bytes) 
FROM vacuum_raw_metrics 
WHERE collected_at BETWEEN ... 
GROUP BY DATE_TRUNC('day', collected_at)
```
- 장점: 데이터 중복 없음, 저장 공간 절약
- 단점: 조회 시마다 집계 계산, 느린 응답 시간

**옵션 2: 반정규화 (집계 테이블 추가)**
```sql
-- 집계 테이블 추가
vacuum_metrics_agg_1m (1분 단위 집계)
vacuum_metrics_agg_5m (5분 단위 집계)
```
- 장점: 조회 성능 향상, 실시간 집계 불필요
- 단점: 데이터 중복, 저장 공간 증가, 배치 작업 필요

#### 해결 과정
**다층 집계 테이블 구조:**
1. **Raw 데이터**: `vacuum_raw_metrics` (테이블별 상세 정보)
2. **1분 집계**: `vacuum_metrics_agg_1m` (분 단위 통계)
3. **5분 집계**: `vacuum_metrics_agg_5m` (5분 단위 통계, 1분 집계에서 재집계)

**집계 컬럼 설계:**
```sql
-- 반정규화: 집계된 값들을 직접 저장
total_bloat_bytes,        -- SUM(bloat_bytes)
avg_bloat_bytes,          -- AVG(bloat_bytes)
max_bloat_bytes,          -- MAX(bloat_bytes)
total_index_bloat_bytes,  -- SUM(index_bloat_bytes)
critical_bloat_tables,    -- COUNT(*) WHERE bloat_ratio > 0.2
```

**Top N 테이블 정보 반정규화:**
```sql
-- Top 5 테이블 정보를 직접 컬럼으로 저장 (반정규화)
top_table_1, top_table_1_dead_tuples,
top_table_2, top_table_2_dead_tuples,
...
top_bloat_table_1, top_bloat_table_1_bytes,
...
```
- 정규화: 별도 `top_tables` 테이블 생성
- 반정규화: 집계 테이블에 직접 저장
- 선택: 반정규화 (조회 성능 우선)

#### 학습 포인트
- **성능 vs 저장 공간**: 조회 빈도가 높으면 반정규화 고려
- **다층 집계**: Raw → 1분 → 5분으로 단계적 집계
- **배치 처리**: 집계는 배치로 처리하여 실시간 부하 감소
- **트레이드오프**: 정규화는 저장 공간, 반정규화는 조회 성능

---

### 18. JSONB 사용: 유연성과 성능의 균형

#### 문제 상황
- 알람 규칙의 임계치 설정이 복잡함 (notice/warning/critical 각각 threshold, occurCount, minDuration, windowMin)
- 인덱스별 Bloat 정보가 가변적 (테이블마다 인덱스 개수 다름)
- 정규화하면 테이블이 많아지고 JOIN이 복잡해짐

#### 정규화 vs JSONB 고민

**옵션 1: 완전 정규화**
```sql
-- 별도 테이블로 분리
alarm_rule_levels (
    alarm_rule_id, level_type, threshold, 
    occur_count, min_duration, window_min
)
-- 3개 레벨 × 여러 규칙 = 많은 행

index_bloat_details (
    vacuum_raw_metrics_id, index_name, bytes, ratio
)
-- 인덱스 개수만큼 행 생성
```
- 장점: 정규화된 구조, 쿼리 유연성
- 단점: JOIN 복잡, 성능 저하, 쿼리 복잡도 증가

**옵션 2: JSONB 사용 (반정규화)**
```sql
-- JSONB로 저장
alarm_rule.levels JSONB
-- {"notice": {"threshold": 100000, "occurCount": 1, ...}, ...}

vacuum_raw_metrics.index_bloat_info JSONB
-- [{"name": "idx1", "bytes": 1024, "ratio": 0.1}, ...]
```
- 장점: 단일 컬럼, 조회 간단, 유연한 구조
- 단점: JSON 파싱 오버헤드, 인덱싱 제한

#### 해결 과정

**1. Alarm Rule Levels (JSONB)**
```sql
-- 저장
INSERT INTO alarm_rule (levels, ...)
VALUES (
    '{"notice": {"threshold": 100000, "occurCount": 1, "minDuration": 2, "windowMin": 15},
      "warning": {"threshold": 500000, "occurCount": 5, "minDuration": 2, "windowMin": 15},
      "critical": {"threshold": 1000000, "occurCount": 10, "minDuration": 1, "windowMin": 10}}'::jsonb,
    ...
);

-- 조회 및 파싱
SELECT levels::text FROM alarm_rule WHERE alarm_rule_id = ?;
-- Java에서 ObjectMapper로 파싱
```

**2. Index Bloat Info (JSONB → 컬럼 전환)**
- 초기: JSONB로 저장, 조회 시마다 파싱
- 개선: `total_index_bloat_bytes` 컬럼 추가 (집계 시 계산하여 저장)
- 이유: 자주 조회되는 집계 값은 컬럼으로 저장

```sql
-- Before: JSON 파싱
SELECT SUM((elem->>'bytes')::BIGINT)
FROM jsonb_array_elements(index_bloat_info::jsonb) AS elem

-- After: 컬럼 직접 사용
SELECT total_index_bloat_bytes
FROM vacuum_metrics_agg_1m
```

#### 학습 포인트
- **JSONB 사용 시기**: 구조가 가변적이고 자주 변경될 때
- **컬럼 전환 시기**: 자주 조회되는 집계 값은 컬럼으로 저장
- **성능 고려**: JSON 파싱 오버헤드 vs JOIN 복잡도
- **유연성 vs 성능**: 트레이드오프 고려

---

### 19. CTE 활용: 복잡한 쿼리의 가독성과 재사용성

#### 문제 상황
- 집계 쿼리가 매우 복잡함 (여러 단계의 집계, 필터링, 윈도우 함수)
- 가독성 저하, 유지보수 어려움
- 중복 로직 발생

#### 해결 과정

**CTE (Common Table Expression) 활용:**
```sql
WITH current_stats AS (
    -- 1분 단위 집계
    SELECT 
        database_id, instance_id,
        DATE_TRUNC('minute', collected_at) as collected_at,
        AVG(n_dead_tup) as avg_dead_tuples,
        SUM(bloat_bytes) as total_bloat_bytes,
        ...
    FROM vacuum_raw_metrics
    WHERE ...
    GROUP BY ...
),
previous_stats AS (
    -- 이전 시점 데이터 (변화율 계산용)
    SELECT 
        database_id, instance_id,
        total_dead_tuples as prev_total_dead_tuples
    FROM vacuum_metrics_agg_1m
    WHERE collected_at = DATE_TRUNC('minute', NOW() - INTERVAL '1 minutes')
)
-- 최종 SELECT
SELECT 
    cs.*,
    cs.total_dead_tuples - COALESCE(ps.prev_total_dead_tuples, 0) as net_change
FROM current_stats cs
LEFT JOIN previous_stats ps ON ...
```

**장점:**
- 가독성 향상: 단계별로 명확히 분리
- 재사용성: CTE를 여러 번 참조 가능
- 디버깅 용이: 각 CTE를 독립적으로 테스트 가능

#### 학습 포인트
- **모듈화**: 복잡한 쿼리를 논리적 단위로 분리
- **가독성**: 유지보수성을 위한 코드 구조화
- **성능**: CTE는 임시 테이블처럼 동작하지만 최적화됨

---

### 20. JOIN 최소화: 조회 성능 최적화

#### 문제 상황
- 알람 규칙 조회 시 `instance_name`, `database_name` 필요
- 매번 JOIN 수행 → 성능 저하
- 정규화된 구조에서는 필수 JOIN

#### 고민 사항

**옵션 1: 매번 JOIN (정규화 유지)**
```sql
SELECT 
    ar.*,
    i.instance_name,
    d.database_name
FROM alarm_rule ar
LEFT JOIN monitor_instance i ON ar.instance_id = i.instance_id
LEFT JOIN monitor_database d ON ar.database_id = d.database_id
```
- 장점: 데이터 일관성, 정규화 유지
- 단점: 매번 JOIN 오버헤드

**옵션 2: 반정규화 (이름 직접 저장)**
```sql
-- alarm_rule 테이블에 직접 저장
instance_name VARCHAR,
database_name VARCHAR
```
- 장점: JOIN 불필요, 빠른 조회
- 단점: 데이터 중복, 동기화 문제 (이름 변경 시)

#### 해결 과정
**LEFT JOIN 유지 (정규화 유지):**
- 이름은 자주 변경되지 않음
- 데이터 일관성이 더 중요
- 인덱스 활용으로 JOIN 성능 충분

```sql
-- 인덱스 활용
CREATE INDEX idx_alarm_rule_instance ON alarm_rule(instance_id);
CREATE INDEX idx_alarm_rule_database ON alarm_rule(database_id);
```

#### 학습 포인트
- **정규화 우선**: 데이터 일관성이 성능보다 중요할 때
- **인덱스 활용**: JOIN 성능 향상
- **트레이드오프**: 중복 저장 vs JOIN 오버헤드

---

### 21. 이전 통계 비교: 변화율 계산을 위한 설계

#### 문제 상황
- Dead Tuples 증가율/감소율 계산 필요
- 현재 값과 이전 값을 비교해야 함
- 매번 이전 데이터 조회 → 성능 저하

#### 해결 과정

**CTE를 활용한 이전 데이터 조회:**
```sql
WITH current_stats AS (
    -- 현재 시점 집계
    SELECT 
        database_id, instance_id,
        SUM(n_dead_tup) as total_dead_tuples,
        ...
    FROM vacuum_raw_metrics
    WHERE collected_at >= DATE_TRUNC('minute', NOW()) - INTERVAL '1 minute'
    GROUP BY ...
),
previous_stats AS (
    -- 이전 시점 집계 (집계 테이블에서 조회)
    SELECT 
        database_id, instance_id,
        total_dead_tuples as prev_total_dead_tuples
    FROM vacuum_metrics_agg_1m
    WHERE collected_at = DATE_TRUNC('minute', NOW() - INTERVAL '1 minutes')
)
SELECT 
    cs.*,
    -- 변화율 계산
    CASE 
        WHEN ps.prev_total_dead_tuples IS NOT NULL 
             AND cs.total_dead_tuples > ps.prev_total_dead_tuples
        THEN cs.total_dead_tuples - ps.prev_total_dead_tuples
        ELSE 0 
    END as dead_tuple_increase_rate,
    ...
FROM current_stats cs
LEFT JOIN previous_stats ps ON ...
```

**설계 결정:**
- 정규화: 이전 데이터를 별도 조회 (집계 테이블 활용)
- 반정규화 고려: `prev_total_dead_tuples` 컬럼 추가
- 선택: 정규화 유지 (CTE로 효율적 조회)

#### 학습 포인트
- **시계열 데이터**: 이전 값과의 비교는 일반적인 패턴
- **집계 테이블 활용**: 이전 집계 결과를 재사용
- **CTE 활용**: 복잡한 비교 로직을 명확하게 표현

---

### 22. Top N 데이터 저장: 반정규화 결정

#### 문제 상황
- 대시보드에서 Top 5 테이블 정보 필요
- 매번 `ROW_NUMBER()` 윈도우 함수로 계산 → 성능 저하
- 집계 시점에 이미 계산된 값을 재계산

#### 해결 과정

**반정규화: Top N을 직접 컬럼으로 저장**
```sql
-- 집계 테이블에 Top 5 정보 직접 저장
top_table_1, top_table_1_dead_tuples,
top_table_2, top_table_2_dead_tuples,
top_table_3, top_table_3_dead_tuples,
top_table_4, top_table_4_dead_tuples,
top_table_5, top_table_5_dead_tuples,

top_bloat_table_1, top_bloat_table_1_bytes,
top_bloat_table_2, top_bloat_table_2_bytes,
...
```

**집계 시점에 계산:**
```sql
WITH top_tables AS (
    SELECT 
        database_id, instance_id, collected_at,
        table_name, n_dead_tup,
        ROW_NUMBER() OVER (
            PARTITION BY database_id, instance_id, collected_at
            ORDER BY n_dead_tup DESC
        ) as rank
    FROM vacuum_raw_metrics
    WHERE ...
)
SELECT 
    ...,
    MAX(CASE WHEN t.rank = 1 THEN t.table_name END) as top_table_1,
    MAX(CASE WHEN t.rank = 1 THEN t.n_dead_tup END) as top_table_1_dead_tuples,
    ...
FROM agg_5m a
LEFT JOIN top_tables t ON ...
```

**정규화 대안:**
```sql
-- 별도 테이블
top_tables_agg (
    agg_id, rank, table_name, dead_tuples
)
-- JOIN 필요
```
- 선택: 반정규화 (조회 성능 우선, Top 5는 고정)

#### 학습 포인트
- **고정된 Top N**: 반정규화 고려 (N이 작고 고정적일 때)
- **가변적인 Top N**: 정규화 고려 (N이 크거나 가변적일 때)
- **집계 시점 계산**: 조회 시 계산 대신 집계 시점에 미리 계산

---

## 정규화/반정규화 설계 원칙

### 1. 정규화를 우선 고려
- 데이터 일관성 보장
- 저장 공간 절약
- 업데이트 용이

### 2. 반정규화를 고려하는 경우
- **조회 빈도가 매우 높을 때**: 집계 테이블
- **JOIN이 복잡하고 성능 저하**: 반정규화
- **구조가 가변적일 때**: JSONB
- **고정된 Top N**: 반정규화

### 3. 하이브리드 접근
- **Raw 데이터**: 정규화 유지
- **집계 데이터**: 반정규화 (성능 우선)
- **메타데이터**: JSONB (유연성 우선)
- **자주 조회되는 집계**: 컬럼으로 저장

### 4. 성능 모니터링
- 반정규화 후 조회 성능 측정
- 저장 공간 증가 모니터링
- 데이터 동기화 정확성 검증

---

## 암복호화 설계: 보안과 호환성

### 23. 암호화 알고리즘 선택: AES-GCM vs AES-CBC

#### 문제 상황
- 데이터베이스 비밀번호를 평문으로 저장하면 보안 위험
- 암호화 알고리즘 선택 필요
- 인증(Authentication)과 기밀성(Confidentiality) 모두 필요

#### 알고리즘 비교

**옵션 1: AES-CBC (Cipher Block Chaining)**
- 장점: 널리 사용, 호환성 좋음
- 단점: 인증 없음 (변조 감지 불가), IV 재사용 위험

**옵션 2: AES-GCM (Galois/Counter Mode)**
- 장점: 인증과 기밀성 모두 제공 (AEAD), 성능 우수
- 단점: IV 재사용 시 심각한 보안 문제

#### 해결 과정

**AES-GCM 선택:**
```java
private static final String CIPHER = "AES/GCM/NoPadding";
private static final int TAG_BITS = 128;  // 인증 태그 128비트
```

**이유:**
1. **AEAD (Authenticated Encryption with Associated Data)**: 변조 감지 가능
2. **성능**: 하드웨어 가속 지원
3. **보안**: IV가 재사용되어도 완전히 안전하지는 않지만, CBC보다 안전

**IV (Initialization Vector) 관리:**
```java
byte[] iv = new byte[ivLen];  // 12 bytes
rng.nextBytes(iv);  // SecureRandom으로 매번 새로 생성
```
- 매 암호화마다 새로운 IV 생성 (재사용 방지)
- IV는 암호문과 함께 저장 (복호화 시 필요)

#### 학습 포인트
- **AEAD 선택**: 인증과 기밀성 모두 필요한 경우
- **IV 관리**: 매번 새로운 IV 생성, 재사용 금지
- **보안 vs 성능**: GCM은 성능과 보안 모두 우수

---

### 24. 키 관리: 마스터 키와 키 로테이션

#### 문제 상황
- 암호화 키를 코드에 하드코딩하면 위험
- 키 유출 시 모든 데이터 노출
- 키 교체 시 기존 데이터 복호화 불가

#### 키 관리 전략

**옵션 1: 단일 키 (하드코딩)**
```java
// ❌ 위험
private static final String KEY = "my-secret-key";
```
- 장점: 구현 간단
- 단점: 키 유출 시 모든 데이터 노출, 키 교체 불가

**옵션 2: 환경 변수/설정 파일**
```yaml
# application.yml
app:
  crypto:
    keys-v1: "1:base64key1,2:base64key2"
    active-key-id: 1
```
- 장점: 코드와 분리, 키 로테이션 가능
- 단점: 설정 파일 보안 필요

**옵션 3: 키 관리 서비스 (KMS)**
- 장점: 전문적인 키 관리, 감사 로그
- 단점: 추가 인프라 필요

#### 해결 과정

**다중 키 지원 (키 로테이션):**
```java
private final Map<Short, byte[]> masterKeys; // keyId -> 32byte key
private final short activeKeyId;  // 현재 활성 키 ID
```

**암호화 시 키 ID 저장:**
```java
// 암호문 구조: version(1) + keyId(2) + salt + iv + ciphertext
ByteBuffer bb = ByteBuffer.allocate(1 + 2 + salt.length + iv.length + ct.length);
bb.put(VERSION).putShort(activeKeyId).put(salt).put(iv).put(ct);
```

**복호화 시 키 ID로 키 선택:**
```java
short keyId = bb.getShort();
byte[] master = masterKeys.get(keyId);
if (master == null) throw new CryptoException("Unknown keyId: " + keyId);
```

**키 로테이션 시나리오:**
1. 새 키 추가: `keys-v1: "1:oldkey,2:newkey"`
2. 활성 키 변경: `active-key-id: 2`
3. 새 데이터는 새 키로 암호화
4. 기존 데이터는 키 ID로 복호화 가능

#### 학습 포인트
- **키 분리**: 코드와 키 분리, 환경 변수/설정 파일 사용
- **키 로테이션**: 다중 키 지원으로 점진적 키 교체
- **키 ID 저장**: 암호문에 키 ID 포함하여 자동 키 선택

---

### 25. 키 파생: PBKDF2를 통한 보안 강화

#### 문제 상황
- 마스터 키를 직접 사용하면 위험
- 같은 평문이 같은 암호문 생성 (패턴 노출)
- 키 유출 시 모든 데이터 노출

#### 해결 과정

**PBKDF2 (Password-Based Key Derivation Function 2) 사용:**
```java
private SecretKey deriveKey(byte[] master, byte[] salt) throws GeneralSecurityException {
    KeySpec spec = new PBEKeySpec(
            Base64.getEncoder().encodeToString(master).toCharArray(),
            salt, pbkdf2Iter, 256);  // 256비트 키
    SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
}
```

**Salt 사용:**
```java
byte[] salt = new byte[saltLen];  // 16 bytes
rng.nextBytes(salt);  // 매번 새로운 salt 생성
```

**이점:**
1. **같은 평문, 다른 암호문**: Salt로 인해 동일 평문도 다른 암호문 생성
2. **무차별 대입 공격 방지**: PBKDF2 반복(200,000회)으로 키 추측 어려움
3. **키 분리**: 마스터 키와 실제 암호화 키 분리

#### 학습 포인트
- **Salt 사용**: 매번 새로운 salt 생성, 암호문과 함께 저장
- **PBKDF2 반복**: 충분한 반복 횟수 (200,000회 이상)
- **키 파생**: 마스터 키를 직접 사용하지 않고 파생 키 사용

---

### 26. 평문 호환성: 기존 데이터 마이그레이션

#### 문제 상황
- 기존 시스템에 평문 비밀번호가 저장되어 있음
- 암호화 도입 시 기존 데이터 처리 필요
- 점진적 마이그레이션 필요

#### 해결 과정

**평문 감지 및 처리:**
```java
public String decryptToString(String blobB64) {
    if (blobB64 == null) return null;
    try {
        byte[] blob = Base64.getDecoder().decode(blobB64);
        
        // 최소 크기 체크
        int minSize = 1 + 2 + saltLen + ivLen;
        if (blob.length < minSize) {
            return blobB64;  // 평문으로 간주
        }
        
        ByteBuffer bb = ByteBuffer.wrap(blob);
        byte version = bb.get();
        
        // 버전이 0x01이 아니면 평문
        if (version != 0x01) {
            return blobB64;  // 평문 그대로 반환
        }
        
        // 정상적인 복호화 진행
        ...
    } catch (IllegalArgumentException e) {
        // Base64 decoding 실패 → 평문
        return blobB64;
    } catch (BufferUnderflowException e) {
        // 버퍼 언더플로우 → 평문
        return blobB64;
    }
}
```

**점진적 마이그레이션:**
1. **읽기**: 평문이면 그대로 반환, 암호문이면 복호화
2. **쓰기**: 항상 암호화하여 저장
3. **자동 마이그레이션**: 다음 업데이트 시 자동 암호화

```java
// InstanceService.update()
if (req.getSecretRef() != null && !req.getSecretRef().trim().isEmpty()) {
    String encryptedPassword = aesGcmService.encryptString(req.getSecretRef());
    entity.setSecretRef(encryptedPassword);  // 항상 암호화하여 저장
}
```

#### 학습 포인트
- **하위 호환성**: 기존 평문 데이터 처리
- **점진적 마이그레이션**: 읽기는 평문 허용, 쓰기는 항상 암호화
- **에러 처리**: 복호화 실패 시 평문으로 간주

---

### 27. MyBatis TypeHandler: 자동 암복호화

#### 문제 상황
- 매번 수동으로 암복호화 코드 작성 → 실수 위험
- 비밀번호 필드마다 암복호화 로직 중복
- 개발자가 암호화를 깜빡할 수 있음

#### 해결 과정

**SecretStringTypeHandler 구현:**
```java
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(String.class)
public class SecretStringTypeHandler extends BaseTypeHandler<String> {
    
    private static volatile AesGcmService crypto;
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, 
                                   String parameter, JdbcType jdbcType) {
        String encrypted = crypto.encryptString(parameter);
        ps.setString(i, encrypted);  // 자동 암호화
    }
    
    @Override
    public String getNullableResult(ResultSet rs, String columnName) {
        String v = rs.getString(columnName);
        String decrypted = crypto.decryptToString(v);
        return decrypted;  // 자동 복호화
    }
}
```

**MyBatis Mapper에서 사용:**
```xml
<resultMap id="instanceMap" type="Instance">
    <result column="secret_ref" 
            property="secretRef" 
            typeHandler="com.dajanggan.global.crypto.mybatis.SecretStringTypeHandler"/>
</resultMap>
```

**초기화:**
```java
@PostConstruct
public void init() {
    SecretStringTypeHandler.setCrypto(aesGcmService);
}
```

**장점:**
1. **자동 처리**: 개발자가 신경 쓸 필요 없음
2. **일관성**: 모든 비밀번호 필드에 동일한 암복호화 적용
3. **안전성**: 실수로 평문 저장하는 것 방지

#### 학습 포인트
- **TypeHandler 활용**: MyBatis의 확장 포인트 활용
- **자동화**: 반복 작업을 프레임워크에 위임
- **초기화 순서**: `@PostConstruct`로 안전한 초기화

---

### 28. 에러 처리: 복호화 실패 시 안전한 처리

#### 문제 상황
- 복호화 실패 시 애플리케이션 중단?
- 평문 데이터와 암호문 데이터 혼재
- 키 변경/유실 시 데이터 복구 불가

#### 해결 과정

**단계적 에러 처리:**
```java
public String decryptToString(String blobB64) {
    try {
        // 1. Base64 디코딩 시도
        byte[] blob = Base64.getDecoder().decode(blobB64);
        
        // 2. 최소 크기 체크 (평문일 가능성)
        if (blob.length < minSize) {
            return blobB64;  // 평문으로 간주
        }
        
        // 3. 버전 체크
        byte version = bb.get();
        if (version != 0x01) {
            return blobB64;  // 평문
        }
        
        // 4. 정상적인 복호화
        ...
        
    } catch (IllegalArgumentException e) {
        // Base64 디코딩 실패 → 평문
        return blobB64;
    } catch (AEADBadTagException e) {
        // 인증 실패 → 데이터 변조 또는 키 불일치
        throw new CryptoException("Authentication failed", e);
    } catch (BufferUnderflowException e) {
        // 버퍼 부족 → 평문
        return blobB64;
    } catch (GeneralSecurityException e) {
        throw new CryptoException("Decryption failed", e);
    }
}
```

**에러 타입별 처리:**
1. **평문 데이터**: 그대로 반환 (하위 호환성)
2. **인증 실패 (AEADBadTagException)**: 예외 발생 (데이터 변조 가능성)
3. **키 불일치**: 예외 발생 (키 관리 문제)
4. **기타 에러**: 예외 발생 (시스템 문제)

#### 학습 포인트
- **단계적 처리**: 평문 → 암호문 순서로 처리
- **에러 분류**: 평문 vs 암호문 오류 구분
- **보안 vs 호환성**: 평문 허용은 보안 약화, 하지만 마이그레이션 필요

---

### 29. 키 저장 형식: Base64 인코딩

#### 문제 상황
- 암호화 키를 어떻게 저장할까?
- 바이너리 데이터를 설정 파일에 저장
- 키 전송/저장 시 인코딩 필요

#### 해결 과정

**Base64 인코딩 사용:**
```yaml
# application.yml
app:
  crypto:
    keys-v1: "1:base64encodedkey1,2:base64encodedkey2"
    active-key-id: 1
```

**키 파싱:**
```java
Map<Short, byte[]> store = new HashMap<>();
for (String e : keysV1.split(",")) {
    String[] kv = e.trim().split(":");
    store.put(Short.parseShort(kv[0]), 
              Base64.getDecoder().decode(kv[1]));  // Base64 디코딩
}
```

**암호문도 Base64:**
```java
return Base64.getEncoder().encodeToString(bb.array());
```

**이유:**
1. **텍스트 저장**: 바이너리를 텍스트로 변환
2. **전송 안전**: HTTP/설정 파일에서 안전하게 전송
3. **표준**: 널리 사용되는 인코딩

#### 학습 포인트
- **Base64 사용**: 바이너리 데이터를 텍스트로 변환
- **키 형식**: `keyId:base64key` 형식으로 다중 키 지원
- **일관성**: 키와 암호문 모두 Base64 사용

---

### 30. 보안 모범 사례 적용

#### 구현된 보안 기능

1. **강력한 암호화**: AES-256-GCM
2. **인증 태그**: 128비트 (변조 감지)
3. **Salt 사용**: 매번 새로운 salt (16 bytes)
4. **IV 사용**: 매번 새로운 IV (12 bytes)
5. **키 파생**: PBKDF2 (200,000회 반복)
6. **키 로테이션**: 다중 키 지원
7. **자동 암복호화**: TypeHandler로 투명하게 처리

#### 보안 체크리스트

✅ **암호화 알고리즘**: AES-GCM (AEAD)  
✅ **키 길이**: 256비트  
✅ **Salt**: 매번 새로운 salt 생성  
✅ **IV**: 매번 새로운 IV 생성  
✅ **키 파생**: PBKDF2 (충분한 반복)  
✅ **키 관리**: 환경 변수/설정 파일  
✅ **키 로테이션**: 다중 키 지원  
✅ **에러 처리**: 안전한 복호화 실패 처리  
✅ **평문 호환성**: 기존 데이터 마이그레이션 지원  

#### 추가 고려 사항

**프로덕션 환경:**
- 키는 환경 변수로 관리 (설정 파일에 저장하지 않음)
- 키 관리 서비스(KMS) 사용 고려
- 키 로테이션 정책 수립
- 암호화 로그 모니터링

**성능:**
- PBKDF2 반복 횟수 조정 (200,000회는 적절)
- 암호화/복호화 성능 모니터링
- 대량 데이터 처리 시 배치 처리 고려

#### 학습 포인트
- **보안 우선**: 편의성보다 보안 우선
- **모범 사례**: 검증된 암호화 라이브러리 사용
- **지속적 개선**: 보안 취약점 모니터링 및 업데이트

---

## JSONB vs 정규화: 알람 규칙 설계 분석

### 31. 알람 규칙 JSONB 사용 패턴 분석

#### 설계 배경
- 알람 규칙의 `levels` 필드를 JSONB로 저장
- notice/warning/critical 각 레벨별로 threshold, occurCount, minDuration, windowMin 저장
- 실제 검색 조건에는 JSONB를 사용하지 않음 (enabled, metric_type, instance_id, database_id만 사용)

#### 실제 사용 패턴

**1. 검색 쿼리 패턴:**
```sql
-- 활성화된 규칙 조회 (findActiveRules)
SELECT * FROM alarm_rule
WHERE enabled = true           -- ✅ 일반 컬럼
  AND metric_type = ?          -- ✅ 일반 컬럼
  AND (instance_id = ? OR instance_id IS NULL)  -- ✅ 일반 컬럼
  AND (database_id = ? OR database_id IS NULL)   -- ✅ 일반 컬럼
```

**2. JSON 파싱 빈도:**
- **호출 빈도**: 1분마다 `AlarmMetricsCollector.collectMetrics()` 실행
- **메트릭 개수**: 약 9개 메트릭
- **각 규칙마다 JSON 파싱**: 
  - `checkThresholds()` → `parseJsonLevels()` 1번
  - `shouldFireAlarm()` → `getOccurCount()`, `getMinDuration()`, `getWindowMin()` 각각 `parseJsonLevels()` (총 3번)
- **계산**: 10개 규칙 × 5번 파싱 = 50번/분 = 3,000번/시간

**참고**: ObjectMapper는 작은 JSON 파싱에 1ms 미만 소요되므로, 실제 성능 병목은 아닐 가능성이 높습니다.

#### JSONB vs 정규화 비교

**현재 구조 (JSONB - 반정규화):**
```sql
alarm_rule (
    alarm_rule_id,
    enabled,
    metric_type,
    instance_id,
    database_id,
    levels JSONB  -- {"notice": {...}, "warning": {...}, "critical": {...}}
)
```

**정규화 대안:**
```sql
alarm_rule (
    alarm_rule_id,
    enabled,
    metric_type,
    instance_id,
    database_id
)

alarm_rule_levels (
    alarm_rule_id,
    level_type,        -- 'notice', 'warning', 'critical'
    threshold,
    occur_count,
    min_duration_min,
    window_min
)
```

#### JSONB 방식의 장점

**1. 원자적 저장/업데이트**
- 한 번의 INSERT/UPDATE로 완결
- 트랜잭션 단순화

**2. 항상 함께 조회**
- 규칙 조회 시 levels도 항상 필요
- JOIN 오버헤드 제거

**3. 스키마 유연성**
- 레벨 타입 추가/필드 변경 시 ALTER TABLE 불필요
- JSON 구조만 변경하면 됨

**4. 검색 조건으로 사용 안 함**
- levels로 WHERE 조건을 거는 일이 없음
- 인덱스 불필요 (현재 사용 패턴 기준)

**5. 1:N 관계가 고정적**
- notice/warning/critical 3개로 거의 고정
- 별도 테이블로 관리할 필요성 낮음

**6. JSONB 검색도 가능 (필요 시)**
```sql
-- PostgreSQL JSONB는 GIN 인덱스로 검색 가능
CREATE INDEX idx_levels ON alarm_rule USING GIN (levels);

-- 내부 필드 검색 예시
SELECT * FROM alarm_rule 
WHERE levels -> 'critical' ->> 'threshold' = '90';
```

#### 정규화 방식의 장점

**1. 타입 안전성**
- DB 레벨에서 threshold가 숫자임을 보장
- JSON 구조 실수로 인한 런타임 에러 방지

**2. 명시적 스키마**
- 어떤 필드가 있는지 DB만 봐도 명확
- 문서화 효과

**3. 부분 업데이트 가능**
- 특정 레벨만 업데이트 가능

**4. 쿼리 가능성**
- 나중에 "threshold > 90인 규칙" 검색이 필요해질 수도

#### 실제 코드에서의 개선점

**현재 문제: 같은 JSON을 여러 번 파싱**
```java
// GenericAlarmCollector.java
private String checkThresholds(AlarmRule rule, BigDecimal currentValue) {
    Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels()); // 파싱 1
    ...
}

private int getOccurCount(String levelsJson, String level) {
    Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson); // 파싱 2
    ...
}
```

**해결책: 파싱 결과 캐싱 (스키마 변경 불필요)**
```java
// AlarmRule 도메인에 파싱된 객체 필드 추가
public class AlarmRule {
    private String levels;  // JSONB 원본
    private transient AlarmRuleLevels parsedLevels;  // 파싱된 객체 (캐시)
    
    public AlarmRuleLevels getParsedLevels() {
        if (parsedLevels == null) {
            parsedLevels = parseJsonLevels(levels);
        }
        return parsedLevels;
    }
}
```

이렇게 하면 규칙당 1번만 파싱하고, 스키마 변경 없이 성능 문제 해결됩니다.

#### 성능 비교 요약

| 기준 | JSONB (반정규화) | 정규화 |
|------|-----------------|--------|
| **저장** | ✅ 간단 (한 번에 저장) | ❌ 복잡 (INSERT 2번) |
| **조회** | ✅ JOIN 불필요 | ❌ JOIN 필요 |
| **파싱** | ⚠️ 필요 (캐싱으로 해결 가능) | ✅ 불필요 |
| **타입 안전성** | ⚠️ 런타임 검증 | ✅ DB 레벨 보장 |
| **유연성** | ✅ 높음 (스키마 변경 불필요) | ❌ 낮음 (ALTER 필요) |
| **검색** | ✅ 가능 (GIN 인덱스) | ✅ 가능 (일반 인덱스) |

#### 학습 포인트

**JSONB vs 정규화 선택 기준:**

**JSONB(반정규화)가 적합한 경우:**
- ✅ 검색 조건으로 사용하지 않음
- ✅ 항상 함께 조회됨
- ✅ 구조가 가변적이거나 자주 변경됨
- ✅ 원자적 저장/업데이트가 중요함
- ✅ 1:N 관계가 고정적이고 개수가 적음

**정규화가 적합한 경우:**
- ✅ 내부 필드로 검색이 필요함
- ✅ 타입 안전성이 중요함
- ✅ 부분 업데이트가 빈번함
- ✅ 레벨 개수가 많고 동적임

**현재 알람 규칙의 경우:**
- 구조가 고정적 (notice/warning/critical 3개)
- 검색 조건에 JSONB 사용 안 함
- 항상 함께 조회됨
- **→ JSONB 선택이 합리적임**

**성능 최적화:**
- 파싱 오버헤드는 코드 레벨에서 해결 가능 (캐싱)
- 실제 성능 병목은 아닐 가능성이 높음
- 정규화로 전환하는 것보다 파싱 캐싱이 더 실용적

**결론:**
- **현재 시스템**: JSONB 유지 + 파싱 캐싱 추가로 충분
- **새로 설계한다면**: 정규화도 좋은 선택 (틀린 선택 아님)
- **둘 다 합리적**: 상황과 요구사항에 따라 선택

---

## 알람 윈도우 설계: 플래핑 문제와 개선 방향

### 32. 알람 윈도우 설계로 인한 플래핑 문제

#### 문제 상황
현재 알람 시스템은 윈도우 기반 설계(`windowMin`, `occurCount`, `minDuration`)를 사용하고 있지만, 다음과 같은 문제가 발생합니다:

1. **플래핑 (Flapping)**: 알람이 너무 자주 발생했다가 해제됨
2. **탐지 지연**: 윈도우를 길게 하면 실제 문제를 늦게 발견
3. **일관성 부족**: 팀마다 `windowMin`, `minDuration`, `occurCount` 기준이 달라짐
4. **성능 문제**: 많은 규칙/메트릭 동시 확인 시 실시간 조회 비용 증가

#### 현재 구현 상태

**구현된 기능:**
- ✅ 윈도우 기반 알람 체크 (`windowMin`, `occurCount`, `minDuration`)
- ✅ 같은 레벨에서 재알람 방지 (이미 FIRED 상태면 재발생 안 함)
- ✅ 레벨 변경 시 즉시 알람 발생
- ✅ 트래킹 상태 관리 (`AlarmTracking`)

**미구현 기능:**
- ❌ **히스테리시스**: 발생 조건과 복구 조건 분리 없음
- ❌ **쿨다운**: 재알림 최소 간격 제어 없음
- ❌ **핫/콜드 분리**: `is_active`, `last_active_at` 같은 빠른 조회용 컬럼 없음
- ❌ **플래핑 감지**: 이벤트 이력 테이블(`alarm_rule_event`) 없음
- ❌ **UI 최적화**: 요약/상세 API 분리 없음

#### 현재 구현의 플래핑 방지 메커니즘

**1. 윈도우 기반 발생 조건:**
```java
// GenericAlarmCollector.java - shouldFireAlarm()
int requiredCount = getOccurCount(rule.getLevels(), level);      // 발생 횟수
int requiredDuration = getMinDuration(rule.getLevels(), level); // 최소 지속 시간
int windowMin = getWindowMin(rule.getLevels(), level);          // 윈도우 크기

// 1. 윈도우 내에서 발생 횟수 체크
if (tracking.getConsecutiveCount() < requiredCount) {
    return false;  // 발생 횟수 부족 → 알람 발생 안 함
}

// 2. 최소 지속 시간 체크
long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
if (durationMinutes < requiredDuration) {
    return false;  // 지속 시간 부족 → 알람 발생 안 함
}
```

**효과**: 
- 짧은 스파이크는 `minDuration`으로 무시됨
- 윈도우 내에서 여러 번 발생해야 알람 발생 (`occurCount`)
- **→ 플래핑을 어느 정도 방지할 수 있음**

**2. 같은 레벨 재알람 방지:**
```java
// 같은 레벨에서 이미 FIRED면 재알람 안 함
if ("FIRED".equals(tracking.getStatus()) && previousLevel != null && level.equals(previousLevel)) {
    return false;  // 재알람 방지
}
```

**효과**: 같은 레벨에서 중복 알람 방지

#### 현재 구현의 한계점

**1. 복구 조건이 발생 조건과 동일:**
```java
// 정상 범위로 돌아오면 즉시 해제
if (triggeredLevel == null) {
    if (tracking != null && "FIRED".equals(tracking.getStatus())) {
        resolveAlarm(tracking, metricType);  // 즉시 해제
    }
}
```

**문제**: 
- 발생 조건: `threshold > 1000000 for 2분`
- 복구 조건: `threshold <= 1000000` (즉시)
- **→ 임계치 근처에서 오락가락하면 플래핑 가능**

**개선**: 히스테리시스 적용 (복구 임계치를 발생 임계치보다 낮게)

**2. 레벨 변경 시 즉시 재알람:**
```java
// 레벨 변경 시 무조건 알람 발생
if (shouldFireAlarm(...) || levelChanged) {
    fireAlarm(...);  // 즉시 발생
}
```

**문제**: 
- NOTICE → WARNING → CRITICAL로 빠르게 상승하면 연속 알람 발생
- **→ 쿨다운이 없어서 알람 노이즈 증가 가능**

**개선**: 쿨다운 적용 (재알림 최소 간격)

**3. UI 조회 성능:**
```java
// AlarmTrackingService.getTrackingStatus()
// 매번 JOIN 쿼리 실행
SELECT at.*, ar.metric_type, i.instance_name, d.database_name
FROM alarm_tracking at
LEFT JOIN alarm_rule ar ON ...
LEFT JOIN monitor_instance i ON ...
LEFT JOIN monitor_database d ON ...
```

**문제**: 핵심 상태(`is_active`, `current_severity`)만 빠르게 조회할 수 없음

**개선**: 핫/콜드 분리 (빠른 조회용 컬럼 추가)

#### 개선 방안

**1. 히스테리시스 적용 (구현 완료)**
```java
// GenericAlarmCollector.java - shouldResolveAlarm()
// 발생 조건: threshold > 1000000 for 2분
// 복구 조건: threshold < 800000 for 3분 (더 낮은 임계치, 더 긴 시간)
private boolean shouldResolveAlarm(
        Connection conn,
        AlarmRule rule,
        AlarmTracking tracking,
        BigDecimal currentValue,
        String currentLevel
) {
    // 복구 임계치와 지속 시간 가져오기
    BigDecimal resolveThreshold = getResolveThreshold(rule.getLevels(), currentLevel);
    int resolveDuration = getResolveDuration(rule.getLevels(), currentLevel);
    String operator = rule.getOperator();
    
    // 복구 임계치가 설정되지 않았으면 기본값 사용 (발생 임계치의 80%)
    if (resolveThreshold == null) {
        BigDecimal fireThreshold = getThresholdValue(parseJsonLevels(rule.getLevels()), currentLevel.toLowerCase());
        if (fireThreshold != null) {
            resolveThreshold = fireThreshold.multiply(new BigDecimal("0.8"));
        } else {
            return true; // 복구 임계치를 알 수 없으면 즉시 복구
        }
    }
    
    // 복구 지속 시간 기본값 (설정되지 않았으면 3분)
    if (resolveDuration <= 0) {
        resolveDuration = 3;
    }
    
    // 1. 복구 임계치 체크 (operator 반대 방향)
    String resolveOperator = getReverseOperator(operator);
    boolean belowResolveThreshold = compareValue(currentValue, resolveThreshold, resolveOperator);
    
    if (!belowResolveThreshold) {
        return false; // 복구 임계치 미달
    }
    
    // 2. 복구 지속 시간 체크
    long durationMinutes = Duration.between(tracking.getFirstTriggeredAt(), now).toMinutes();
    return durationMinutes >= resolveDuration;
}
```

**구현 내용:**
- `AlarmRuleDto.ThresholdLevel`에 `resolveThreshold`, `resolveDurationMin` 필드 추가
- `shouldResolveAlarm()` 메서드로 복구 조건 체크
- `getResolveThreshold()`, `getResolveDuration()` 메서드 추가
- `getReverseOperator()` 메서드로 operator 반대 방향 처리
- 기본값: 복구 임계치는 발생 임계치의 80%, 복구 지속 시간은 발생 지속 시간의 1.5배

**2. 쿨다운 적용 (구현 완료)**
```java
// GenericAlarmCollector.java - isInCooldown()
// 같은 레벨의 알람이 최근 cooldownMin 분 내에 발생했는지 확인
private boolean isInCooldown(AlarmRule rule, String level) {
    int cooldownMin = getCooldown(rule.getLevels(), level);
    if (cooldownMin <= 0) {
        return false; // 쿨다운이 설정되지 않았으면 쿨다운 없음
    }

    // 마지막 알람 발생 시간 조회
    OffsetDateTime lastFiredAt = alarmFeedMapper.selectLastFiredAtByRuleId(rule.getAlarmRuleId());
    if (lastFiredAt == null) {
        return false; // 이전 알람이 없으면 쿨다운 없음
    }

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime cooldownEnd = lastFiredAt.plusMinutes(cooldownMin);
    
    return now.isBefore(cooldownEnd);
}

// fireAlarm() 전에 쿨다운 체크 (레벨 변경 시에는 쿨다운 무시)
if (!levelChanged && isInCooldown(rule, triggeredLevel)) {
    log.debug("⏸️ 쿨다운 중: 알람 발생 건너뜀");
    return; // 쿨다운 중이면 알람 발생 안 함
}
```

**구현 내용:**
- `AlarmRuleDto.ThresholdLevel`에 `cooldownMin` 필드 추가
- `isInCooldown()` 메서드로 쿨다운 체크
- `getCooldown()` 메서드로 쿨다운 시간 추출
- `selectLastFiredAtByRuleId()` 메서드로 마지막 알람 발생 시간 조회
- 레벨 변경 시에는 쿨다운 무시 (중요한 변화이므로)
- 기본값: critical 15분, warning 30분, notice 60분

**3. 핫/콜드 분리**
```sql
-- alarm_rule 테이블에 빠른 조회용 컬럼 추가
ALTER TABLE alarm_rule ADD COLUMN is_active BOOLEAN DEFAULT false;
ALTER TABLE alarm_rule ADD COLUMN last_active_at TIMESTAMP;
ALTER TABLE alarm_rule ADD COLUMN current_severity VARCHAR(20);

-- 인덱스 추가
CREATE INDEX idx_alarm_rule_active ON alarm_rule(is_active, last_active_at);

-- 빠른 조회 쿼리
SELECT alarm_rule_id, current_severity, last_active_at
FROM alarm_rule
WHERE is_active = true
ORDER BY last_active_at DESC;
```

**4. 플래핑 감지**
```sql
-- 이벤트 이력 테이블 생성
CREATE TABLE alarm_rule_event (
    event_id BIGSERIAL PRIMARY KEY,
    alarm_rule_id BIGINT NOT NULL,
    event_type VARCHAR(20),  -- 'activated', 'deactivated', 'severity_changed'
    severity VARCHAR(20),
    occurred_at TIMESTAMP NOT NULL,
    FOREIGN KEY (alarm_rule_id) REFERENCES alarm_rule(alarm_rule_id)
);

-- 플래핑 탐지 쿼리
SELECT alarm_rule_id, COUNT(*) as toggles
FROM alarm_rule_event
WHERE occurred_at >= now() - interval '1 hour'
  AND event_type IN ('activated', 'deactivated')
GROUP BY alarm_rule_id
HAVING COUNT(*) > 5;  -- 1시간에 5번 이상 토글 = 플래핑
```

**5. UI 최적화**
```java
// 요약 API (빠름) - 핵심 지표만
@GetMapping("/api/alarms/summary")
public AlarmSummaryDto getAlarmSummary() {
    // is_active만 빠르게 조회
    return AlarmSummaryDto.builder()
        .activeCount(countActiveAlarms())
        .criticalCount(countBySeverity("CRITICAL"))
        .recentAlarms(getRecentAlarms(10))
        .build();
}

// 상세 API (느림) - 필요 시 호출
@GetMapping("/api/alarms/{id}/detail")
public AlarmDetailDto getAlarmDetail(@PathVariable Long id) {
    // 상세 메트릭, 쿼리 로그 등 무거운 데이터
    return AlarmDetailDto.builder()
        .feed(getAlarmFeed(id))
        .metricHistory(getMetricHistory(id))
        .relatedObjects(getRelatedObjects(id))
        .build();
}
```

#### 권장 파라미터 (초기값)

```json
{
  "critical": {
    "threshold": 1000000,
    "occurCount": 2,
    "minDuration": 60,           // 1분
    "windowMin": 2,              // 2분 윈도우
    "resolveThreshold": 800000,   // 복구 임계치 (발생보다 낮음)
    "resolveDurationMin": 180,    // 3분 지속 시 복구
    "cooldownMin": 15            // 15분 쿨다운 (구현 완료)
  },
  "warning": {
    "threshold": 500000,
    "occurCount": 2,
    "minDuration": 120,           // 2분
    "windowMin": 5,              // 5분 윈도우
    "resolveThreshold": 400000,
    "resolveDurationMin": 300,    // 5분 지속 시 복구
    "cooldownMin": 30            // 30분 쿨다운 (구현 완료)
  },
  "notice": {
    "threshold": 100000,
    "occurCount": 2,
    "minDuration": 60,            // 1분
    "windowMin": 15,             // 15분 윈도우
    "resolveThreshold": 80000,
    "resolveDurationMin": 120,    // 2분 지속 시 복구
    "cooldownMin": 60            // 60분 쿨다운 (구현 완료)
  }
}
```

#### 학습 포인트

**현재 시스템의 플래핑 방지 메커니즘:**
- ✅ **윈도우 기반 발생 조건**: `windowMin` 내에서 `occurCount`번 발생해야 알람
- ✅ **최소 지속 시간**: `minDuration` 이상 지속되어야 알람
- ✅ **같은 레벨 재알람 방지**: 이미 FIRED면 재알람 안 함
- **→ 기본적인 플래핑 방지는 가능함**

**하지만 여전히 개선 가능한 부분:**
- ⚠️ **히스테리시스 없음**: 발생/복구 조건이 동일 → 임계치 근처에서 플래핑 가능
- ⚠️ **쿨다운 없음**: 레벨 변경 시 즉시 재알람 → 알람 노이즈 증가 가능
- ⚠️ **UI 조회 성능**: 핵심 상태만 빠르게 조회 불가

**개선 방향:**
- **히스테리시스**: 발생 조건과 복구 조건 분리
- **쿨다운**: 재알림 최소 간격 제어
- **핫/콜드 분리**: 빠른 조회용 컬럼 추가
- **플래핑 감지**: 이벤트 이력으로 자동 탐지
- **UI 최적화**: 요약/상세 API 분리

**핵심 원칙:**
> "핫한 상태(활성화 여부)는 컬럼/캐시로, 가변 설정은 JSONB로 — 윈도우는 히스테리시스·쿨다운·플래그로 보완"

이 패턴으로 UI 로딩(핵심 지표)은 즉시, 플래핑·오탐은 크게 줄이고 실제 장애는 놓치지 않게 설계할 수 있습니다.

---

### 33. 지속시간 추적: Count 기반 vs 실제 시간 기반

#### Problem: 지속시간 추적의 어려움

**Pain Points:**
- DB 구조에서 지속시간을 직접 추적하기 어려움
- 평가주기가 불규칙하거나 누락될 수 있음
- 실제 경과 시간과 평가 횟수가 다를 수 있음

**Needs:**
- 평가주기마다 조건을 체크하고 지속시간을 추적
- 조건 만족 시 count 증가
- count × 평가주기 >= 지속시간 → 알람 발송
- 조건 불만족 시 tracking 삭제 (리셋)

#### 현재 구현 방식 분석

**현재 구현:**
```java
// GenericAlarmCollector.java
// 1. 조건 만족 시 count 증가
if (triggeredLevel != null) {
    if (levelChanged) {
        tracking.setConsecutiveCount(1);  // 리셋
        tracking.setFirstTriggeredAt(now);
    } else {
        tracking.setConsecutiveCount(tracking.getConsecutiveCount() + 1);  // 증가
    }
}

// 2. 지속시간 체크 (실제 경과 시간 사용)
long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
if (durationMinutes < requiredDuration) {
    return false;  // 지속 시간 부족
}

// 3. 발생 횟수 체크 (count 사용)
if (tracking.getConsecutiveCount() < requiredCount) {
    return false;  // 발생 횟수 부족
}
```

**평가주기:** 1분마다 (`@Scheduled(fixedDelay = 60000)`)

#### Count 기반 vs 실제 시간 기반 비교

**현재 방식 (실제 시간 기반):**
```java
// 실제 경과 시간 사용
long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
if (durationMinutes < requiredDuration) {
    return false;
}
```

**장점:**
- ✅ 정확한 시간 측정 (평가주기와 무관)
- ✅ 평가가 누락되어도 정확한 시간 계산
- ✅ 평가주기가 변경되어도 영향 없음
- ✅ 실제 경과 시간을 정확히 반영

**단점:**
- ⚠️ 평가가 누락되면 지속시간은 지나갔지만 알람이 발생하지 않을 수 있음
- ⚠️ 불연속적인 평가에 대응 어려움

**제안 방식 (Count 기반):**
```java
// count × 평가주기 >= 지속시간
int evaluationPeriodMin = 1;  // 1분
int requiredDurationMin = 10;  // 10분
int requiredCount = requiredDurationMin / evaluationPeriodMin;  // 10회

if (tracking.getConsecutiveCount() < requiredCount) {
    return false;  // count 부족
}
```

**장점:**
- ✅ 평가주기와 명확하게 연결
- ✅ 불연속적인 평가에도 대응 가능
- ✅ count 기반으로 간단하게 계산

**단점:**
- ❌ 평가주기가 변경되면 문제 발생 (예: 1분 → 2분으로 변경 시)
- ❌ 실제 경과 시간과 count 기반 시간이 다를 수 있음
- ❌ 평가가 누락되면 count가 증가하지 않아 지속시간이 지나가도 알람 발생 안 함

#### 하이브리드 방식 (현재 구현 - 권장)

**현재 구현은 이미 하이브리드 방식:**
```java
// 1. 발생 횟수: count 기반 (평가주기와 연결)
if (tracking.getConsecutiveCount() < requiredCount) {
    return false;  // 윈도우 내 발생 횟수 부족
}

// 2. 지속 시간: 실제 시간 기반 (정확한 시간 측정)
long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
if (durationMinutes < requiredDuration) {
    return false;  // 실제 경과 시간 부족
}
```

**효과:**
- ✅ 발생 횟수는 count로 체크 (평가주기와 연결)
- ✅ 지속 시간은 실제 시간으로 체크 (정확성 보장)
- ✅ 두 조건을 모두 만족해야 알람 발생

#### Count 기반만 사용할 경우의 문제

**시나리오 1: 평가주기 변경**
```
평가주기: 1분 → 2분으로 변경
지속시간: 10분
필요 count: 10분 / 1분 = 10회 → 10분 / 2분 = 5회로 변경 필요
→ 기존 count와 불일치 발생
```

**시나리오 2: 평가 누락**
```
평가주기: 1분
지속시간: 10분 (필요 count: 10회)
실제 상황: 10분 경과, 하지만 평가가 5회만 실행됨 (count = 5)
→ 실제로는 10분 지났지만 count가 5이므로 알람 발생 안 함
```

**시나리오 3: 실제 시간과 불일치**
```
평가주기: 1분
count = 10 (10회 평가)
실제 경과 시간: 12분 (평가가 지연되거나 빠르게 실행됨)
→ count는 10분이지만 실제로는 12분 경과
```

#### 현재 구현의 효과성

**하이브리드 방식의 장점:**

1. **정확성**: 실제 경과 시간을 사용하므로 정확함
   ```java
   long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
   // 실제 경과 시간: 정확하게 측정됨
   ```

2. **유연성**: 평가주기가 변경되어도 영향 없음
   ```java
   // 평가주기가 1분 → 2분으로 변경되어도
   // 실제 경과 시간은 정확하게 측정됨
   ```

3. **안정성**: 평가가 누락되어도 실제 시간은 정확히 측정됨
   ```java
   // 평가가 5회만 실행되어도
   // 실제 경과 시간은 10분이면 10분으로 정확히 측정됨
   ```

4. **발생 횟수 보장**: count로 평가주기와 연결
   ```java
   // count로 윈도우 내 발생 횟수 보장
   if (tracking.getConsecutiveCount() < requiredCount) {
       return false;  // 발생 횟수 부족
   }
   ```

#### 개선 제안 (선택사항)

**현재 방식이 이미 최적이지만, 추가 검증 가능:**

```java
// 현재: 실제 시간만 체크
long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
if (durationMinutes < requiredDuration) {
    return false;
}

// 개선: count도 함께 체크 (보조 검증)
int evaluationPeriodMin = 1;  // 평가주기
int minRequiredCount = (requiredDuration + evaluationPeriodMin - 1) / evaluationPeriodMin;
if (tracking.getConsecutiveCount() < minRequiredCount) {
    log.warn("지속시간은 충족했지만 평가 횟수 부족: duration={}분, count={}, requiredCount={}",
            durationMinutes, tracking.getConsecutiveCount(), minRequiredCount);
    // 실제 시간이 충족했으면 알람 발생 (유연성)
    // 하지만 count도 체크하여 평가주기 보장
}
```

**하지만 현재 방식이 더 간단하고 효과적:**
- 실제 시간만 체크해도 충분함
- count는 발생 횟수 체크에만 사용하는 것이 명확함

#### 학습 포인트

**현재 구현의 효과성:**
- ✅ **하이브리드 방식**: count(발생 횟수) + 실제 시간(지속 시간)
- ✅ **정확성과 유연성**: 실제 시간으로 정확하게, count로 평가주기 보장
- ✅ **효과적**: 두 조건을 모두 만족해야 알람 발생

**Count 기반만 사용할 경우의 문제:**
- ❌ 평가주기 변경 시 재계산 필요
- ❌ 평가 누락 시 지속시간이 지나가도 알람 발생 안 함
- ❌ 실제 경과 시간과 불일치 가능

**결론:**
현재 구현 방식(하이브리드)이 더 효과적입니다. Count는 발생 횟수 체크에, 실제 시간은 지속 시간 체크에 사용하는 것이 최적입니다.

**추가 고려사항:**
- 평가주기가 고정적이고 정확하다면 count 기반도 가능
- 하지만 실제 시간 기반이 더 안정적이고 유연함
- 현재 방식(하이브리드)이 두 방식의 장점을 모두 활용

---

### 34. 플래핑 현상 방지 솔루션

#### Problem 1: 알람이 너무 자주 토글됨

**Pain Points:**
- CPU 90% → 알람 발생 🚨
- CPU 89% → 알람 해제 ✅
- CPU 91% → 알람 발생 🚨 (플래핑!)
- 30초마다 알람이 발생했다가 해제되는 반복
- 알람 노이즈 증가로 실제 중요한 알람을 놓칠 수 있음

**Needs:**
- 알람이 발생한 후 일정 시간 동안은 재알람을 방지해야 함
- 레벨이 변경되면 즉시 알람 발생 (중요한 변화이므로)
- 심각도에 따라 쿨다운 시간을 다르게 설정

**Solution 1: 쿨다운 (Cooldown) - 재알림 최소 간격**

**구현 완료:**
```java
// GenericAlarmCollector.java - isInCooldown()
// 같은 레벨의 알람이 최근 cooldownMin 분 내에 발생했는지 확인
private boolean isInCooldown(AlarmRule rule, String level) {
    int cooldownMin = getCooldown(rule.getLevels(), level);
    if (cooldownMin <= 0) {
        return false; // 쿨다운이 설정되지 않았으면 쿨다운 없음
    }

    // 마지막 알람 발생 시간 조회
    OffsetDateTime lastFiredAt = alarmFeedMapper.selectLastFiredAtByRuleId(rule.getAlarmRuleId());
    if (lastFiredAt == null) {
        return false; // 이전 알람이 없으면 쿨다운 없음
    }

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime cooldownEnd = lastFiredAt.plusMinutes(cooldownMin);
    
    return now.isBefore(cooldownEnd);
}

// fireAlarm() 전에 쿨다운 체크 (레벨 변경 시에는 쿨다운 무시)
if (!levelChanged && isInCooldown(rule, triggeredLevel)) {
    log.debug("⏸️ 쿨다운 중: 알람 발생 건너뜀");
    return; // 쿨다운 중이면 알람 발생 안 함
}
```

**효과:**
- ✅ 같은 레벨의 알람이 `cooldownMin` 분 내에 재발생하지 않음
- ✅ 레벨 변경 시에는 쿨다운 무시 (중요한 변화이므로)
- ✅ 기본값: critical 15분, warning 30분, notice 60분

---

#### Problem 2: 임계치 근처에서 오락가락

**Pain Points:**
- 발생 조건: `threshold > 1000000` → 알람 발생
- 복구 조건: `threshold <= 1000000` → 즉시 해제
- 임계치 근처에서 오락가락하면 플래핑 발생
- 예: 1000001 → 알람 발생, 999999 → 해제, 1000001 → 알람 발생 (반복)

**Needs:**
- 발생 조건과 복구 조건을 다르게 설정하여 플래핑 방지
- 복구 임계치는 발생 임계치보다 낮게 설정
- 복구 지속 시간도 체크

**Solution 2: 히스테리시스 (Hysteresis) - 발생 ≠ 복구 조건**

**구현 완료:**
```java
// GenericAlarmCollector.java - shouldResolveAlarm()
// 발생 조건: threshold > 1000000 for 2분
// 복구 조건: threshold < 800000 for 3분 (더 낮은 임계치, 더 긴 시간)
private boolean shouldResolveAlarm(
        Connection conn,
        AlarmRule rule,
        AlarmTracking tracking,
        BigDecimal currentValue,
        String currentLevel
) {
    // 복구 임계치와 지속 시간 가져오기
    BigDecimal resolveThreshold = getResolveThreshold(rule.getLevels(), currentLevel);
    int resolveDuration = getResolveDuration(rule.getLevels(), currentLevel);
    
    // 복구 임계치가 설정되지 않았으면 기본값 사용 (발생 임계치의 80%)
    if (resolveThreshold == null) {
        BigDecimal fireThreshold = getThresholdValue(parseJsonLevels(rule.getLevels()), currentLevel.toLowerCase());
        if (fireThreshold != null) {
            resolveThreshold = fireThreshold.multiply(new BigDecimal("0.8"));
        } else {
            return true; // 복구 임계치를 알 수 없으면 즉시 복구
        }
    }
    
    // 복구 지속 시간 기본값 (설정되지 않았으면 3분)
    if (resolveDuration <= 0) {
        resolveDuration = 3;
    }
    
    // 1. 복구 임계치 체크 (operator 반대 방향)
    String resolveOperator = getReverseOperator(operator);
    boolean belowResolveThreshold = compareValue(currentValue, resolveThreshold, resolveOperator);
    
    if (!belowResolveThreshold) {
        return false; // 복구 임계치 미달
    }
    
    // 2. 복구 지속 시간 체크
    long durationMinutes = Duration.between(tracking.getFirstTriggeredAt(), now).toMinutes();
    return durationMinutes >= resolveDuration;
}
```

**효과:**
- ✅ 발생 조건: `threshold > 1000000 for 2분` → 알람 발생
- ✅ 복구 조건: `threshold < 800000 for 3분` → 알람 해제
- ✅ 임계치 근처에서 오락가락해도 플래핑 감소
- ✅ 기본값: 복구 임계치는 발생 임계치의 80%, 복구 지속 시간은 발생 지속 시간의 1.5배

---

#### Problem 3: 짧은 스파이크로 인한 오탐

**Pain Points:**
- 일시적인 스파이크로 인한 알람 발생
- 실제 문제가 아닌데 알람이 발생하여 노이즈 증가
- 예: 1초 동안 CPU 100% → 알람 발생 (실제 문제 아님)

**Needs:**
- 짧은 스파이크는 무시하고 지속적인 문제만 알람 발생
- 윈도우 내에서 여러 번 발생해야 알람 발생
- 최소 지속 시간 이상 지속되어야 알람 발생

**Solution 3: 윈도우 기반 발생 조건 + 최소 지속 시간**

**구현 완료:**
```java
// GenericAlarmCollector.java - shouldFireAlarm()
// 1. 윈도우 기반 발생 횟수 체크
int requiredCount = getOccurCount(rule.getLevels(), level);      // 발생 횟수
int windowMin = getWindowMin(rule.getLevels(), level);            // 윈도우 크기

// 윈도우 내에서 발생 횟수 체크
if (tracking.getConsecutiveCount() < requiredCount) {
    return false;  // 발생 횟수 부족 → 알람 발생 안 함
}

// 2. 최소 지속 시간 체크
int requiredDuration = getMinDuration(rule.getLevels(), level);  // 최소 지속 시간
long durationMinutes = Duration.between(firstTriggered, now).toMinutes();
if (durationMinutes < requiredDuration) {
    return false;  // 지속 시간 부족 → 알람 발생 안 함
}
```

**효과:**
- ✅ 윈도우 내에서 `occurCount`번 발생해야 알람 발생
- ✅ 최소 `minDuration` 분 이상 지속되어야 알람 발생
- ✅ 짧은 스파이크는 무시됨
- ✅ 기본값: occurCount 1-3회, windowMin 1-15분, minDuration 1-10분

---

#### Problem 4: 레벨 변경 시 연속 알람 발생

**Pain Points:**
- NOTICE → WARNING → CRITICAL로 빠르게 상승하면 연속 알람 발생
- 같은 문제에 대해 여러 번 알람 발생하여 노이즈 증가
- 예: NOTICE 알람 → 1분 후 WARNING 알람 → 1분 후 CRITICAL 알람 (3번 알람)

**Needs:**
- 레벨 변경 시에는 즉시 알람 발생 (중요한 변화이므로)
- 하지만 같은 레벨에서는 재알람 방지
- 쿨다운으로 연속 알람 방지

**Solution 4: 레벨 변경 감지 + 쿨다운 조합**

**구현 완료:**
```java
// GenericAlarmCollector.java - processAlarmRule()
// 레벨 변경 감지
String previousLevel = tracking != null ? tracking.getCurrentLevel() : null;
boolean levelChanged = previousLevel != null && !previousLevel.equals(triggeredLevel);

// 레벨 변경 시에는 쿨다운 무시하고 즉시 알람 발생
if (shouldFireAlarm(...) || levelChanged) {
    // 쿨다운 체크 (레벨 변경 시에는 쿨다운 무시)
    if (!levelChanged && isInCooldown(rule, triggeredLevel)) {
        return; // 쿨다운 중이면 알람 발생 안 함
    }
    fireAlarm(...);  // 알람 발생
}
```

**효과:**
- ✅ 레벨 변경 시에는 즉시 알람 발생 (중요한 변화)
- ✅ 같은 레벨에서는 쿨다운 적용하여 재알람 방지
- ✅ 알람 노이즈 감소

---

#### 종합 효과

**플래핑 방지 메커니즘:**

1. **윈도우 기반 발생 조건**: 짧은 스파이크 무시
2. **최소 지속 시간**: 일시적인 변동 무시
3. **히스테리시스**: 임계치 근처에서 오락가락 방지
4. **쿨다운**: 같은 레벨에서 재알람 방지
5. **레벨 변경 감지**: 중요한 변화는 즉시 알람 발생

**결과:**
- ✅ 플래핑 현상 크게 감소
- ✅ 알람 노이즈 감소
- ✅ 실제 중요한 알람은 놓치지 않음
- ✅ 운영자 경험 개선

---

## 참고: 주요 기술 스택

- **ORM**: MyBatis (XML Mapper)
- **트랜잭션**: Spring `@Transactional`
- **비동기 통신**: Spring WebFlux `WebClient`
- **배치 처리**: Spring Batch
- **데이터베이스**: PostgreSQL (JSONB, CTE 활용)
- **암호화**: AES-GCM

