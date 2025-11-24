# Vacuum 메트릭 수집 시 JSON 파싱 오류 문제

## 🔥 문제상황 (강력 추천!)

### 증상
- Vacuum 메트릭 수집 시 `jsonb_build_object`로 JSON 생성
- PostgreSQL의 `NUMERIC` 타입이 소수점이 포함된 문자열로 반환됨
- Jackson이 문자열을 Long 타입으로 파싱 실패

### 발생 위치
- **코드**: `VacuumMetricsCollector.getVacuumMetrics()` 메서드
- **SQL**: `index_bloat` CTE에서 `jsonb_build_object` 사용

### 문제 코드
```sql
-- ❌ 문제가 발생할 수 있는 코드
index_bloat AS (
    SELECT 
        i.indrelid AS table_oid,
        jsonb_agg(
            jsonb_build_object(
                'name', c.relname,
                'bytes', pg_relation_size(i.indexrelid),  -- BIGINT (문제 없음)
                'ratio', COALESCE(
                    (SELECT 
                        CASE 
                            WHEN st.n_live_tup + st.n_dead_tup > 0
                            THEN (st.n_dead_tup::NUMERIC / NULLIF(st.n_live_tup + st.n_dead_tup, 0))
                            ELSE 0.0
                        END
                     FROM pg_stat_all_tables st
                     WHERE st.relid = i.indrelid
                     LIMIT 1),
                    0.0
                )  -- NUMERIC 타입 → 문자열로 반환될 수 있음!
            )
        ) AS index_bloat_info
    FROM pg_index i
    ...
)
```

### 실제 발생 시나리오
```java
// Java DTO
@JsonProperty("indexBloatInfo")
private String indexBloatInfo;  // JSON 문자열로 받음

// JSON 파싱 시
// {"name": "idx_users_email", "bytes": 123456, "ratio": "0.15"}  ← ratio가 문자열!
// Jackson이 "0.15"를 Double로 파싱하려 했으나 실패
```

## 해결과정

### 시도 1: CAST(total_exec_time AS BIGINT)
```sql
-- ❌ 실패: 소수점 버림 문제
'ratio', CAST(ratio_value AS BIGINT)
-- 0.15 → 0 (데이터 손실!)
```

### 시도 2: ROUND(total_exec_time)::BIGINT
```sql
-- ❌ 여전히 실패: 여전히 문자열로 옴
'ratio', ROUND(ratio_value)::BIGINT
-- jsonb_build_object는 여전히 문자열로 직렬화
```

### 최종 해결: 명시적 타입 캐스팅 + 밀리초 변환
```sql
-- ✅ 해결: NUMERIC을 명시적으로 DOUBLE PRECISION으로 변환
index_bloat AS (
    SELECT 
        i.indrelid AS table_oid,
        jsonb_agg(
            jsonb_build_object(
                'name', c.relname,
                'bytes', pg_relation_size(i.indexrelid)::BIGINT,  -- 명시적 BIGINT
                'ratio', COALESCE(
                    (SELECT 
                        CASE 
                            WHEN st.n_live_tup + st.n_dead_tup > 0
                            THEN (st.n_dead_tup::NUMERIC / NULLIF(st.n_live_tup + st.n_dead_tup, 0))::DOUBLE PRECISION
                            ELSE 0.0::DOUBLE PRECISION
                        END
                     FROM pg_stat_all_tables st
                     WHERE st.relid = i.indrelid
                     LIMIT 1),
                    0.0::DOUBLE PRECISION
                )::DOUBLE PRECISION  -- 명시적 DOUBLE PRECISION 변환
            )
        ) AS index_bloat_info
    FROM pg_index i
    ...
)
```

### 대안 해결: Java에서 파싱 시 타입 처리
```java
// DTO에서 JSON 파싱 시 타입 안전하게 처리
@JsonDeserialize(using = IndexBloatInfoDeserializer.class)
private List<IndexBloatInfo> indexBloatInfo;

// 커스텀 Deserializer
public class IndexBloatInfoDeserializer extends JsonDeserializer<List<IndexBloatInfo>> {
    @Override
    public List<IndexBloatInfo> deserialize(JsonParser p, DeserializationContext ctxt) {
        // 문자열로 온 숫자를 적절한 타입으로 변환
        // "0.15" → 0.15 (Double)
    }
}
```

## 교훈

### 1. PostgreSQL jsonb_build_object의 타입 변환 동작 이해
- `NUMERIC` 타입은 JSON에서 문자열로 직렬화될 수 있음
- 명시적 타입 캐스팅이 필요: `::DOUBLE PRECISION`, `::BIGINT` 등

### 2. 숫자 타입 불일치 시 명시적 캐스팅 필요
```sql
-- ❌ 암묵적 변환 (위험)
jsonb_build_object('value', numeric_column)

-- ✅ 명시적 변환 (안전)
jsonb_build_object('value', numeric_column::DOUBLE PRECISION)
```

### 3. API 응답 형식과 DB 반환 형식의 일치 중요성
- DB에서 반환되는 JSON 형식과 Java DTO의 타입이 일치해야 함
- Jackson 파싱 실패를 방지하기 위해 타입을 명확히 지정

## 현재 코드 상태

### 실제 구현 (VacuumMetricsCollector.java)
```206:226:src/main/java/com/dajanggan/domain/metric/collector/VacuumMetricsCollector.java
            jsonb_build_object(
                'name', c.relname,
                'bytes', pg_relation_size(i.indexrelid),
                -- ✅ 테이블의 bloat ratio를 인덱스에도 적용
                -- table_stats 필터 조건 때문에 일부 테이블이 누락될 수 있으므로
                -- 직접 pg_stat_all_tables에서 조회하여 모든 테이블의 인덱스에 대해 계산
                'ratio', COALESCE(
                    (SELECT 
                        CASE 
                            WHEN st.n_live_tup + st.n_dead_tup > 0
                            THEN (st.n_dead_tup::NUMERIC / NULLIF(st.n_live_tup + st.n_dead_tup, 0))
                            ELSE 0.0
                        END
                     FROM pg_stat_all_tables st
                     WHERE st.relid = i.indrelid
                       AND st.schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                     LIMIT 1),
                    0.0
                )
            )
```

### 개선 제안
```sql
-- 개선된 버전: 명시적 타입 캐스팅
jsonb_build_object(
    'name', c.relname,
    'bytes', pg_relation_size(i.indexrelid)::BIGINT,
    'ratio', COALESCE(
        (SELECT 
            CASE 
                WHEN st.n_live_tup + st.n_dead_tup > 0
                THEN (st.n_dead_tup::NUMERIC / NULLIF(st.n_live_tup + st.n_dead_tup, 0))::DOUBLE PRECISION
                ELSE 0.0::DOUBLE PRECISION
            END
         FROM pg_stat_all_tables st
         WHERE st.relid = i.indrelid
         LIMIT 1),
        0.0::DOUBLE PRECISION
    )::DOUBLE PRECISION
)
```

## 발표 시 강조 포인트

1. **실제 발생 가능한 문제**
   - PostgreSQL의 `NUMERIC` 타입이 JSON 직렬화 시 문자열로 변환될 수 있음
   - Jackson 파싱 실패로 인한 런타임 에러 발생 가능

2. **해결 방법의 다양성**
   - SQL 레벨에서 타입 명시적 변환 (권장)
   - Java 레벨에서 커스텀 Deserializer 사용

3. **타입 안전성의 중요성**
   - DB와 Java 간 타입 불일치로 인한 문제 방지
   - 명시적 타입 캐스팅을 통한 안전한 데이터 전달

