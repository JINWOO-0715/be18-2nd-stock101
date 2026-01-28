package com.monstersinc.stock101.disclosure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공시보고서 업로드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisclosureUploadResponse {

    /**
     * 내부 소스 파일 ID
     */
    private Long sourceId;

    private Long userId;

    private Long receptNo;

    /**
     * 파일 저장 경로
     */
    private String filePath;

    /**
     * 파일 크기 (bytes)
     */
    private Long fileSize;

    /**
     * 파일 해시 (SHA-256)
     */
    private String fileHash;

    /**
     * 저장소 타입 (LOCAL, S3)
     */
    private String storageType;

    /**
     * 중복 파일 여부
     */
    private boolean isDuplicate;

    /**
     * 메시지
     */
    private String message;
}
