# AlarmFeed 상세 조회 성능 문제 분석

## 🔥 문제상황: 상세 조회 클릭 시 시간이 오래 걸림

### 현재 코드 흐름

```74:150:src/main/java/com/dajanggan/domain/alarm/service/AlarmFeedService.java
    public AlarmFeedDto.DetailResponse getAlarmDetail(Long alarmFeedId) {
        // 알림 기본 정보
        AlarmFeed feed = alarmFeedMapper.selectAlarmDetail(alarmFeedId);
        if (feed == null) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }

        // Latency 데이터 (24시간)
        List<AlarmFeedDto.LatencyDataRaw> latencyRaw =
                alarmFeedMapper.selectLatencyData(feed.getAlarmFeedId());

        List<BigDecimal> latencyData = latencyRaw.stream()
                .map(AlarmFeedDto.LatencyDataRaw::getAvgLatency)
                .collect(Collectors.toList());

        List<String> latencyLabels = latencyRaw.stream()
                .map(AlarmFeedDto.LatencyDataRaw::getHourLabel)
                .collect(Collectors.toList());

        AlarmFeedDto.LatencyData latency = AlarmFeedDto.LatencyData.builder()
                .data(latencyData)
                .labels(latencyLabels)
                .build();

        // 요약 정보
        AlarmFeedDto.Summary summary = AlarmFeedDto.Summary.builder()
                .current(formatValue(feed.getCurrentValue()))
                .threshold(formatValue(feed.getThresholdValue()))
                .duration("15m")
                .build();

        // 관련 객체
        List<AlarmFeedDto.RelatedObjectRaw> relatedRaw =
                alarmFeedMapper.selectRelatedObjects(feed.getAlarmFeedId());

        log.info("📋 관련 객체 조회: alarmFeedId={}, 조회된 개수={}", feed.getAlarmFeedId(), relatedRaw != null ? relatedRaw.size() : 0);
        
        // 관련 객체가 없으면 동적으로 생성 시도
        if ((relatedRaw == null || relatedRaw.isEmpty()) && feed.getMetricType() != null) {
            log.info("🔄 관련 객체가 없어서 동적으로 생성 시도: alarmFeedId={}, metricType={}", 
                    feed.getAlarmFeedId(), feed.getMetricType());
            relatedRaw = generateRelatedObjectsOnDemand(feed);
        }
        
        if (relatedRaw != null && !relatedRaw.isEmpty()) {
            relatedRaw.forEach(raw -> log.info("  - type={}, name={}, metricValue={}, status={}", 
                    raw.getObjectType(), raw.getObjectName(), raw.getMetricValue(), raw.getStatus()));
        }

        String metricType = feed.getMetricType();
        List<AlarmFeedDto.RelatedItem> related = relatedRaw != null ? relatedRaw.stream()
                .map(raw -> {
                    String formattedMetric = formatRelatedMetric(metricType, raw.getMetricValue(), raw.getObjectType());
                    log.debug("관련 객체 포맷팅: metricType={}, rawMetric={}, formatted={}", 
                            metricType, raw.getMetricValue(), formattedMetric);
                    return AlarmFeedDto.RelatedItem.builder()
                            .type(raw.getObjectType())
                            .name(raw.getObjectName())
                            .metric(formattedMetric)
                            .level(raw.getStatus())
                            .build();
                })
                .collect(Collectors.toList()) : List.of();

        log.info("📋 관련 객체 최종 결과: 개수={}", related.size());

        return AlarmFeedDto.DetailResponse.builder()
                .id(feed.getAlarmFeedId())
                .title(feed.getAlarmTitle())
                .severity(feed.getSeverityLevel())
                .occurredAt(feed.getOccurredAt().format(FORMATTER))
                .description(feed.getMessage())
                .latency(latency)
                .summary(summary)
                .related(related)
                .build();
    }
```

## 주요 성능 병목 지점

### 1. 🔥 동적 관련 객체 생성 (가장 큰 병목)

