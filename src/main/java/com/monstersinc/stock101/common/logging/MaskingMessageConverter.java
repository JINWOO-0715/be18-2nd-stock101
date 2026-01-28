package com.monstersinc.stock101.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 민감한 정보를 마스킹하는 Logback 메시지 컨버터
 * 운영 환경(prod)에서 API 키, 비밀번호, 토큰 등을 로그에서 자동으로 마스킹
 */
public class MaskingMessageConverter extends MessageConverter {

    /**
     * 마스킹 대상 패턴 목록
     * JSON 필드, URL 파라미터, HTTP 헤더 등 다양한 형식 지원
     */
    private static final Pattern[] SENSITIVE_PATTERNS = {
        // JSON 형식: "appkey":"value"
        Pattern.compile("(\"appkey\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        // JSON 형식: "secretkey":"value", "appsecret":"value"
        Pattern.compile("(\"(?:secretkey|appsecret|secret)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        // JSON 형식: "password":"value"
        Pattern.compile("(\"password\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        // JSON 형식: "token":"value"
        Pattern.compile("(\"token\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        // JSON 형식: "access_token":"value", "refresh_token":"value"
        Pattern.compile("(\"(?:access_token|refresh_token)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        // URL 파라미터: appkey=value
        Pattern.compile("(appkey=)([^&\\s]+)", Pattern.CASE_INSENSITIVE),
        // URL 파라미터: secretkey=value
        Pattern.compile("(secretkey=)([^&\\s]+)", Pattern.CASE_INSENSITIVE),
        // HTTP 헤더: Authorization: Bearer token
        Pattern.compile("(Authorization:\\s*Bearer\\s+)([^\\s]+)", Pattern.CASE_INSENSITIVE),
        // JWT 토큰 패턴 (eyJ로 시작)
        Pattern.compile("(eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)\\.[A-Za-z0-9_-]+"),
        // API 키 패턴 (일반적으로 32자 이상의 영숫자)
        Pattern.compile("\\b([A-Za-z0-9]{32,})\\b")
    };

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        
        if (message == null) {
            return null;
        }

        // 모든 민감 정보 패턴에 대해 마스킹 적용
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            StringBuffer sb = new StringBuffer();
            
            while (matcher.find()) {
                String replacement;
                if (matcher.groupCount() >= 2) {
                    // 그룹이 있는 경우 (JSON, URL 파라미터 등)
                    String prefix = matcher.group(1);
                    String sensitiveValue = matcher.group(2);
                    String suffix = matcher.groupCount() >= 3 ? matcher.group(3) : "";
                    replacement = prefix + maskValue(sensitiveValue) + suffix;
                } else {
                    // 그룹이 없는 경우 (JWT, API 키 등)
                    String sensitiveValue = matcher.group(0);
                    replacement = maskValue(sensitiveValue);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            message = sb.toString();
        }

        return message;
    }

    /**
     * 민감한 값을 마스킹 처리
     * - 8자 이하: 전체를 ****로 마스킹
     * - 20자 이하: 앞 2자 + **** + 뒤 2자
     * - 20자 초과: 앞 4자 + **** + 뒤 4자
     */
    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "****";
        }

        int length = value.length();
        
        if (length <= 8) {
            return "****";
        } else if (length <= 20) {
            return value.substring(0, 2) + "****" + value.substring(length - 2);
        } else {
            return value.substring(0, 4) + "****" + value.substring(length - 4);
        }
    }
}
