/** 작성자 : 서샘이 */
package com.dajanggan.domain.event.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class EventLog {

    private Long eventId;
    private Long instanceId;
    private Long databaseId;
    private String instanceName;
    private String databaseName;
    private String category;
    private String eventType;
    private String level;
    private String userName;
    private String resourceType;
    private OffsetDateTime detectedAt;
    private Double duration;
    private String description;

}
