/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.controller;

import com.dajanggan.domain.overview.dto.MetricDefinition;
import com.dajanggan.domain.overview.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Metric", description = "커스텀 대시보드 지표 관련 API")
@RestController
@RequestMapping("/api/metric")
public class MetricController {

    private final MetricService metricService;

    public MetricController(MetricService metricService){
        this.metricService = metricService;
    }

    @Operation(summary = "지표 정의 목록 조회", description = "정의된 지표를 조회하여 커스텀 대시보드 구현에 적용됩니다.")
    @GetMapping("/list")
    public ResponseEntity<List<MetricDefinition>> getMetricList(){
        List<MetricDefinition> list = metricService.getMetricList();
        return ResponseEntity.ok(list);
    }
}
