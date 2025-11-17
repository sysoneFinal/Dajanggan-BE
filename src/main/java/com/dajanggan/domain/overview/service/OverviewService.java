package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.overview.dto.*;
import com.dajanggan.domain.overview.repository.OverviewRepository;
import com.dajanggan.global.exception.DajangganException;
import com.dajanggan.global.exception.ExceptionMessage;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class OverviewService {

    private final OverviewRepository overviewRepository;
    private final MetricsQueryService metricsQueryService;

    public OverviewService(
            OverviewRepository overviewRepository,
            MetricsQueryService metricsQueryService
    ){
        this.overviewRepository = overviewRepository;
        this.metricsQueryService = metricsQueryService;
    }


    /**
     * 사용자 대시보드 + 데이터 조회 (한번에!)
     */
    public DashboardDataResponse getDashboardWithData(Long instanceId) {
        // 1. 사용자 레이아웃 조회
        DashboardLayoutResponse dashboard = overviewRepository.getUserLayout(instanceId);
        
        if (dashboard == null || dashboard.getUserLayout() == null) {
            log.error("대시보드를 찾을 수 없음: instanceId={}", instanceId);
            throw new DajangganException(ExceptionMessage.DASHBOARD_NOT_FOUND);
        }

        log.info("대시보드 조회 성공: instanceId={}", instanceId);

        JsonNode userLayout = dashboard.getUserLayout();
        JsonNode widgetsNode = userLayout.get("widgets");

        if (widgetsNode == null || !widgetsNode.isArray()) {
            log.error("위젯 구조가 잘못됨: instanceId={}", instanceId);
            throw new DajangganException(ExceptionMessage.INVALID_WIDGET_STRUCTURE);
        }

        // 2. 각 위젯별로 데이터 조회
        List<WidgetWithData> widgetsWithData = new ArrayList<>();
        
        for (JsonNode widgetNode : widgetsNode) {
            try {
                WidgetWithData widgetData = processWidget(instanceId, widgetNode);
                widgetsWithData.add(widgetData);
                log.debug("위젯 데이터 조회 성공: widgetId={}", widgetNode.get("id").asText());
            } catch (Exception e) {
                log.error("위젯 데이터 조회 실패: widgetId={}, error={}", 
                    widgetNode.get("id").asText(), e.getMessage(), e);
                // 에러 발생해도 다른 위젯은 계속 처리
                widgetsWithData.add(createErrorWidget(widgetNode, e.getMessage()));
            }
        }

        return DashboardDataResponse.builder()
            .instanceId(instanceId)
            .widgets(widgetsWithData)
            .build();
    }

    /**
     * 위젯 데이터 처리
     */
    private WidgetWithData processWidget(Long instanceId, JsonNode widgetNode) {
        String widgetId = widgetNode.get("id").asText();
        String chartType = widgetNode.get("chartType").asText();
        String title = widgetNode.get("title").asText();
        
        // databases 배열에서 첫 번째 DB 정보 추출
        JsonNode databasesNode = widgetNode.get("databases");
        if (databasesNode == null || !databasesNode.isArray() || databasesNode.size() == 0) {
            throw new IllegalArgumentException("데이터베이스 정보가 없습니다");
        }
        
        String dbName = databasesNode.get(0).get("name").asText();
        
        // metrics 배열 추출
        JsonNode metricsNode = widgetNode.get("metrics");
        if (metricsNode == null || !metricsNode.isArray()) {
            throw new IllegalArgumentException("메트릭 정보가 없습니다");
        }
        
        List<String> metrics = new ArrayList<>();
        for (JsonNode metric : metricsNode) {
            metrics.add(metric.asText());
        }

        // 고정값: 15분
        String timeRange = "15m";

        // 실제 메트릭 데이터 조회
        List<Map<String, Object>> data = metricsQueryService.queryMetrics(
            dbName, 
            instanceId, 
            metrics, 
            timeRange
        );

        return WidgetWithData.builder()
            .id(widgetId)
            .chartType(chartType)
            .title(title)
            .layout(widgetNode.get("layout"))
            .options(widgetNode.get("options"))
            .metrics(metrics)
            .data(data)
            .error(null)
            .build();
    }

    /**
     * 에러 위젯 생성
     */
    private WidgetWithData createErrorWidget(JsonNode widgetNode, String errorMessage) {
        return WidgetWithData.builder()
            .id(widgetNode.get("id").asText())
            .chartType(widgetNode.get("chartType").asText())
            .title(widgetNode.get("title").asText())
            .layout(widgetNode.get("layout"))
            .options(widgetNode.get("options"))
            .data(Collections.emptyList())
            .error(errorMessage)
            .build();
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
