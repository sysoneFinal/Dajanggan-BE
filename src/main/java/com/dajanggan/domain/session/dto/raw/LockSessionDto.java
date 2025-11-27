/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.raw;

import lombok.*;


@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LockSessionDto {
    private String mode; // 락의 강도
    private String lockType;  // 무슨 락인지
    private String tableName;
    private Double waitDurationSec;  // 실제 대기 시간

}
