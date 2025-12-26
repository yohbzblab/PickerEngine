package org.engine.pickerengine.dto;

public record InstagramDmRequest(String userId, String version, Boolean ignoreCache) {
    public boolean ignoreCacheOrDefault() {
        return ignoreCache != null && ignoreCache;
    }
}
