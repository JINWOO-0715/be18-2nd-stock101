package com.monstersinc.stock101.exception.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;


@Getter
@RequiredArgsConstructor
public enum GlobalExceptionMessage {

    DUPLICATE_EMAIL("중복된 이메일 입니다.", HttpStatus.CONFLICT),

    USER_NOT_FOUND("유저 정보를 찾을 수 없습니다.",HttpStatus.NOT_FOUND),

    UNAUTHORIZED_TOKEN("유효한 인증 토큰이 없습니다.", HttpStatus.UNAUTHORIZED),
  
    UNAUTHORIZED_USER("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED),

    INDICATOR_NOT_FOUND("지표 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    STOCK_NOT_FOUND("주식 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    DUPLICATE_PREDICTION("이미 동일한 종목에 대한 예측이 존재합니다.", HttpStatus.CONFLICT),

    POST_NOT_FOUND("게시물 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // KIS API 관련
    KIS_API_UNAVAILABLE("KIS API 일시적 장애입니다. 캐시 데이터로 응답합니다.", HttpStatus.SERVICE_UNAVAILABLE),
    KIS_RATE_LIMIT_EXCEEDED("KIS API 호출 제한에 도달했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    KIS_TOKEN_REFRESH_FAILED("KIS 인증 토큰 갱신 실패", HttpStatus.INTERNAL_SERVER_ERROR),

    // DART API 관련
    DART_API_UNAVAILABLE("DART API 일시적 장애입니다.", HttpStatus.SERVICE_UNAVAILABLE),
    DART_RATE_LIMIT_EXCEEDED("DART API 호출 제한 초과", HttpStatus.TOO_MANY_REQUESTS),

    // Cache 관련
    CACHE_OPERATION_FAILED("캐시 작업 실패", HttpStatus.INTERNAL_SERVER_ERROR),

    // Queue 관련
    QUEUE_REQUEST_FAILED("대량 조회 요청 실패", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;

    private final HttpStatus status;
}
