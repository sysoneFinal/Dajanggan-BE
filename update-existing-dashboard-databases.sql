-- 이미 들어간 대시보드 데이터의 databases 배열을 업데이트하는 SQL
-- 각 인스턴스의 모든 DB를 databases 배열에 추가

-- 1. 먼저 인스턴스별 데이터베이스 목록 확인
SELECT 
    mi.instance_id,
    mi.instance_name,
    md.database_id,
    md.database_name
FROM monitor_instance mi
LEFT JOIN monitor_database md ON mi.instance_id = md.instance_id
ORDER BY mi.instance_id, md.database_id;

-- 2. 특정 인스턴스의 대시보드 databases 배열 업데이트
-- 아래의 {INSTANCE_ID}를 실제 인스턴스 ID로 변경하세요

WITH instance_databases AS (
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', database_id,
            'name', database_name
        )
    ) as databases
    FROM monitor_database
    WHERE instance_id = {INSTANCE_ID}
)
UPDATE user_dashboard
SET user_layout = (
    SELECT jsonb_set(
        user_layout,
        '{widgets}',
        (
            SELECT jsonb_agg(
                jsonb_set(
                    widget,
                    '{databases}',
                    COALESCE(id.databases, '[]'::jsonb)
                )
            )
            FROM jsonb_array_elements(user_layout->'widgets') as widget
        )
    )
    FROM instance_databases id
),
updated_at = NOW()
WHERE instance_id = {INSTANCE_ID}
AND EXISTS (
    SELECT 1 
    FROM instance_databases 
    WHERE databases IS NOT NULL
);

-- 3. 모든 인스턴스의 대시보드 databases 배열 일괄 업데이트
-- 주의: 모든 인스턴스의 대시보드를 업데이트합니다
/*
UPDATE user_dashboard ud
SET user_layout = (
    SELECT jsonb_set(
        ud.user_layout,
        '{widgets}',
        (
            SELECT jsonb_agg(
                jsonb_set(
                    widget,
                    '{databases}',
                    COALESCE(
                        (
                            SELECT jsonb_agg(
                                jsonb_build_object(
                                    'id', database_id,
                                    'name', database_name
                                )
                            )
                            FROM monitor_database
                            WHERE instance_id = ud.instance_id
                        ),
                        '[]'::jsonb
                    )
                )
            )
            FROM jsonb_array_elements(ud.user_layout->'widgets') as widget
        )
    )
),
updated_at = NOW()
WHERE EXISTS (
    SELECT 1 
    FROM monitor_database md
    WHERE md.instance_id = ud.instance_id
);
*/

-- 4. 업데이트 결과 확인
SELECT 
    dashboard_id,
    instance_id,
    jsonb_pretty(user_layout) as user_layout,
    updated_at
FROM user_dashboard
WHERE instance_id = {INSTANCE_ID};

-- 5. 위젯별 databases 배열 확인
SELECT 
    instance_id,
    widget->>'id' as widget_id,
    widget->>'title' as widget_title,
    jsonb_pretty(widget->'databases') as databases,
    jsonb_array_length(widget->'databases') as db_count
FROM user_dashboard,
     jsonb_array_elements(user_layout->'widgets') as widget
WHERE instance_id = {INSTANCE_ID}
ORDER BY widget->>'id';

