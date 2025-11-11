package com.dajanggan.domain.overview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class DashboardSaveRequest {
    private Long instanceId;
    private JsonNode userLayout;

}
