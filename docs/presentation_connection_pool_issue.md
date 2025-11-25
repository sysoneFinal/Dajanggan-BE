# Connection Pool 미사용 문제 상세 분석

## 🔥 문제상황: AlarmMetricsCollector에서 Connection Pool 미사용

### 현재 코드 (문제 있는 코드)

```143:156:src/main/java/com/dajanggan/domain/alarm/service/AlarmMetricsCollector.java
    private Connection createConnection(Instance instance, String databaseName) throws SQLException {
        // ✅ trim()으로 공백 제거
        String host = instance.getHost() != null ? instance.getHost().trim() : "";
        String userName = instance.getUserName() != null ? instance.getUserName().trim() : "";
        String password = aesGcmService.decryptToString(instance.getSecretRef());
        String dbName = databaseName != null ? databaseName.trim() : "";

        String url = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                dbName);

        return DriverManager.getConnection(url, userName, password);
    }
```

### 문제점 분석

#### 1. 매번 새로운 Connection 생성

**현재 동작:**
```java
// AlarmMetricsCollector.collectMetrics()
for (Database db : databases) {  // 예: 10개 Database
    Instance instance = instanceMap.get(db.getInstanceId());
    
    try (Connection conn = createConnection(instance, db.getDatabaseName())) {
        // 알람 체크 (약 10개의 메트릭 체크)
        checkMetricSafely(conn, instance, db, "autovacuum_worker_utilization");
        checkMetricSafely(conn, instance, db, "transaction_age");
        // ... 8개 더
    }  // Connection 자동 종료 (close)
}
```

**문제:**
- 각 Database마다 **새로운 Connection 생성** (10개 Database = 10번 생성)
- 알람 체크 완료 후 Connection **즉시 종료**
- 다음 알람 체크 주기(1분 후)에 다시 **새로 생성**

#### 2. Connection 생성 비용

**Connection 생성 과정:**
1. **네트워크 연결 설정** (TCP 3-way handshake)
   - 클라이언트 → PostgreSQL 서버 연결
   - 시간: 약 10-50ms (네트워크 지연에 따라 다름)

2. **PostgreSQL 인증 과정**
   - 사용자 인증 정보 전송
   - 인증 처리
   - 시간: 약 5-20ms

3. **세션 초기화**
   - 데이터베이스 선택
   - 세션 변수 설정
   - 시간: 약 5-10ms

**총 비용: 약 20-80ms per Connection**

#### 3. 실제 성능 영향

**시나리오: Instance 1개, Database 10개**

```
현재 방식 (DriverManager.getConnection):
- 1분마다 알람 체크 실행
- 각 Database마다 Connection 생성: 10회 × 50ms = 500ms
- Connection 종료: 10회 × 5ms = 50ms
- 총 Connection 오버헤드: 550ms

1시간 기준:
- 알람 체크: 60회
- 총 Connection 생성 시간: 550ms × 60 = 33초
- 실제 알람 체크 시간: 약 2초 × 60 = 120초
- Connection 오버헤드 비율: 33초 / 153초 = 21.6%
```

### Connection Pool 사용 시 (해결책)

#### DataSourceFactory의 구현

```98:143:src/main/java/com/dajanggan/infrastructure/datasource/DataSourceFactory.java
    private DataSource createDataSource(Instance instance, String databaseName, String decryptedPassword) {
        HikariConfig config = new HikariConfig();

        // JDBC URL 구성
        String host = instance.getHost();
        if (host != null) {
            host = host.trim(); // 공백 제거
        }
        log.warn("> [DEBUG] Host value: '{}', length: {}", host, host != null ? host.length() : 0);

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                databaseName);
        config.setJdbcUrl(jdbcUrl);
        log.info(">> Creating connection to: {}", jdbcUrl);

        // 인증 정보
        config.setUsername(instance.getUserName());
        config.setPassword(decryptedPassword);
        log.debug(">>>> Password set for instance: {} (length: {})",
                instance.getInstanceName(), decryptedPassword != null ? decryptedPassword.length() : 0);

        // SSL 설정
        if (instance.getSslmode() != null) {
            config.addDataSourceProperty("sslmode", instance.getSslmode());
            log.info(">>>>>> SSL Mode: {}", instance.getSslmode());
        } else {
            log.warn("******** No SSL mode configured for instance: {}", instance.getInstanceName());
        }

        // Connection Pool 설정
        config.setMaximumPoolSize(5);  // 메트릭 수집용이므로 작은 풀 크기
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);  // 10초
        config.setIdleTimeout(300000);  // 5분
        config.setMaxLifetime(600000);  // 10분

        // Pool 이름
        config.setPoolName("MetricsCollector-" + instance.getInstanceName() + "-" + databaseName);

        log.info(">>>>>>>>DataSource created for instance: {} ({}:{}/{})",
                instance.getInstanceName(), instance.getHost(), instance.getPort(), databaseName);

        return new HikariDataSource(config);
    }
```

