package com.dajanggan.domain.overview.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class DashboardSaveRequest {
    private Long instanceId;
    private String userLayout;

}
