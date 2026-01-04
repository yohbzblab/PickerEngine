package org.engine.pickerengine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class InstagramPricePromptService {

    private static final String DEFAULT_TEMPLATE = """
            You are a data extraction engine. Extract pricing information from the input text and image.

            Input text:
            {{input_text}}

            Return JSON ONLY using this schema:
            {
              "unit": {
                "currency": "KRW",
                "amount_unit": "",
                "vat": "excluded|included|unknown"
              },
              "items": [
                {
                  "platform": "",
                  "product": "",
                  "price": null,
                  "price_text": "",
                  "details": [],
                  "notes": []
                }
              ],
              "global_notes": []
            }

            Rules:
            - Prefer values from the image when they disagree with the text.
            - Keep original labels and language as seen in the source.
            - price is a number without commas when possible; use null if unknown.
            - price_text should preserve the raw price cell as seen.
            - Put table bullet points into details, column notes into notes, and footnotes into global_notes.
            - Output valid JSON only. No markdown or commentary.
            """;
    private static final String DEFAULT_VERSIONS = "v1";

    private final List<String> availableVersions;

    public InstagramPricePromptService(
            @Value("${instagram.price-versions:" + DEFAULT_VERSIONS + "}") String versions) {
        this.availableVersions = parseVersions(versions);
    }

    public String buildPromptFromTemplate(String inputText, String template) {
        String resolvedTemplate = template;
        if (resolvedTemplate == null || resolvedTemplate.isBlank()) {
            resolvedTemplate = DEFAULT_TEMPLATE;
        }
        String resolvedText = inputText == null ? "" : inputText.trim();
        if (resolvedText.isBlank()) {
            resolvedText = "(empty)";
        }
        return resolvedTemplate.replace("{{input_text}}", resolvedText);
    }

    public String loadTemplateRaw(String version) {
        return loadTemplate(version);
    }

    public List<String> listVersions() {
        return List.copyOf(availableVersions);
    }

    private String loadTemplate(String version) {
        String path = "prompts/instagram_prices_" + version + ".txt";
        try (InputStream stream = InstagramPricePromptService.class.getClassLoader().getResourceAsStream(path)) {
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
