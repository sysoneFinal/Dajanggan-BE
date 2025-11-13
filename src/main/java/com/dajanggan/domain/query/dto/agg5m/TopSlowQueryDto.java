package com.dajanggan.domain.query.dto.agg5m;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class TopSlowQueryDto {
    private Long instanceId;
    private Long databaseId;
    private String topSlowQuery1;
    private Double topSlowQuery1Time;
    private String topSlowQuery2;
    private Double topSlowQuery2Time;
    private String topSlowQuery3;
    private Double topSlowQuery3Time;
    private String topSlowQuery4;
    private Double topSlowQuery4Time;
    private String topSlowQuery5;
    private Double topSlowQuery5Time;
}