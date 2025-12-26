package org.engine.pickerengine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class InstagramDmPromptService {

    private static final String DEFAULT_TEMPLATE = """
            너너는 인스타그램 협업/관심 DM을 작성하는 전문가다.

            [입력 정보]
            - mood_keywords: {{MOOD_KEYWORDS}}
            - content_keywords: {{CONTENT_KEYWORDS}}
            - tone_keywords: {{TONE_KEYWORDS}}
            - 계정 요약 문장: {{IMPRESSION_SUMMARY}}

            [작성 목표]
            - 상대가 “내 계정을 제대로 보고 보낸 메시지”라고 느끼게 한다.

            [작성 규칙]
            1. 키워드를 직접 나열하지 말고 자연스럽게 문장에 흡수할 것
            2. 첫 문장은 가벼운 관찰 또는 인상으로 시작
            3. 칭찬은 구체적으로, 감탄사는 최소화
            4. 광고·협업 제안처럼 보이지 않게 작성
            5. 전체 3~5문장, DM에 어울리는 길이
            6. 존댓말, 부드럽고 인간적인 톤 유지

            [출력 형식]
            - 인스타그램 DM으로 바로 보낼 수 있는 단일 메시지
            """;
    private static final String DEFAULT_VERSIONS = "v1";

    private final List<String> availableVersions;

    public InstagramDmPromptService(
            @Value("${instagram.dm-versions:" + DEFAULT_VERSIONS + "}") String versions) {
        this.availableVersions = parseVersions(versions);
    }

    public String loadTemplateRaw(String version) {
        return loadTemplate(version);
    }

    public List<String> listVersions() {
        return List.copyOf(availableVersions);
    }

    private String loadTemplate(String version) {
        String path = "prompts/instagram_dm_" + version + ".txt";
        try (InputStream stream = InstagramDmPromptService.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return DEFAULT_TEMPLATE;
            }
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return template.isBlank() ? DEFAULT_TEMPLATE : template;
        } catch (Exception ignored) {
            return DEFAULT_TEMPLATE;
        }
    }

    private List<String> parseVersions(String versions) {
        if (versions == null || versions.isBlank()) {
            return List.of("v1");
        }
        String[] parts = versions.split(",");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        if (list.isEmpty()) {
            list.add("v1");
        }
        return list;
    }
}
