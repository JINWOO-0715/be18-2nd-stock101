package com.monstersinc.stock101.kis.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monstersinc.stock101.kis.queue.StockPriceUpdateRequest.RequestPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

/**
 * Redis 기반 주식 시세 업데이트 Queue 관리
 * Sorted Set을 사용한 우선순위 큐
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceUpdateQueue {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String QUEUE_KEY_PREFIX = "kis:queue:";
    private static final String STATUS_KEY_PREFIX = "kis:status:";

    /**
     * Queue에 요청 추가
     *
     * @param request 업데이트 요청
     * @return 요청 ID
     */
    public String enqueue(StockPriceUpdateRequest request) {
        try {
            String queueKey = QUEUE_KEY_PREFIX + request.getPriority().name();
            String requestJson = objectMapper.writeValueAsString(request);

            // Redis Sorted Set 사용 (score: 생성 시각의 epoch second)
            double score = request.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
            redisTemplate.opsForZSet().add(queueKey, requestJson, score);

            // 상태 저장
            setStatus(request.getRequestId(), RequestStatus.QUEUED);

            log.info("Queue 추가: requestId={}, stockCode={}, priority={}, estimatedCalls={}",
                    request.getRequestId(), request.getStockCode(),
                    request.getPriority(), request.estimateApiCalls());

            return request.getRequestId();

        } catch (JsonProcessingException e) {
            log.error("Queue 추가 실패 (JSON 직렬화 오류): {}", e.getMessage());
            throw new RuntimeException("Queue 추가 실패", e);
        }
    }

    /**
     * Queue에서 요청 가져오기 (우선순위 HIGH 먼저)
     *
     * @return 처리할 요청 (없으면 empty)
     */
    public Optional<StockPriceUpdateRequest> dequeue() {
        // HIGH 우선순위 먼저 확인
        Optional<StockPriceUpdateRequest> request = dequeueFromPriority(RequestPriority.HIGH);
        if (request.isPresent()) {
            return request;
        }

        // HIGH가 없으면 LOW
        return dequeueFromPriority(RequestPriority.LOW);
    }

    /**
     * 특정 우선순위에서 요청 꺼내기
     */
    private Optional<StockPriceUpdateRequest> dequeueFromPriority(RequestPriority priority) {
        try {
            String queueKey = QUEUE_KEY_PREFIX + priority.name();

            // ZPOPMIN: 가장 오래된(가장 작은 score) 항목 꺼내기
            Set<ZSetOperations.TypedTuple<String>> result =
                    redisTemplate.opsForZSet().popMin(queueKey, 1);

            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }

            String requestJson = result.iterator().next().getValue();
            if (requestJson == null) {
                return Optional.empty();
            }

            StockPriceUpdateRequest request = objectMapper.readValue(requestJson, StockPriceUpdateRequest.class);
            log.debug("Queue에서 꺼냄: requestId={}, priority={}", request.getRequestId(), priority);

            return Optional.of(request);

        } catch (Exception e) {
            log.error("Queue dequeue 실패: priority={}, error={}", priority, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 요청 상태 조회
     */
    public RequestStatus getStatus(String requestId) {
        String statusKey = STATUS_KEY_PREFIX + requestId;
        String status = redisTemplate.opsForValue().get(statusKey);
        return status != null ? RequestStatus.valueOf(status) : RequestStatus.NOT_FOUND;
    }

    /**
     * 요청 상태 설정
     */
    public void setStatus(String requestId, RequestStatus status) {
        String statusKey = STATUS_KEY_PREFIX + requestId;
        redisTemplate.opsForValue().set(statusKey, status.name(), Duration.ofHours(24));
        log.debug("상태 변경: requestId={}, status={}", requestId, status);
    }

    /**
     * Queue 크기 조회
     */
    public long getQueueSize(RequestPriority priority) {
        String queueKey = QUEUE_KEY_PREFIX + priority.name();
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0;
    }

    /**
     * 전체 Queue 크기
     */
    public long getTotalQueueSize() {
        return getQueueSize(RequestPriority.HIGH) + getQueueSize(RequestPriority.LOW);
    }
}
