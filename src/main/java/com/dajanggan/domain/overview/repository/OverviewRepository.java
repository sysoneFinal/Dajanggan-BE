package com.dajanggan.domain.overview.repository;

import com.dajanggan.domain.overview.dto.DashboardLayoutResponse;
import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import com.dajanggan.domain.overview.dto.MetricDefinition;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface OverviewRepository {

    // 대시보드 저장 및 수정
    int saveDashboardLayout(DashboardSaveRequest dashboardSaveRequest);

    // 대시보드 조회
    DashboardLayoutResponse getUserLayout(Long instanceId);

    // 메트릭 정의 조회 (metric_definition 테이블)
    List<MetricDefinition> getMetricDefinitions(List<String> metricNames);

    // 동적 메트릭 데이터 조회
    List<Map<String, Object>> queryMetrics(Map<String, Object> params);
    
    // 🔥 디폴트 대시보드 생성 (인스턴스 등록 시 자동 호출)
    int createDefaultDashboard(DashboardSaveRequest dashboardSaveRequest);
}