#### Connection Pool의 동작 방식

**HikariCP Connection Pool 설정:**
- `MaximumPoolSize: 5` - 최대 5개의 Connection 유지
- `MinimumIdle: 1` - 최소 1개의 Connection을 항상 유지
- `ConnectionTimeout: 10초` - Connection 획득 대기 시간
- `IdleTimeout: 5분` - 사용하지 않는 Connection 유지 시간
- `MaxLifetime: 10분` - Connection 최대 수명

**Connection Pool 동작:**
```
1. 첫 번째 요청:
   - Pool에 Connection이 없음
   - 새 Connection 생성 (50ms)
   - Pool에 저장
   - 요청자에게 반환

2. 두 번째 요청 (같은 DataSource):
   - Pool에 Connection이 있음
   - 기존 Connection 재사용 (1ms 미만)
   - 요청자에게 반환

3. Connection 반환:
   - 사용 완료 후 close() 호출
   - 실제로는 종료하지 않고 Pool로 반환
   - 다음 요청에서 재사용 가능
```

#### 성능 개선 효과

**시나리오: Instance 1개, Database 10개**

```
Connection Pool 사용 시:
- 첫 번째 알람 체크:
  - Connection 생성: 1회 × 50ms = 50ms (Pool 초기화)
  - 나머지 9개: Pool에서 재사용 = 9 × 1ms = 9ms
  - 총: 59ms

- 두 번째 알람 체크 (1분 후):
  - 모든 Connection: Pool에서 재사용 = 10 × 1ms = 10ms
  - (IdleTimeout 5분이므로 Connection 유지됨)

1시간 기준:
- 첫 번째: 59ms
- 나머지 59회: 10ms × 59 = 590ms
- 총 Connection 오버헤드: 649ms
- 실제 알람 체크 시간: 약 2초 × 60 = 120초
- Connection 오버헤드 비율: 0.649초 / 120.649초 = 0.5%

개선 효과:
- Connection 생성 시간: 33초 → 0.649초 (98% 감소)
- 오버헤드 비율: 21.6% → 0.5% (98% 감소)
```

### 추가 문제: 비밀번호 중복 복호화

**현재 코드:**
```java
private Connection createConnection(Instance instance, String databaseName) throws SQLException {
    String password = aesGcmService.decryptToString(instance.getSecretRef()); // 매번 복호화!
    // ...
}
```

**문제:**
- 같은 Instance의 비밀번호를 각 Database마다 복호화
- Instance 1개, Database 10개면 **비밀번호를 10번 복호화**
- 복호화 비용: 약 10-50ms per 복호화

**해결:**
```java
// 비밀번호 복호화 캐싱
Map<Long, String> decryptedPasswordMap = new ConcurrentHashMap<>();
for (Instance instance : instanceMap.values()) {
    String decrypted = aesGcmService.decryptToString(instance.getSecretRef());
    decryptedPasswordMap.put(instance.getInstanceId(), decrypted);
}

// Connection 생성 시 재사용
String password = decryptedPasswordMap.get(instance.getInstanceId());
```

**효과:**
- 비밀번호 복호화: 10회 → 1회 (90% 감소)
- 복호화 시간: 500ms → 50ms (90% 감소)

### 최적화된 코드

