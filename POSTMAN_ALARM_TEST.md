# 알람 시스템 Postman 테스트 가이드

## 기본 설정
- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`

---

## 1. 알람 규칙 관리

### 1.1 알람 규칙 목록 조회
```
GET /api/alarms/rules?instanceId=1&databaseId=1&metricType=dead_tuples&enabled=true
```

**Query Parameters:**
- `instanceId` (optional): 인스턴스 ID
- `databaseId` (optional): 데이터베이스 ID
- `metricType` (optional): 지표 타입 (dead_tuples, bloat_percent, 등)
- `enabled` (optional): 활성화 여부 (true/false)

---

### 1.2 알람 규칙 상세 조회
```
GET /api/alarms/rules/{alarmRuleId}
```

**예시:**
```
GET /api/alarms/rules/1
```

---

### 1.3 알람 규칙 생성 (집계 타입 + 윈도우 포함)
```
POST /api/alarms/rules
Content-Type: application/json
```

**Request Body (집계 타입: avg_5m, 윈도우: 15분):**
```json
{
  "enabled": true,
  "instanceId": 1,
  "databaseId": 1,
  "metricType": "dead_tuples",
  "aggregationType": "avg_5m",
  "levels": {
    "notice": {
      "threshold": 100000,
      "minDurationMin": 1,
      "occurCount": 2,
      "windowMin": 15
    },
    "warning": {
      "threshold": 500000,
      "minDurationMin": 5,
      "occurCount": 2,
      "windowMin": 15
    },
    "critical": {
      "threshold": 1000000,
      "minDurationMin": 10,
      "occurCount": 1,
      "windowMin": 10
    }
  }
}
```

**Request Body (집계 타입: latest_avg, 윈도우: 10분):**
```json
{
  "enabled": true,
  "instanceId": 1,
  "databaseId": 1,
  "metricType": "bloat_percent",
  "aggregationType": "latest_avg",
  "levels": {
    "notice": {
      "threshold": 10.0,
      "minDurationMin": 1,
      "occurCount": 3,
      "windowMin": 10
    },
    "warning": {
      "threshold": 20.0,
      "minDurationMin": 3,
      "occurCount": 2,
      "windowMin": 10
    },
    "critical": {
      "threshold": 30.0,
      "minDurationMin": 5,
      "occurCount": 1,
      "windowMin": 5
    }
  }
}
```

**Request Body (집계 타입: avg_15m, 윈도우: 20분):**
```json
{
  "enabled": true,
  "instanceId": 1,
  "databaseId": 1,
  "metricType": "total_table_bloat",
  "aggregationType": "avg_15m",
  "levels": {
    "notice": {
      "threshold": 1073741824,
      "minDurationMin": 2,
      "occurCount": 2,
      "windowMin": 20
    },
    "warning": {
      "threshold": 5368709120,
      "minDurationMin": 5,
      "occurCount": 2,
      "windowMin": 20
    },
    "critical": {
      "threshold": 10737418240,
      "minDurationMin": 10,
      "occurCount": 1,
      "windowMin": 15
    }
  }
}
```

**Request Body (집계 타입: p95_15m, 윈도우: 15분):**
```json
{
  "enabled": true,
  "instanceId": 1,
  "databaseId": 1,
  "metricType": "autovacuum_worker_utilization",
  "aggregationType": "p95_15m",
  "levels": {
    "notice": {
      "threshold": 50.0,
      "minDurationMin": 1,
      "occurCount": 2,
      "windowMin": 15
    },
    "warning": {
      "threshold": 70.0,
      "minDurationMin": 3,
      "occurCount": 2,
      "windowMin": 15
    },
    "critical": {
      "threshold": 90.0,
      "minDurationMin": 5,
      "occurCount": 1,
      "windowMin": 10
    }
  }
}
```

**Response:**
```json
{
  "alarmRuleId": 1,
  "message": "알림 규칙이 생성되었습니다."
}
```

---

### 1.4 알람 규칙 수정
```
PUT /api/alarms/rules/{alarmRuleId}
Content-Type: application/json
```

**Request Body:**
```json
{
  "enabled": true,
  "aggregationType": "avg_5m",
  "levels": {
    "notice": {
      "threshold": 100000,
      "minDurationMin": 1,
      "occurCount": 2,
      "windowMin": 15
    },
    "warning": {
      "threshold": 500000,
      "minDurationMin": 5,
      "occurCount": 2,
      "windowMin": 15
    },
    "critical": {
      "threshold": 1000000,
      "minDurationMin": 10,
      "occurCount": 1,
      "windowMin": 10
    }
  }
}
```

---

### 1.5 알람 규칙 삭제
```
DELETE /api/alarms/rules/{alarmRuleId}
```

---

### 1.6 알람 규칙 활성화/비활성화
```
PATCH /api/alarms/rules/{alarmRuleId}/enabled?enabled=true
```

---

## 2. 알람 피드 조회

### 2.1 알람 피드 목록 조회
```
GET /api/alarms/feeds?instanceId=1&databaseId=1&severityLevel=CRITICAL&isRead=false
```

**Query Parameters:**
- `instanceId` (optional): 인스턴스 ID
- `databaseId` (optional): 데이터베이스 ID
- `severityLevel` (optional): 심각도 레벨 (NOTICE, WARNING, CRITICAL)
- `isRead` (optional): 읽음 여부 (true/false)

---

### 2.2 알람 피드 상세 조회
```
GET /api/alarms/feeds/{alarmFeedId}
```

---

### 2.3 알람 피드 읽음 처리
```
PATCH /api/alarms/feeds/{alarmFeedId}/read
```

---

## 3. 알람 트래킹 조회

### 3.1 트래킹 상태 목록 조회
```
GET /api/alarms/tracking?instanceId=1&status=FIRED
```

**Query Parameters:**
- `instanceId` (optional): 인스턴스 ID
- `status` (optional): 상태 (PENDING_FIRED, FIRED, RESOLVED)

---

## 4. 테스트용 API (수동 알람 체크)

### 4.1 특정 지표 알람 체크 (즉시 실행)
```
GET /api/test/alarm/check?instanceId=1&databaseId=1&metricType=dead_tuples
```

**Query Parameters:**
- `instanceId` (required): 인스턴스 ID
- `databaseId` (required): 데이터베이스 ID
- `metricType` (required): 지표 타입

**지표 타입 예시:**
- `dead_tuples`
- `bloat_percent`
- `total_table_bloat`
- `autovacuum_worker_utilization`
- `blockers_per_hour`
- `transaction_age`
- `block_duration`
- `wraparound_progress`
- `long_running_queries`
- `lock_waits`
- `long_idle_sessions`
- `blocking_sessions`
- `slow_query_spike`
- `avg_execution_spike`
- `qps_spike`

**Response:**
```json
{
  "success": true,
  "message": "알람 체크 완료",
  "instanceName": "postgres-instance",
  "databaseName": "postgres",
  "metricType": "dead_tuples"
}
```

---

### 4.2 모든 지표 알람 체크
```
GET /api/test/alarm/check-all?instanceId=1&databaseId=1
```

---

### 4.3 트래킹 상태 조회
```
GET /api/test/alarm/tracking?instanceId=1
```

**Response:**
```json
{
  "success": true,
  "trackings": [
    {
      "alarmRuleId": 1,
      "metricType": "dead_tuples",
      "currentLevel": "WARNING",
      "currentValue": 600000,
      "consecutiveCount": 3,
      "status": "PENDING_FIRED",
      "firstTriggeredAt": "2025-01-18T10:00:00+09:00",
      "lastCheckedAt": "2025-01-18T10:03:00+09:00"
    }
  ],
  "count": 1
}
```

---

### 4.4 알람 피드 조회 (미해결)
```
GET /api/test/alarm/feeds?instanceId=1&databaseId=1&severityLevel=CRITICAL
```

**Response:**
```json
{
  "success": true,
  "feeds": [
    {
      "alarmFeedId": 1,
      "alarmRuleId": 1,
      "severityLevel": "CRITICAL",
      "metricValue": 1200000,
      "isResolved": false,
      "firedAt": "2025-01-18T10:05:00+09:00"
    }
  ],
  "count": 1
}
```

---

### 4.5 트래킹 초기화
```
DELETE /api/test/alarm/tracking/{ruleId}
```

**예시:**
```
DELETE /api/test/alarm/tracking/1
```

---

### 4.6 테스트 규칙 생성
```
POST /api/test/alarm/create-test-rule?instanceId=1&databaseId=1&metricType=dead_tuples
```

---

### 4.7 DB 연결 테스트
```
GET /api/test/alarm/test-connection?instanceId=1&databaseId=1
```

---

## 5. 집계 타입별 테스트 시나리오

### 시나리오 1: latest_avg (실시간 값)
1. 규칙 생성 (집계 타입: `latest_avg`)
2. 알람 체크 실행: `GET /api/test/alarm/check?instanceId=1&databaseId=1&metricType=dead_tuples`
3. 트래킹 확인: `GET /api/test/alarm/tracking?instanceId=1`
4. 알람 피드 확인: `GET /api/test/alarm/feeds?instanceId=1`

### 시나리오 2: avg_5m (5분 평균)
1. 규칙 생성 (집계 타입: `avg_5m`)
2. 5분 집계 데이터가 쌓일 때까지 대기 (또는 수동으로 집계 실행)
3. 알람 체크 실행
4. 트래킹 및 피드 확인

### 시나리오 3: avg_15m (15분 평균)
1. 규칙 생성 (집계 타입: `avg_15m`)
2. 15분간 데이터 수집 대기
3. 알람 체크 실행
4. 트래킹 및 피드 확인

### 시나리오 4: p95_15m (15분 95퍼센타일)
1. 규칙 생성 (집계 타입: `p95_15m`)
2. 15분간 데이터 수집 대기
3. 알람 체크 실행
4. 트래킹 및 피드 확인

---

## 6. 윈도우 기능 테스트 시나리오

### 시나리오: 윈도우 기반 발생 횟수 체크
1. 규칙 생성 (윈도우: 15분, 발생 횟수: 2회)
   ```json
   {
     "windowMin": 15,
     "occurCount": 2,
     "minDurationMin": 1
   }
   ```

2. 알람 체크를 여러 번 실행 (최근 15분 내에 2회 이상 발생해야 알람 발생)
   ```
   GET /api/test/alarm/check?instanceId=1&databaseId=1&metricType=dead_tuples
   ```

3. 트래킹 확인하여 `consecutiveCount` 증가 확인
   ```
   GET /api/test/alarm/tracking?instanceId=1
   ```

4. 윈도우가 지나가면 (15분 후) 카운트가 리셋되는지 확인

---

## 7. Postman Collection 예시

### Collection 구조
```
📁 알람 시스템 테스트
  📁 알람 규칙 관리
    📄 규칙 목록 조회
    📄 규칙 상세 조회
    📄 규칙 생성 (latest_avg)
    📄 규칙 생성 (avg_5m)
    📄 규칙 생성 (avg_15m)
    📄 규칙 생성 (p95_15m)
    📄 규칙 수정
    📄 규칙 삭제
    📄 규칙 활성화/비활성화
  📁 알람 피드
    📄 피드 목록 조회
    📄 피드 상세 조회
    📄 피드 읽음 처리
  📁 알람 트래킹
    📄 트래킹 상태 조회
  📁 테스트 API
    📄 특정 지표 체크
    📄 전체 지표 체크
    📄 트래킹 초기화
    📄 테스트 규칙 생성
    📄 DB 연결 테스트
```

---

## 8. 주의사항

1. **집계 타입별 지원 지표:**
   - `latest_avg`: 모든 지표 지원
   - `avg_5m`, `avg_15m`, `p95_15m`: 집계 테이블에 있는 지표만 지원
     - `dead_tuples` → `total_dead_tuples`
     - `bloat_percent` → `avg_bloat_ratio`
     - `total_table_bloat` → `total_bloat_bytes`
     - `autovacuum_worker_utilization` → `worker_utilization_pct`

2. **윈도우 기능:**
   - `windowMin`: 최근 X분 내에 발생 횟수를 체크
   - 윈도우가 지나가면 자동으로 카운트 리셋
   - `occurCount`: 윈도우 내에 발생해야 하는 최소 횟수
   - `minDurationMin`: 최소 지속 시간 (분)

3. **테스트 순서:**
   - 먼저 DB 연결 테스트로 연결 확인
   - 규칙 생성
   - 알람 체크 실행
   - 트래킹 및 피드 확인

