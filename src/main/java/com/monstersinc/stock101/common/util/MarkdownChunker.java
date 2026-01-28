package com.monstersinc.stock101.common.util;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownChunker {

    /**
     * 마크다운 헤더(##, ###)를 기준으로 텍스트를 분할합니다.
     */
    public List<String> splitByHierarchy(String markdown) {
        List<String> chunks = new ArrayList<>();

        // 헤더를 기준으로 분할 (## 또는 ### 으로 시작하는 라인)
        // 긍정형 전방 탐색(?=...)을 사용하여 구분자인 헤더를 보존하며 나눕니다.
        String[] rawSections = markdown.split("(?=\\n## )|(?=\\n### )");

        StringBuilder currentChunk = new StringBuilder();
        int maxChunkSize = 2000; // 한 청크당 최대 권장 글자 수

        for (String section : rawSections) {
            if (section.isBlank()) continue;

            // 현재 청크에 추가했을 때 너무 커지면 지금까지의 내용을 저장하고 새로 시작
            if (currentChunk.length() + section.length() > maxChunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }

            currentChunk.append(section).append("\n");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    public String extractParent(String chunk) {
        // "## 제목" 형태를 찾음 (대제목)
        Pattern pattern = Pattern.compile("^##\\s+(.*)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(chunk);

        if (matcher.find()) {
            return cleanTitle(matcher.group(1));
        }

        // 만약 ##이 없고 ###만 있다면 그것을 부모 제목으로 간주
        return extractSub(chunk);
    }

    /**
     * 청크 내의 하위 제목(###)을 추출하여 소주제로 결정합니다.
     */
    public String extractSub(String chunk) {
        // "### 제목" 형태를 찾음 (소제목)
        Pattern pattern = Pattern.compile("^###\\s+(.*)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(chunk);

        if (matcher.find()) {
            return cleanTitle(matcher.group(1));
        }

        // 소제목이 없으면 대제목을 한 번 더 찾음
        Pattern parentPattern = Pattern.compile("^##\\s+(.*)$", Pattern.MULTILINE);
        Matcher parentMatcher = parentPattern.matcher(chunk);
        if (parentMatcher.find()) {
            return cleanTitle(parentMatcher.group(1));
        }

        return "세부 내용"; // 아무 제목도 없을 경우
    }

    /**
     * 마크다운 특수문자나 불필요한 공백을 제거합니다.
     */
    private String cleanTitle(String title) {
        if (title == null) return "";
        // 끝에 붙은 마크다운 기호나 줄바꿈 제거
        return title.replaceAll("[#*`]", "").trim();
    }

}