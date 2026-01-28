package com.monstersinc.stock101.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.Map;

@Slf4j
@Service
public class DoclingApiService {

    private final WebClient webClient;

    public DoclingApiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://127.0.0.1:8000") // Docling API의 실제 호스트와 포트로 변경
                .build();
    }

    public String callAnalyze(String filePath) {
        log.info("비동기 분석 요청 시작: {}", filePath);
        File file = new File(filePath);
        String absolutePath = file.getAbsolutePath().replace("\\", "/");
        log.info("파이썬으로 전송할 절대 경로: {}", absolutePath);

        return webClient.post()
                .uri("/analyze")
                .bodyValue(Map.of("path", absolutePath))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(res -> {
                    // 1. 파이썬 응답 자체가 null인 경우
                    if (res == null) {
                        return Mono.error(new RuntimeException("파이썬 서버로부터 빈 응답을 받았습니다."));
                    }

                    // 2. 파이썬에서 명시적 에러를 보낸 경우
                    if ("error".equals(res.get("status")) || res.containsKey("error")) {
                        return Mono.error(new RuntimeException("파이썬 분석 에러: " + res.get("error")));
                    }

                    // 3. 정상 응답에서 content 추출
                    String content = (String) res.get("content");
                    if (content == null) {
                        return Mono.error(new RuntimeException("응답에 'content' 필드가 누락되었습니다."));
                    }
                    return Mono.just((String) res.get("content"));
                })
                .block();
    }
}


