package com.monstersinc.stock101.disclosure.service;

import com.monstersinc.stock101.common.service.DocumentAsyncProcessor;
import com.monstersinc.stock101.common.service.FileStorageService;
import com.monstersinc.stock101.disclosure.domain.DisclosureSource;
import com.monstersinc.stock101.disclosure.domain.ProcessStatus;
import com.monstersinc.stock101.disclosure.dto.DisclosureUploadResponse;
import com.monstersinc.stock101.disclosure.repository.DisclosureSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 공시보고서 소스 파일 비즈니스 로직 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisclosureService {

    private final DisclosureSourceRepository sourceRepository;
    private final FileStorageService fileStorageService;
    private final DocumentAsyncProcessor asyncProcessor;

    /**
     * 공시보고서 업로드
     * @param file PDF 파일
     * @param userId 사용자 ID
     * @param stockId 주식 ID (선택)
     * @return 업로드된 소스 파일 정보
     */
    @Transactional
    public DisclosureUploadResponse uploadDocument(MultipartFile file, Long userId, String stockId) throws IOException {
        String fileHash = calculateFileHash(file);

        // 중복 체크 및 재업로드 처리
        Optional<DisclosureSource> existing = sourceRepository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            return handleExistingFile(existing.get(), file, fileHash);
        }

        // 새 파일 업로드 처리
        return handleNewFileUpload(file, userId, stockId, fileHash);
    }

    /**
     * 기존 파일 처리 (재업로드 또는 중복)
     */
    private DisclosureUploadResponse handleExistingFile(DisclosureSource existingSource,
                                                        MultipartFile file,
                                                        String fileHash) {
        ProcessStatus existingStatus = ProcessStatus.fromString(existingSource.getStatus());

        if (existingStatus.isFailed()) {
            return handleFailedFileReupload(existingSource, file, fileHash);
        }

        return buildDuplicateResponse(existingSource);
    }

    /**
     * 실패한 파일 재업로드 처리
     */
    private DisclosureUploadResponse handleFailedFileReupload(DisclosureSource existingSource,
                                                               MultipartFile file,
                                                               String fileHash) {
        log.info("실패한 파일 재업로드: sourceId={}, status={}",
                existingSource.getSourceId(), existingSource.getStatus());

        Long sourceId = existingSource.getSourceId();
        String filePath = existingSource.getFilePath();

       scheduleAsyncProcessing(sourceId, filePath);

        return DisclosureUploadResponse.builder()
                .sourceId(sourceId)
                .filePath(filePath)
                .fileSize(file.getSize())
                .fileHash(fileHash)
                .storageType(existingSource.getStorageType().name())
                .isDuplicate(false)
                .message("재업로드 성공 - 실패한 단계부터 재시작합니다.")
                .build();
    }

    /**
     * 중복 파일 응답 생성
     */
    private DisclosureUploadResponse buildDuplicateResponse(DisclosureSource existingSource) {
        log.info("Duplicate file detected: hash={}, existing sourceId={}, status={}",
                existingSource.getFileHash(), existingSource.getSourceId(), existingSource.getStatus());

        return DisclosureUploadResponse.builder()
                .sourceId(existingSource.getSourceId())
                .filePath(existingSource.getFilePath())
                .isDuplicate(true)
                .message("이미 업로드된 파일입니다.")
                .build();
    }

    /**
     * 새 파일 업로드 처리
     */
    private DisclosureUploadResponse handleNewFileUpload(MultipartFile file,
                                                          Long userId,
                                                          String stockId,
                                                          String fileHash) throws IOException {
        // 파일 저장
        String filePath = fileStorageService.storeFile(file, userId);
        DisclosureSource.StorageType storageType = determineStorageType();

        // 엔티티 생성 및 저장
        DisclosureSource source = createDisclosureSource(userId, stockId, filePath, fileHash,
                                                         file.getSize(), storageType);
        sourceRepository.save(source);

        // 비동기 처리 등록
        scheduleAsyncProcessing(source.getSourceId(), filePath);

        return buildUploadSuccessResponse(source, fileHash);
    }

    /**
     * DisclosureSource 엔티티 생성
     */
    private DisclosureSource createDisclosureSource(Long userId, String stockId, String filePath,
                                                     String fileHash, long fileSize,
                                                     DisclosureSource.StorageType storageType) {
        if (stockId == null || stockId.isEmpty()) {
            log.info("stockId 없이 문서 업로드: userId={}", userId);
        }

        return DisclosureSource.builder()
                .userId(userId)
                .stockId(stockId)
                .filePath(filePath)
                .fileHash(fileHash)
                .fileSize(fileSize)
                .status(ProcessStatus.PENDING.name())
                .storageType(storageType)
                .build();
    }

    /**
     * 업로드 성공 응답 생성
     */
    private DisclosureUploadResponse buildUploadSuccessResponse(DisclosureSource source, String fileHash) {
        return DisclosureUploadResponse.builder()
                .sourceId(source.getSourceId())
                .filePath(source.getFilePath())
                .fileSize(source.getFileSize())
                .fileHash(fileHash)
                .storageType(source.getStorageType().name())
                .isDuplicate(false)
                .message("업로드 성공")
                .build();
    }

    /**
     * 비동기 처리 스케줄링
     * DB 커밋 후 실행되도록 등록
     */
    private void scheduleAsyncProcessing(Long sourceId, String filePath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncProcessor.processDocumentAsync(sourceId, filePath);
            }
        });
    }


    /**
     * ID로 소스 파일 조회
     */
    public DisclosureSource getSource(Long sourceId) {
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));
    }

    /**
     * 소스 파일 삭제
     */
    @Transactional
    public void deleteSource(Long sourceId) throws IOException {
        DisclosureSource source = getSource(sourceId);

        // DB 삭제
        sourceRepository.deleteById(sourceId);

        // 파일 삭제
        fileStorageService.deleteFile(source.getFilePath());
        
        log.info("Deleted disclosure source: sourceId={}", sourceId);
    }

    /**
     * 파일 SHA-256 해시 계산
     */
    private String calculateFileHash(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 저장소 타입 결정 (설정 기반)
     */
    private DisclosureSource.StorageType determineStorageType() {
        return DisclosureSource.StorageType.LOCAL;
    }
}
