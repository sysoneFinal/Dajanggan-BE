// 작성자 : 김동현
package com.dajanggan.global.exception;

import lombok.Getter;

/**
 * Dajanggan 애플리케이션 전용 커스텀 예외 클래스
 * DB 모니터링 시스템에서 발생하는 비즈니스 로직 관련 예외 처리
 */
@Getter
public class DajangganException extends RuntimeException {
    private final ExceptionMessage exceptionMessage;

    public DajangganException(ExceptionMessage exceptionMessage) {
        super(exceptionMessage.getMessage());
        this.exceptionMessage = exceptionMessage;
    }
}
