/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.dto;

import lombok.*;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDataResponse {
    private Long instanceId;
    private List<WidgetWithData> widgets;
}
