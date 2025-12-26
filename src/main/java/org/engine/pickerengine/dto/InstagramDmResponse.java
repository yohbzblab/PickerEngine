package org.engine.pickerengine.dto;

import java.util.List;

public record InstagramDmResponse(
        String message,
        List<String> moodKeywords,
        List<String> contentKeywords,
        List<String> toneKeywords,
        String impressionSummary
) {
}
