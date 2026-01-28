package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.disclosure.config.DisclosureProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 파일 저장/로드 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final DisclosureProperties disclosureProperties;

    /**
     * 파일 저장 (DART 접수번호 기준)
     * 
     * @param file    업로드된 파일
     * @param userId  사용자 ID
     * @return 저장된 파일 경로
     */
    public String storeFile(MultipartFile file, Long userId) throws IOException {
        // 파일 검증
        validateFile(file);

        // 저장 디렉토리 생성
        Path uploadPath = Paths.get(disclosureProperties.getUploadDir(), String.valueOf(userId));
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 고유한 파일명 생성
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

        // 파일 저장
        Path targetPath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return targetPath.toString();
    }

    /**
     * 파일 검증
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }

        // 파일 크기 검증
        if (file.getSize() > disclosureProperties.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    String.format("파일 크기가 너무 큽니다. 최대 크기: %d MB",
                            disclosureProperties.getMaxFileSize() / (1024 * 1024)));
        }

        // 파일 확장자 검증
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("PDF 파일만 업로드 가능합니다.");
        }
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex);
    }

    /**
     * 파일 삭제
     */
    public void deleteFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            Files.delete(path);
            log.info("File deleted: {}", filePath);
        }
    }

    /**
     * 파일 존재 여부 확인
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
}