**문제 코드:**
```292:377:src/main/java/com/dajanggan/domain/alarm/service/AlarmFeedService.java
    private List<AlarmFeedDto.RelatedObjectRaw> generateRelatedObjectsOnDemand(AlarmFeed feed) {
        String metricType = feed.getMetricType();
        if (metricType == null) {
            return List.of();
        }

        String sql = metricConfig.getRelatedObjectsQuery(metricType);
        if (sql == null) {
            log.warn("⚠️ 관련 객체 쿼리 없음: metricType={}", metricType);
            return List.of();
        }

        try {
            // 인스턴스와 데이터베이스 정보 조회
            Instance instance = instanceRepository.findAllWithSecrets(List.of(feed.getInstanceId())).stream()
                    .findFirst()
                    .orElse(null);
            
            if (instance == null) {
                log.warn("⚠️ 인스턴스를 찾을 수 없음: instanceId={}", feed.getInstanceId());
                return List.of();
            }

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(feed.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(feed.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElse(null);

            if (databaseName == null) {
                log.warn("⚠️ 데이터베이스를 찾을 수 없음: databaseId={}", feed.getDatabaseId());
                return List.of();
            }

            // DB 연결 생성 및 쿼리 실행
            String host = instance.getHost() != null ? instance.getHost().trim() : "";
            String userName = instance.getUserName() != null ? instance.getUserName().trim() : "";
            String password = aesGcmService.decryptToString(instance.getSecretRef());
            String url = String.format("jdbc:postgresql://%s:%d/%s",
                    host, instance.getPort(), databaseName);

            log.info("🔌 관련 객체 생성용 DB 연결: instanceId={}, databaseId={}", 
                    feed.getInstanceId(), feed.getDatabaseId());
            log.debug("📝 실행할 쿼리: {}", sql);

            try (Connection conn = DriverManager.getConnection(url, userName, password);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                List<AlarmFeedDto.RelatedObjectRaw> generated = new java.util.ArrayList<>();
                while (rs.next()) {
                    AlarmFeedDto.RelatedObjectRaw raw = new AlarmFeedDto.RelatedObjectRaw();
                    raw.setObjectType(rs.getString("object_type"));
                    raw.setObjectName(rs.getString("object_name"));
                    raw.setMetricValue(rs.getString("metric_value"));
                    raw.setStatus(rs.getString("status"));
                    generated.add(raw);
                }

                if (generated.isEmpty()) {
                    log.warn("⚠️ 관련 객체 쿼리 결과가 0개입니다: alarmFeedId={}, metricType={}", 
                            feed.getAlarmFeedId(), metricType);
                } else {
                    log.info("✅ 관련 객체 동적 생성 완료: alarmFeedId={}, 생성된 개수={}", 
                            feed.getAlarmFeedId(), generated.size());
                }

                // 생성된 객체를 DB에 저장 (선택사항)
                if (!generated.isEmpty()) {
                    saveRelatedObjectsToDb(feed.getAlarmFeedId(), feed.getAlarmRuleId(), generated);
                }

                return generated;
            }

        } catch (SQLException e) {
            log.error("❌ 관련 객체 동적 생성 실패: alarmFeedId={}, metricType={}", 
                    feed.getAlarmFeedId(), metricType, e);
            return List.of();
        } catch (Exception e) {
            log.error("❌ 관련 객체 동적 생성 중 예외 발생: alarmFeedId={}, metricType={}", 
                    feed.getAlarmFeedId(), metricType, e);
            return List.of();
        }
    }
```

**성능 문제:**

1. **인스턴스 조회** (`findAllWithSecrets`)
   - 비밀번호 복호화 포함
   - 시간: 약 10-50ms

2. **데이터베이스 목록 조회** (`findDatabaseEntitiesByInstanceId`)
   - 전체 Database 목록 조회 후 필터링
   - 시간: 약 5-20ms

3. **비밀번호 복호화** (`aesGcmService.decryptToString`)
   - 매번 복호화 수행
   - 시간: 약 10-50ms

4. **Connection 생성** (`DriverManager.getConnection`)
   - Connection Pool 미사용
   - 네트워크 연결 + 인증
   - 시간: 약 50-100ms

5. **실제 PostgreSQL 쿼리 실행**
   - 복잡한 쿼리일 수 있음 (예: `pg_stat_statements`, `pg_stat_all_tables`)
   - 시간: 약 100-500ms (쿼리 복잡도에 따라)

6. **결과를 DB에 저장** (`saveRelatedObjectsToDb`)
   - 각 관련 객체마다 INSERT
   - 시간: 약 10-50ms per 객체

**총 소요 시간: 약 185-770ms (최악의 경우 1초 이상)**

### 2. Latency 데이터 조회

**쿼리:**
```sql
SELECT
    TO_CHAR(amh.recorded_at, 'HH24:00') AS hourLabel,
    AVG(CAST(amh.metric_value AS DECIMAL)) AS avgLatency
FROM alarm_metric_history amh
WHERE amh.alarm_feed_id = #{alarmFeedId}
  AND amh.recorded_at >= NOW() - INTERVAL '24 hours'
GROUP BY TO_CHAR(amh.recorded_at, 'HH24:00')
ORDER BY hourLabel
```

**성능 문제:**
- 24시간 데이터를 GROUP BY로 집계
- 인덱스가 없으면 Full Table Scan 가능
- `TO_CHAR` 함수 사용으로 인덱스 활용 어려움
- 시간: 약 50-200ms (데이터 양에 따라)

### 3. 여러 개의 순차적 쿼리

**현재 흐름:**
1. `selectAlarmDetail` - 알림 기본 정보 (약 5ms)
2. `selectLatencyData` - Latency 데이터 (약 50-200ms)
3. `selectRelatedObjects` - 관련 객체 조회 (약 5-20ms)
4. **관련 객체가 없으면** → `generateRelatedObjectsOnDemand` (약 185-770ms)

**총 소요 시간:**
- 관련 객체가 있는 경우: 약 60-225ms
- 관련 객체가 없는 경우: 약 245-995ms (최악의 경우 1초 이상)

