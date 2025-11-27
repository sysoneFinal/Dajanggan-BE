/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class WidgetWithData {
    private String id;
    private String chartType;
    private String title;
    private JsonNode layout;
    private JsonNode options;
    private List<String> metrics;
    private List<Map<String, Object>> data;  // 실제 메트릭 데이터
    private String error;  // 에러 메시지 (있을 경우)
}
