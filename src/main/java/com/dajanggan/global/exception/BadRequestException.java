// 작성자 : 김동현
package com.dajanggan.global.exception;

import lombok.Getter;

@Getter
public class BadRequestException extends RuntimeException {
    private final ExceptionMessage exceptionMessage;

    public BadRequestException(ExceptionMessage exceptionMessage) {
        super(exceptionMessage.getMessage());
        this.exceptionMessage = exceptionMessage;
    }
}
