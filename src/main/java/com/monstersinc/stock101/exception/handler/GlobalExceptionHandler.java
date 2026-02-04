package com.monstersinc.stock101.exception.handler;


import com.monstersinc.stock101.common.ratelimit.RateLimitException;
import com.monstersinc.stock101.exception.GlobalException;
import com.monstersinc.stock101.exception.dto.ApiErrorResponseDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<ApiErrorResponseDto> handleException(GlobalException globalException) {

        log.error("GlobalException: {} ", globalException.getMessage());

        return new ResponseEntity<>(
                new ApiErrorResponseDto(globalException.getStatus().value() , globalException.getType() , globalException.getMessage()),
                globalException.getStatus()
        );
    }
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleConstraintViolationException(HandlerMethodValidationException e) {

        String violationMessages = "Request가 잘못된 형식입니다.";

        log.error("ConstraintViolationException: {}", violationMessages, e);

        // BAD_REQUEST (400) 상태로 응답 DTO를 생성합니다.
        return new ResponseEntity<>(
                new ApiErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_INPUT_FORMAT",
                        violationMessages
                ),
                HttpStatus.BAD_REQUEST // 400 Bad Request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        // 첫 번째 필드 에러 메시지 사용(너의 GlobalExceptionMessage 키를 DTO message에 넣어두면 그대로 내려감)
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Request가 잘못된 형식입니다.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_INPUT_FORMAT",
                        msg
                ));
    }

    /**
     * Rate Limit 예외 처리
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiErrorResponseDto> handleRateLimitException(RateLimitException e) {
        log.warn("Rate Limit 초과: apiType={}, waitTimeMs={}", e.getApiType(), e.getWaitTimeMs());

        ApiErrorResponseDto errorResponse = new ApiErrorResponseDto(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                e.getMessage()
        );

        // Retry-After 헤더 추가 (초 단위)
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(e.getWaitTimeMs() / 1000));

        return new ResponseEntity<>(errorResponse, headers, HttpStatus.TOO_MANY_REQUESTS);
    }
}
