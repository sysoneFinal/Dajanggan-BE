# 팀원 vs 사용자 최적화 비교

## 🔍 문제 영역 비교

### 팀원이 해결한 문제: 데이터 수집 병렬화
- **영역**: 백엔드 스케줄러 (메트릭 수집)
- **문제**: 
  - 쿼리 실행량 증가 시 Raw 데이터 수집이 스케줄 주기를 초과
  - Spring 기본 스케줄러가 싱글 스레드라서 순차 대기
  - 집계 작업 지연/누락 발생
- **해결**: 병렬화 전략 (CommonMetricsCollector)
  - 여러 Database를 병렬로 수집
  - ExecutorService 사용 (10개 스레드)
  - 순차 진행 → 병렬 진행

### 사용자가 해결한 문제: AlarmFeed 상세 조회 성능
- **영역**: API 응답 (사용자 요청)
- **문제**:
  - 상세 조회 클릭 시 1초 이상 소요
  - 동적 관련 객체 생성 시 동기 처리 (185-770ms 대기)
- **해결**: 비동기 처리
  - 관련 객체 생성 비동기화
  - 사용자 즉시 응답, 백그라운드에서 처리

## 📊 상세 비교

| 구분 | 팀원 (데이터 수집) | 사용자 (API 응답) |
|------|-------------------|------------------|
| **문제 영역** | 백엔드 스케줄러 | 사용자 API 요청 |
| **대상** | 메트릭 수집 작업 | AlarmFeed 상세 조회 |
| **문제 상황** | 순차 수집으로 인한 지연 | 동기 처리로 인한 대기 |
| **해결 방법** | 병렬 수집 | 비동기 처리 |
| **기술** | ExecutorService + CompletableFuture | CompletableFuture (비동기 처리) |
| **효과** | 2분 → 30초 (75% 감소) | 1초 이상 → 20-80ms (95% 감소) |
| **코드 위치** | CommonMetricsCollector | AlarmFeedService |

## 🔧 구현 방식 비교

### 팀원: 병렬 수집
```java
// CommonMetricsCollector.java
private final ExecutorService executorService = Executors.newFixedThreadPool(10);

List<CompletableFuture<CollectionResult>> futures = databases.stream()
    .map(database -> CompletableFuture.supplyAsync(() -> {
        return collectForDatabase(database, instance, decryptedPassword, collectedAt);
    }, executorService))
    .toList();

// 모든 작업 완료 대기
List<CollectionResult> results = futures.stream()
    .map(CompletableFuture::join)
    .toList();
```

**특징:**
- 여러 Database를 동시에 수집
- 모든 작업 완료까지 대기 (join)
- 스케줄러 주기 내에 완료 보장

### 사용자: 비동기 처리
```java
// AlarmFeedService.java
private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(5);

// 비동기로 생성 (사용자는 빈 리스트로 즉시 응답받음)
CompletableFuture.runAsync(() -> {
    List<AlarmFeedDto.RelatedObjectRaw> generated = generateRelatedObjectsOnDemand(feed);
    if (!generated.isEmpty()) {
        saveRelatedObjectsToDb(feed.getAlarmFeedId(), feed.getAlarmRuleId(), generated);
    }
}, asyncExecutor);

// 빈 리스트 반환 (즉시 응답)
relatedRaw = List.of();
```

**특징:**
- 사용자 요청에 즉시 응답
- 백그라운드에서 작업 수행
- 결과는 다음 조회 시 사용 가능

## 🎯 핵심 차이점

### 1. 목적
- **팀원**: 스케줄러 주기 내 수집 완료 보장 (안정성)
- **사용자**: 사용자 대기 시간 제거 (응답성)

### 2. 처리 방식
- **팀원**: 병렬 처리 + 완료 대기 (모든 작업 완료 필요)
- **사용자**: 비동기 처리 + 즉시 응답 (결과 대기 불필요)

### 3. 성능 지표
- **팀원**: 전체 수집 시간 단축 (2분 → 30초)
- **사용자**: API 응답 시간 단축 (1초 → 20ms)

## 💡 발표 시 구분 포인트

### 팀원의 최적화
- **제목**: "데이터 수집 병렬화를 통한 수집 안정성 확보"
- **핵심**: 여러 Database를 동시에 수집하여 전체 시간 단축
- **효과**: 스케줄 주기 내 수집 완료 보장

### 사용자의 최적화
- **제목**: "비동기 처리를 통한 API 응답 성능 개선"
- **핵심**: 사용자 대기 없이 즉시 응답, 백그라운드에서 처리
- **효과**: 사용자 대기 시간 100% 제거 (185-770ms → 0ms)

## 결론

**완전히 다른 문제입니다!**

- **팀원**: 백엔드 스케줄러의 데이터 수집 성능 (내부 작업)
- **사용자**: 사용자 API 요청의 응답 성능 (외부 요청)

둘 다 병렬/비동기 처리를 사용하지만:
- **목적이 다름**: 수집 안정성 vs 사용자 경험
- **처리 방식이 다름**: 병렬 + 대기 vs 비동기 + 즉시 응답
- **효과가 다름**: 전체 시간 단축 vs 응답 시간 단축

**발표 시 서로 다른 문제로 구분하여 설명하면 됩니다!**

