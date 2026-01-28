package com.monstersinc.stock101.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 파일 다운로드 유틸리티
 */
@Slf4j
public final class FileDownloadUtil {

    private FileDownloadUtil() {}

    private static final int DEFAULT_TIMEOUT_MS = 30000; // 30초
    private static final int BUFFER_SIZE = 8192;

    /**
     * 원격 파일 다운로드
     * 
     * @param urlStr     다운로드 URL
     * @param outputFile 저장할 파일 경로
     */
    public static void download(String urlStr, File outputFile) throws IOException {
        download(urlStr, outputFile, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 원격 파일 다운로드
     * 
     * @param urlStr     다운로드 URL
     * @param outputFile 저장할 파일 경로
     * @param timeoutMs  연결/읽기 타임아웃(ms)
     */
    public static void download(String urlStr, File outputFile, int timeoutMs) throws IOException {
        URL url = new URL(urlStr);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);

        // 부모 디렉토리 생성
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            log.info("Downloaded {} bytes to {}", totalBytes, outputFile.getAbsolutePath());
        }
    }

    /**
     * 원격 파일 다운로드 (Path 버전)
     */
    public static void download(String urlStr, Path outputPath) throws IOException {
        download(urlStr, outputPath.toFile(), DEFAULT_TIMEOUT_MS);
    }

    /**
     * 원격 파일 다운로드 (Path 버전)
     */
    public static void download(String urlStr, Path outputPath, int timeoutMs) throws IOException {
        download(urlStr, outputPath.toFile(), timeoutMs);
    }

    /**
     * 디렉토리 생성 (없으면)
     */
    public static Path ensureDirectory(Path dirPath) throws IOException {
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            log.debug("Created directory: {}", dirPath);
        }
        return dirPath;
    }

    /**
     * 파일 복사
     */
    public static void copyFile(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Copied {} to {}", source, target);
    }

    /**
     * 파일 삭제 (존재하면)
     */
    public static boolean deleteIfExists(Path path) throws IOException {
        boolean deleted = Files.deleteIfExists(path);
        if (deleted) {
            log.debug("Deleted file: {}", path);
        }
        return deleted;
    }

    /**
     * 파일 삭제 (존재하면)
     */
    public static boolean deleteIfExists(File file) {
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.debug("Deleted file: {}", file.getAbsolutePath());
            }
            return deleted;
        }
        return false;
    }
}
