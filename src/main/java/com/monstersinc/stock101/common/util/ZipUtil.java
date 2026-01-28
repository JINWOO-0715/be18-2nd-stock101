package com.monstersinc.stock101.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 파일 다운로드 및 해제 유틸리티
 */
@Slf4j
public final class ZipUtil {

    private ZipUtil() {}

    /**
     * 원격 ZIP(또는 일반 파일) 다운로드
     * @param urlStr 다운로드 URL
     * @param outputFile 저장할 파일 경로
     * @param timeoutMs 연결/읽기 타임아웃(ms)
     */
    public static void downloadFile(String urlStr, File outputFile, int timeoutMs) throws IOException {
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
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * ZIP 파일을 지정된 폴더로 모두 해제
     * @param zipFile ZIP 파일
     * @param destDir 해제 대상 폴더
     * @return 해제된 파일 목록
     */
    public static List<File> extractAll(File zipFile, Path destDir) throws IOException {
        Objects.requireNonNull(zipFile, "zipFile");
        Objects.requireNonNull(destDir, "destDir");
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        List<File> extracted = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = destDir.resolve(entry.getName()).toFile();
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    extracted.add(outFile);
                }
            }
        }
        return extracted;
    }

    /**
     * ZIP 파일에서 특정 확장자와 일치하는 첫 번째 파일만 추출
     * @param zipFile ZIP 파일
     * @param extension 확장자 (예: ".mst", ".xml")
     * @param destDir 추출 대상 폴더
     * @return 추출된 파일, 없으면 null
     */
    public static File extractFirstByExtension(File zipFile, String extension, Path destDir) throws IOException {
        Objects.requireNonNull(zipFile, "zipFile");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(destDir, "destDir");
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(extension.toLowerCase())) {
                    File outFile = destDir.resolve(entry.getName()).toFile();
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    return outFile;
                }
            }
        }
        return null;
    }
}
