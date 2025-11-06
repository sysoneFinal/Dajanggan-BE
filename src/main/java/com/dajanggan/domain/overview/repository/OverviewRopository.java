package com.dajanggan.domain.overview.repository;

import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OverviewRopository {
    int saveDashboardLayout(DashboardSaveRequest dashboardSaveRequest);
}
