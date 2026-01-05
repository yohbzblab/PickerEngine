package org.engine.pickerengine.dto;

import java.util.List;

public record InstagramProfileInsights(
        InstagramProfile profile,
        List<InstagramPost> posts,
        String accountId,
        String email,
        String name,
        String bio,
        Integer followers,
        String profileLink,
        List<String> categories,
        Boolean hasLinks,
        Double uploadFreq,
        Double recentAvgViews,
        Double pinnedAvgViews,
        Double recent18AvgViews,
        List<String> recentAds,
        String contactMethod,
        Double recentAvgComments,
        Double recentAvgLikes,
        Double recentAvgShares
) {
}
