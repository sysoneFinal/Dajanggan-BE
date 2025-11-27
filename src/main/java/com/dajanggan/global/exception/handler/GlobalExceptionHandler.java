// 작성자 : 김동현
package com.dajanggan.global.exception.handler;

import com.dajanggan.global.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 * PostgreSQL DB 모니터링 시스템의 모든 예외를 처리합니다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Client Error 4xx - Custom Exception 요청에 문제가 있는 경우
     */

    /**
     * 400 Bad Request - 잘못된 요청
     */
    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequestException(final BadRequestException e) {
        log.warn("BadRequestException occurred: {}", e.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problemDetail.setTitle("잘못된 요청입니다");
        return problemDetail;
    }

    /**
     * 401 Unauthorized - 인증 실패
     */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthenticationException(final AuthenticationException e) {
        log.warn("AuthenticationException occurred: {}", e.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problemDetail.setTitle("인증 실패");
        return problemDetail;
    }

    /**
     * 403 Forbidden - 접근 권한 없음
     */
    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail handleForbiddenException(final ForbiddenException e) {
        log.warn("ForbiddenException occurred: {}", e.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problemDetail.setTitle("접근 권한 없음");
        return problemDetail;
    }

    /**
     * 404 Not Found - 데이터 없음
     */
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFoundException(final NotFoundException e) {
        log.warn("NotFoundException occurred: {}", e.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problemDetail.setTitle("데이터 없음");
        return problemDetail;
    }

    /**
     * 409 Conflict - 데이터 충돌
     */
    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflictException(final ConflictException e) {
        log.warn("ConflictException occurred: {}", e.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problemDetail.setTitle("데이터 충돌");
        return problemDetail;
    }

    /**
     * Dajanggan 커스텀 예외 처리
     * DB 모니터링 관련 비즈니스 로직 예외
     */
    @ExceptionHandler(DajangganException.class)
    ProblemDetail handleDajangganException(final DajangganException e) {
//        log.error("DajangganException occurred: {}", e.getMessage());
        log.error("DajangganException occurred: {}", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        problemDetail.setTitle("DB 모니터링 오류");
        return problemDetail;
    }

    /**
     * IOException 처리 (SSE 연결 끊김 등)
     * SSE 연결이 끊어질 때 발생하는 정상적인 IOException은 무시
     */
    @ExceptionHandler(java.io.IOException.class)
    void handleIOException(final java.io.IOException e) {
        // 클라이언트 연결 끊김은 정상적인 상황이므로 DEBUG 레벨로 로깅
        log.debug("IOException occurred (클라이언트 연결 끊김): {}", e.getMessage());
        // 응답하지 않음 (이미 연결이 끊어진 상태)
    }

    /**
     * Internal Server Error 5xx :
     * 예외처리가 제대로 되지 않았거나 코드 자체의 문제인 경우일 확률 높음
     * 코드를 고치거나 해당 예외처리 핸들러를 추가해줘야 함
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleInternalError(final Exception e) {
        log.error("Uncaught {} - {}", e.getClass().getSimpleName(), e.getMessage());
        e.printStackTrace();
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        problemDetail.setTitle("서버 내부 오류");
        return problemDetail;
    }

}
