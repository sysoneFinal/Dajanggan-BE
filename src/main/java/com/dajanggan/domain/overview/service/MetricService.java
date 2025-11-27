/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.overview.dto.MetricDefinition;
import com.dajanggan.domain.overview.repository.MetricRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MetricService {

    private final MetricRepository metricRepository;

    public MetricService(MetricRepository metricRepository){
        this.metricRepository = metricRepository;
    }


    /** 지표 목록 조회 */
    public List<MetricDefinition> getMetricList(){
        return metricRepository.getMetricList();
    }

    /** 지표 데이터 조회*/
    public List<Map<String, Object>> getMetricData(
            String tableName, String columnName,
            String instanceId, String databaseId,
            LocalDateTime startTime, LocalDateTime endTime) {

        // 시간 미지정 시 기본값 (최근 24시간)
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(24);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        return metricRepository.getMetricData(
                tableName, columnName, instanceId,
                databaseId, startTime, endTime
        );
    }

}
