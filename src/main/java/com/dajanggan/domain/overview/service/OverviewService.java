package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import com.dajanggan.domain.overview.repository.OverviewRopository;
import com.dajanggan.global.exception.DajangganException;
import com.dajanggan.global.exception.ExceptionMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class OverviewService {

    private final OverviewRopository overviewRopository;
    private final ObjectMapper objectMapper;

    public OverviewService (OverviewRopository overviewRopository, ObjectMapper objectMapper){
        this.overviewRopository = overviewRopository;
        this.objectMapper = objectMapper;
    }

    /** 사용자 커스터마이징 대시보드 저장 */
    public void saveDashboardLayout (DashboardSaveRequest dashboardSaveRequest){
        validateDashboardJson(dashboardSaveRequest.getUserLayout());

        int result = overviewRopository.saveDashboardLayout(dashboardSaveRequest);
        if(result < 1 ){
            throw new DajangganException(ExceptionMessage.DB_QUERY_EXECUTION_FAILED);
        }
    }


    /** Json 내부 유효성 검증 */
    private void validateDashboardJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // layout, widgets 존재 여부
            if (root.get("layout") == null || root.get("widgets") == null) {
                throw new DajangganException(ExceptionMessage.INVALID_DASHBOARD_JSON);
            }

            JsonNode layout = root.get("layout");

            // layout 배열인지 확인
            if (!layout.isArray()) {
                throw new DajangganException(ExceptionMessage.INVALID_WIDGET_STRUCTURE);
            }

            // 위젯 개수 제한
            if (layout.size() > 15) {
                throw new DajangganException(ExceptionMessage.WIDGET_COUNT_EXCEEDED);
            }

            // 각 layout item 검증
            for (JsonNode item : layout) {
                if (item.get("i") == null || item.get("x") == null ||
                        item.get("y") == null || item.get("w") == null || item.get("h") == null) {
                    throw new DajangganException(ExceptionMessage.INVALID_WIDGET_STRUCTURE);
                }
            }

        } catch (Exception e) {
            throw new DajangganException(ExceptionMessage.DATA_PARSING_FAILED);
        }
    }
}
