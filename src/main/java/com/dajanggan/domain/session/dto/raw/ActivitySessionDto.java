/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.raw;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySessionDto {
    private Long dbId;
    private Integer pid;
    private String username;
    private String dbName;
    private String clientAddr;
    private String applicationName;
    private String state;
    private String waitEventType;
    private String waitEvent;
    private Long queryId;
    private String query;
    private OffsetDateTime queryStart;
    private OffsetDateTime xactStart;
}