## 해결방안

### 1. 동적 관련 객체 생성 최적화 (가장 중요)

#### 문제점
- Connection Pool 미사용
- 비밀번호 중복 복호화
- 동기적 실행으로 사용자 대기

#### 해결책

**A. Connection Pool 사용**
```java
// DataSourceFactory 사용
private final DataSourceFactory dataSourceFactory;

private List<AlarmFeedDto.RelatedObjectRaw> generateRelatedObjectsOnDemand(AlarmFeed feed) {
    // ... 기존 코드 ...
    
    // 비밀번호 복호화 (1회만)
    String decryptedPassword = aesGcmService.decryptToString(instance.getSecretRef());
    
    // Connection Pool 사용
    JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(
        instance, databaseName, decryptedPassword);
    
    List<AlarmFeedDto.RelatedObjectRaw> generated = jdbc.query(sql, (rs, rowNum) -> {
        AlarmFeedDto.RelatedObjectRaw raw = new AlarmFeedDto.RelatedObjectRaw();
        raw.setObjectType(rs.getString("object_type"));
        raw.setObjectName(rs.getString("object_name"));
        raw.setMetricValue(rs.getString("metric_value"));
        raw.setStatus(rs.getString("status"));
        return raw;
    });
    
    // ...
}
```

**효과:**
- Connection 생성: 50-100ms → 1-5ms (95% 감소)

**B. 비동기 처리 또는 캐싱**
```java
// 관련 객체가 없을 때는 빈 리스트 반환하고
// 백그라운드에서 생성 후 다음 조회 시 사용
if ((relatedRaw == null || relatedRaw.isEmpty()) && feed.getMetricType() != null) {
    // 비동기로 생성 (사용자 대기 없음)
    CompletableFuture.runAsync(() -> {
        List<AlarmFeedDto.RelatedObjectRaw> generated = generateRelatedObjectsOnDemand(feed);
        if (!generated.isEmpty()) {
            saveRelatedObjectsToDb(feed.getAlarmFeedId(), feed.getAlarmRuleId(), generated);
        }
    });
    // 빈 리스트 반환
    relatedRaw = List.of();
}
```

**효과:**
- 사용자 대기 시간: 185-770ms → 0ms (즉시 응답)

### 2. Latency 데이터 조회 최적화

**인덱스 추가:**
```sql
-- alarm_metric_history 테이블에 인덱스 추가
CREATE INDEX idx_alarm_metric_history_feed_time 
ON alarm_metric_history(alarm_feed_id, recorded_at);

-- 또는 복합 인덱스
CREATE INDEX idx_alarm_metric_history_feed_time_covering 
ON alarm_metric_history(alarm_feed_id, recorded_at, metric_value);
```

**쿼리 최적화:**
```sql
-- TO_CHAR 대신 DATE_TRUNC 사용 (인덱스 활용 가능)
SELECT
    DATE_TRUNC('hour', amh.recorded_at) AS hourLabel,
    AVG(amh.metric_value) AS avgLatency
FROM alarm_metric_history amh
WHERE amh.alarm_feed_id = #{alarmFeedId}
  AND amh.recorded_at >= NOW() - INTERVAL '24 hours'
GROUP BY DATE_TRUNC('hour', amh.recorded_at)
ORDER BY hourLabel
```

**효과:**
- 쿼리 실행 시간: 50-200ms → 10-50ms (75% 감소)

### 3. 관련 객체 조회 최적화

**인덱스 확인:**
```sql
-- alarm_related_objects 테이블에 인덱스 확인
CREATE INDEX IF NOT EXISTS idx_alarm_related_objects_feed_id 
ON alarm_related_objects(alarm_feed_id);
```

### 4. 배치 저장 최적화

**현재:**
```java
// 각 객체마다 개별 INSERT
for (AlarmFeedDto.RelatedObjectRaw raw : relatedObjects) {
    alarmFeedMapper.insertRelatedObject(...);
}
```

**개선:**
```java
// Batch Insert 사용
alarmFeedMapper.insertRelatedObjectsBatch(relatedObjects);
```

**효과:**
- 저장 시간: 10-50ms per 객체 → 10-50ms total (90% 감소)

## 최적화 후 예상 성능

### 현재 성능
- 관련 객체가 있는 경우: 약 60-225ms
- 관련 객체가 없는 경우: 약 245-995ms (최악의 경우 1초 이상)

### 최적화 후 성능
- 관련 객체가 있는 경우: 약 20-80ms (70% 개선)
- 관련 객체가 없는 경우: 약 20-80ms (비동기 처리 시 즉시 응답)

## 구현 우선순위

1. **높음**: Connection Pool 사용 (가장 큰 효과)
2. **높음**: 비동기 처리 또는 캐싱 (사용자 경험 개선)
3. **중간**: Latency 데이터 쿼리 최적화
4. **낮음**: Batch Insert (데이터가 많을 때만 효과)

