/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.agg5m;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ConnectionDto {

    private Double usedConnections;    // 사용 커넥션 수
    private Double maxConnections;     // 최대 커넥션 수
}
