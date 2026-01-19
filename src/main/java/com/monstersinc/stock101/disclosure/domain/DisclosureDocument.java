package com.monstersinc.stock101.disclosure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공시보고서 문서 엔티티
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisclosureDocument {

    private Long documentId;
    private Long stockId;
    private String title;
    private String documentType; // 10-K, 10-Q, 8-K 등
    private String filePath;
    private String fileName;
    private Long fileSize;
    private LocalDateTime uploadDate;
    private Long uploadedBy;
    private ProcessingStatus processingStatus;
    private Integer totalPages;
    private Integer totalChunks;
    private String metadata; // JSON 형식
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 문서 처리 상태
     */
    public enum ProcessingStatus {
        UPLOADED, // 업로드됨
        PROCESSING, // 처리 중
        COMPLETED, // 완료
        FAILED // 실패
    }
}
