# 문제해결 2

## 실제 개발 과정에서 부딪힌 문제들, 어떻게 해결했을까요?

---

## 🏷️ 해시태그

`#API_성능` `#비동기_처리` `#사용자_경험`

---

## 💬 문제 상황

> "알림 상세 조회를 클릭하면 1초 이상 걸려요. 관련 객체를 생성하는 동안 계속 기다려야 해요."

---

## Pain Points

- **동기 처리로 인한 사용자 대기**
  - 관련 객체가 없을 때 실제 DB에 연결하여 쿼리 실행
  - 인스턴스 조회, 비밀번호 복호화, Connection 생성, 쿼리 실행 등 모든 작업을 사용자가 기다려야 함
  - 소요 시간: 약 185-770ms

- **불필요한 작업까지 동기 처리**
  - 관련 객체는 다음 조회 시에 보여도 되는 보조 데이터
  - 사용자는 필수 정보만 보면 되는데 모든 작업 완료를 기다림
  - 사용자 경험 저하

---

## Needs

- **즉시 응답, 백그라운드 처리**
  - 사용자는 빈 리스트로 즉시 응답받고
  - 관련 객체는 백그라운드에서 생성하여 다음 조회 시 사용

- **필수 vs 보조 데이터 구분**
  - 필수 데이터: 동기 처리 (즉시 필요)
  - 보조 데이터: 비동기 처리 (다음 조회 시 사용 가능)

---

## 해결방안

### 비동기 처리 적용

**이전 (동기 처리):**
```java
// 사용자가 모든 작업 완료까지 대기 (185-770ms)
if (relatedRaw == null || relatedRaw.isEmpty()) {
    relatedRaw = generateRelatedObjectsOnDemand(feed); 
}
```

**개선 (비동기 처리):**
```java
// 즉시 응답, 백그라운드에서 생성
if (relatedRaw == null || relatedRaw.isEmpty()) {
    CompletableFuture.runAsync(() -> {
        List<AlarmFeedDto.RelatedObjectRaw> generated = 
            generateRelatedObjectsOnDemand(feed);
        if (!generated.isEmpty()) {
            saveRelatedObjectsToDb(feed.getAlarmFeedId(), 
                feed.getAlarmRuleId(), generated);
        }
    }, asyncExecutor);
    
    // 빈 리스트 반환 (즉시 응답)
    relatedRaw = List.of();
}
```

---

## 성능 개선 결과

### 이전
- 관련 객체 생성 시: **185-770ms 대기**
- 총 응답 시간: **1초 이상**

### 개선 후
- 관련 객체 생성: **0ms (백그라운드 처리)**
- 총 응답 시간: **20-80ms**

### 효과
- **사용자 대기 시간: 100% 제거** (185-770ms → 0ms)
- **API 응답 시간: 95% 감소**
- **사용자 경험: 즉시 응답 → 대기 시간 없음**

---

## 교훈

1. **사용자 경험 우선**
   - 필수적이지 않은 작업은 비동기 처리
   - 사용자가 기다릴 필요 없는 작업은 백그라운드에서 처리

2. **동기 vs 비동기 판단**
   - 필수 데이터: 동기 처리 (즉시 필요)
   - 보조 데이터: 비동기 처리 (다음 조회 시 사용 가능)

3. **실제 효과**
   - 사용자 대기 시간 100% 제거
   - API 응답 시간 95% 감소


