package com.dajanggan.domain.overview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutResponse {
    private Long instanceId;
    private JsonNode userLayout;
}
