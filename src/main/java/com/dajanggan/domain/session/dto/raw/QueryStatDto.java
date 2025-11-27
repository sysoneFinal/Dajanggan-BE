/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.raw;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class QueryStatDto {
    private Long queryId;
    private Double meanExecTime;
    private Long calls;
}
