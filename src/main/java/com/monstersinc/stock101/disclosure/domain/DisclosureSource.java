package com.monstersinc.stock101.disclosure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공시보고서 소스 파일 엔티티
 * 테이블: disclosure_source
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisclosureSource {

    /**
     * 내부 관리용 파일 ID (PK, AUTO_INCREMENT)
     */
    private Long sourceId;

    private Long userId;

    /**
     * DART 접수번호 (외래키)
     */
    private String rceptNo;

    /**
     * S3 또는 로컬 파일 저장 경로
     */
    private String filePath;

    /**
     * 파일 SHA-256 해시값 (중복 체크용)
     */
    private String fileHash;

    /**
     * 파일 크기 (Byte)
     */
    private Long fileSize;

    /**
     * 저장소 유형 (LOCAL, S3 등)
     */
    @Builder.Default
    private StorageType storageType = StorageType.S3;

    private String status;

    private String companyName;

    /**
     * 생성 시간
     */
    private LocalDateTime createdAt;

    /**
     * 저장소 유형 enum
     */
    public enum StorageType {
        LOCAL,
        S3
    }
}
