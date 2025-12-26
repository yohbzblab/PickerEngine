package org.engine.pickerengine.dto;

public record InstagramDmRequest(String userId, String version, String dmVersion, Boolean ignoreCache) {
    public boolean ignoreCacheOrDefault() {
        return ignoreCache != null && ignoreCache;
    }

    public String dmVersionOrDefault() {
        return dmVersion == null || dmVersion.isBlank() ? null : dmVersion;
    }
}