```java
// AlarmMetricsCollector.java
private final DataSourceFactory dataSourceFactory;

@Scheduled(fixedDelay = 60000)
public void collectMetrics() {
    // ... 기존 코드 ...
    
    // 비밀번호 복호화 캐싱
    Map<Long, String> decryptedPasswordMap = new ConcurrentHashMap<>();
    for (Instance instance : instanceMap.values()) {
        try {
            String decrypted = aesGcmService.decryptToString(instance.getSecretRef());
            decryptedPasswordMap.put(instance.getInstanceId(), decrypted);
        } catch (Exception e) {
            log.error("비밀번호 복호화 실패: instanceId={}", instance.getInstanceId(), e);
        }
    }
    
    // Connection Pool 사용
    for (Database db : databases) {
        Instance instance = instanceMap.get(db.getInstanceId());
        String decryptedPassword = decryptedPasswordMap.get(instance.getInstanceId());
        
        if (decryptedPassword == null) {
            log.error("복호화된 비밀번호가 없음: instanceId={}", instance.getInstanceId());
            continue;
        }
        
        // DataSourceFactory를 통한 Connection Pool 사용
        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(
            instance, db.getDatabaseName(), decryptedPassword);
        
        try (Connection conn = jdbc.getDataSource().getConnection()) {
            // 알람 체크...
            checkMetricSafely(conn, instance, db, "autovacuum_worker_utilization");
            // ...
        }
    }
}
```

### Connection Pool의 추가 이점

#### 1. Connection 재사용
- **현재**: 매번 새로 생성 → 종료
- **Pool**: 생성 → 재사용 → 반환 → 재사용

#### 2. Connection 상태 관리
- **현재**: Connection 상태 확인 없이 사용
- **Pool**: Connection 유효성 검사 (isValid())
  - 끊어진 Connection 자동 감지 및 재생성
  - 안정성 향상

#### 3. 리소스 관리
- **현재**: Connection 수 제한 없음 (메모리 누수 위험)
- **Pool**: 최대 Connection 수 제한 (MaximumPoolSize)
  - 메모리 사용량 예측 가능
  - 서버 부하 제어

#### 4. 타임아웃 관리
- **현재**: 타임아웃 없음 (무한 대기 가능)
- **Pool**: ConnectionTimeout 설정
  - Connection 획득 실패 시 빠른 실패
  - 장애 전파 방지

### 실제 성능 측정 (예상)

**테스트 시나리오:**
- Instance: 3개
- Database: 각 Instance당 10개 (총 30개)
- 알람 체크 주기: 1분
- 측정 시간: 1시간

**현재 방식 (DriverManager):**
```
Connection 생성: 30개 × 50ms = 1,500ms
비밀번호 복호화: 30회 × 30ms = 900ms
총 오버헤드: 2,400ms per 알람 체크

1시간 총 오버헤드: 2,400ms × 60 = 144초 (2.4분)
```

**Connection Pool 사용:**
```
첫 번째 알람 체크:
- Connection 생성: 30개 × 50ms = 1,500ms
- 비밀번호 복호화: 3회 × 30ms = 90ms (Instance당 1회)
- 총: 1,590ms

두 번째 이후:
- Connection 재사용: 30개 × 1ms = 30ms
- 비밀번호 복호화: 0ms (캐싱됨)
- 총: 30ms

1시간 총 오버헤드: 1,590ms + (30ms × 59) = 3,360ms (3.36초)

개선 효과: 144초 → 3.36초 (97.7% 감소)
```

### 발표 시 강조 포인트

1. **Connection 생성 비용의 중요성**
   - 단순해 보이지만 누적되면 큰 성능 저하
   - 1시간에 2.4분을 Connection 생성에 소비

2. **Connection Pool의 효과**
   - 첫 번째 이후부터는 거의 오버헤드 없음
   - 97.7% 성능 개선

3. **일관된 아키텍처**
   - `CommonMetricsCollector`에서는 이미 Connection Pool 사용
   - `AlarmMetricsCollector`도 동일한 패턴 적용 필요

4. **실제 운영 환경에서의 중요성**
   - Database가 많을수록 효과 증가
   - 장기 운영 시 누적 효과가 큼


