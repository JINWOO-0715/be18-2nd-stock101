package com.monstersinc.stock101.disclosure.service;

import com.monstersinc.stock101.common.service.DocumentAsyncProcessor;
import com.monstersinc.stock101.common.service.FileStorageService;
import com.monstersinc.stock101.disclosure.domain.DisclosureSource;
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
     * @return 업로드된 소스 파일 정보
     */
    @Transactional
    public DisclosureUploadResponse uploadDocument(MultipartFile file,  Long userID) throws IOException {
        // 1. 파일 해시 계산 (중복 체크)
        String fileHash = calculateFileHash(file);

        // 2. 중복 체크
        Optional<DisclosureSource> existing = sourceRepository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            log.info("Duplicate file detected: hash={}, existing sourceId={}", fileHash, existing.get().getSourceId());
            return DisclosureUploadResponse.builder()
                    .sourceId(existing.get().getSourceId())
                    .filePath(existing.get().getFilePath())
                    .isDuplicate(true)
                    .message("이미 업로드된 파일입니다.")
                    .build();
        }

        // 3. 파일 저장 (S3 또는 로컬)
        String filePath = fileStorageService.storeFile(file, userID);

        // 4. 저장소 타입 결정 (설정에 따라)
        DisclosureSource.StorageType storageType = determineStorageType();

        // 5. 엔티티 생성 및 저장
        DisclosureSource source = DisclosureSource.builder()
                .userId(userID)
                .filePath(filePath)
                .fileHash(fileHash)
                .fileSize(file.getSize())
                .status("PENDING")
                .storageType(storageType)
                .build();

        sourceRepository.save(source);

        // DB 커밋이 완료된 후에 비동기 로직을 실행하도록 등록
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // DB 커밋이 성공한 직후 이 로직이 실행됩니다.
                asyncProcessor.processDocumentAsync(source.getSourceId(), filePath);
            }
        });

        return DisclosureUploadResponse.builder()
                .sourceId(source.getSourceId())
                .filePath(filePath)
                .fileSize(file.getSize())
                .fileHash(fileHash)
                .storageType(storageType.name())
                .isDuplicate(false)
                .message("업로드 성공")
                .build();
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
