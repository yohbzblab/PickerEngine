package org.engine.pickerengine.service;

import org.engine.pickerengine.dto.InstagramKeywordResponse;
import org.engine.pickerengine.entity.InstagramKeywordCacheEntity;
import org.engine.pickerengine.repository.InstagramKeywordCacheRepository;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class InstagramKeywordCacheService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final InstagramKeywordCacheRepository repository;

    public InstagramKeywordCacheService(InstagramKeywordCacheRepository repository) {
        this.repository = repository;
    }

    public Optional<InstagramKeywordResponse> findCached(String username, String promptVersion) {
        if (username == null || username.isBlank() || promptVersion == null || promptVersion.isBlank()) {
            return Optional.empty();
        }
        return repository.findByUsernameAndPromptVersion(username, promptVersion)
                .map(this::toResponse);
    }

    public Optional<InstagramKeywordResponse> findFreshCached(
            String username,
            String promptVersion,
            LocalDateTime threshold) {
        if (threshold == null) {
            return findCached(username, promptVersion);
        }
        return repository.findByUsernameAndPromptVersion(username, promptVersion)
                .filter(entity -> entity.getUpdatedAt() != null && entity.getUpdatedAt().isAfter(threshold))
                .map(this::toResponse);
    }

    public void save(String username, String promptVersion, InstagramKeywordResponse response) {
        if (username == null || username.isBlank() || promptVersion == null || promptVersion.isBlank()) {
            return;
        }
        if (response == null || (response.keywords().isEmpty() && response.category().isEmpty())) {
            return;
        }
        InstagramKeywordCacheEntity entity = repository
                .findByUsernameAndPromptVersion(username, promptVersion)
                .orElseGet(() -> new InstagramKeywordCacheEntity(username, promptVersion));
        entity.setKeywords(toJson(response.keywords()));
        entity.setCategories(toJson(response.category()));
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private String toJson(List<String> values) {
        List<String> safe = values == null ? List.of() : values;
        try {
            return OBJECT_MAPPER.writeValueAsString(safe);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    String text = item.asText();
                    if (text != null && !text.isBlank()) {
                        values.add(text);
                    }
                }
            }
            return values;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private InstagramKeywordResponse toResponse(InstagramKeywordCacheEntity entity) {
        return new InstagramKeywordResponse(
                parseList(entity.getKeywords()),
                parseList(entity.getCategories()));
    }
}
