package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.overview.dto.DashboardLayoutResponse;
import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import com.dajanggan.domain.overview.dto.MetricDefinition;
import com.dajanggan.domain.overview.repository.OverviewRepository;
import com.dajanggan.global.exception.DajangganException;
import com.dajanggan.global.exception.ExceptionMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class OverviewService {

    private final OverviewRepository overviewRepository;

    public OverviewService (OverviewRepository overviewRepository){
        this.overviewRepository = overviewRepository;
    }


    /** 사용자 대시보드 조회 */
    public DashboardLayoutResponse getUserLayout(Long instanceId){
        DashboardLayoutResponse response = overviewRepository.getUserLayout(instanceId);
        log.info("인스턴스 정보 {}", instanceId);
        log.info("사용자 대시보드 조회 {}", response );
        return response;
    }





    /** 사용자 커스터마이징 대시보드 저장 */
    @Transactional
    public void saveDashboardLayout (DashboardSaveRequest dashboardSaveRequest){
        log.info("대시보드 저장 {}", dashboardSaveRequest);
        validateDashboardJson(dashboardSaveRequest.getUserLayout());
        int result = overviewRepository.saveDashboardLayout(dashboardSaveRequest);
        if(result < 1 ){
            throw new DajangganException(ExceptionMessage.DB_QUERY_EXECUTION_FAILED);
        }
    }

    /** Json 내부 유효성 검증 */
    private void validateDashboardJson(JsonNode root) {
        try {
            if (root == null || !root.has("widgets")) {
                throw new DajangganException(ExceptionMessage.INVALID_DASHBOARD_JSON);
            }

            JsonNode widgets = root.get("widgets");

            // widgets 배열인지 확인
            if (!widgets.isArray()) {
                throw new DajangganException(ExceptionMessage.INVALID_WIDGET_STRUCTURE);
            }

            // 위젯 개수 제한
            if (widgets.size() > 15) {
                throw new DajangganException(ExceptionMessage.WIDGET_COUNT_EXCEEDED);
            }

            // 각 위젯 내부 검증
            for (JsonNode widget : widgets) {
                if (!widget.has("id") || !widget.has("chartType") || !widget.has("layout")) {
                    throw new DajangganException(ExceptionMessage.INVALID_WIDGET_STRUCTURE);
                }

                JsonNode layout = widget.get("layout");
                if (!layout.has("x") || !layout.has("y") ||
                        !layout.has("w") || !layout.has("h")) {
                    throw new DajangganException(ExceptionMessage.INVALID_WIDGET_STRUCTURE);
                }
            }

        } catch (Exception e) {
            log.error( "대시보드 JSON 검증 실패: {}", root, e);
            throw new DajangganException(ExceptionMessage.DATA_PARSING_FAILED);
        }
    }
}
