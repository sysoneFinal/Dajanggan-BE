package com.dajanggan.domain.overview.repository;

import com.dajanggan.domain.overview.dto.DashboardLayoutResponse;
import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import com.dajanggan.domain.overview.dto.MetricDefinition;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OverviewRepository {

    // 대시보드 저장 및 수정
    int saveDashboardLayout(DashboardSaveRequest dashboardSaveRequest);

    // 대시보드 조회
    DashboardLayoutResponse getUserLayout(Long instanceId);
}
