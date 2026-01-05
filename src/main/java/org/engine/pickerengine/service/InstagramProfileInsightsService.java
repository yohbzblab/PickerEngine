package org.engine.pickerengine.service;

import org.engine.pickerengine.dto.InstagramPost;
import org.engine.pickerengine.dto.InstagramProfile;
import org.engine.pickerengine.dto.InstagramProfileInsights;
import org.engine.pickerengine.dto.InstagramProfileWithPosts;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InstagramProfileInsightsService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

    private final InstagramService instagramService;

    public InstagramProfileInsightsService(InstagramService instagramService) {
        this.instagramService = instagramService;
    }

    public InstagramProfileInsights fetchInsights(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            return emptyInsights();
        }
        InstagramProfileWithPosts data = instagramService.fetchProfileWithPosts(normalized);
        InstagramProfile profile = data == null ? null : data.profile();
        List<InstagramPost> posts = data == null ? List.of() : data.posts();
        String accountId = instagramService.fetchAccountId(normalized);
        String resolvedUsername = profile != null && profile.username() != null && !profile.username().isBlank()
                ? profile.username()
                : normalized;
        String bio = profile == null ? null : profile.biography();
        String email = extractEmail(bio, profile == null ? null : profile.externalUrl());
        String profileLink = resolvedUsername.isBlank()
                ? null
                : "https://www.instagram.com/" + resolvedUsername + "/";
        List<String> categories = profile != null && profile.categoryName() != null && !profile.categoryName().isBlank()
                ? List.of(profile.categoryName())
                : List.of();
        Boolean hasLinks = detectLinkTreeOrYoutube(profile, bio);
        List<InstagramPost> sortedPosts = sortPostsByTakenAtDesc(posts);
        List<InstagramPost> recent9 = takeRecent(sortedPosts, 9);
        List<InstagramPost> recent18 = takeRecent(sortedPosts, 18);
        Double uploadFreq = computeUploadFreqPerWeek(sortedPosts);
        Double recentAvgViews = averageViews(recent9);
        Double recent18AvgViews = averageViews(recent18);
        Double pinnedAvgViews = averageTopViews(recent18, 3);
        Double recentAvgLikes = averageMetric(recent9, InstagramPost::likeCount);
        Double recentAvgComments = averageMetric(recent9, InstagramPost::commentCount);
        Double recentAvgShares = null;
        List<String> recentAds = detectRecentAds(recent18);
        String contactMethod = email != null && !email.isBlank() ? "email" : "dm";

        return new InstagramProfileInsights(
                profile,
                posts,
                accountId,
                email,
                profile == null ? null : profile.fullName(),
                bio,
                profile == null ? null : profile.followers(),
                profileLink,
                categories,
                hasLinks,
                uploadFreq,
                recentAvgViews,
                pinnedAvgViews,
                recent18AvgViews,
                recentAds,
                contactMethod,
                recentAvgComments,
                recentAvgLikes,
                recentAvgShares);
    }

    private InstagramProfileInsights emptyInsights() {
        return new InstagramProfileInsights(
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null);
    }

    private String extractEmail(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String email = findEmail(candidate);
            if (email != null) {
                return email;
            }
        }
        return null;
    }

    private String findEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private Boolean detectLinkTreeOrYoutube(InstagramProfile profile, String bio) {
        if (containsLinkKeyword(profile == null ? null : profile.externalUrl())) {
            return true;
        }
        return containsLinkKeyword(bio);
    }

    private boolean containsLinkKeyword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("linktr.ee")
                || lower.contains("youtube.com")
                || lower.contains("youtu.be");
    }

    private List<InstagramPost> sortPostsByTakenAtDesc(List<InstagramPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<InstagramPost> sorted = new ArrayList<>(posts);
        sorted.sort((left, right) -> {
            Instant leftTime = parseInstant(left == null ? null : left.takenAt());
            Instant rightTime = parseInstant(right == null ? null : right.takenAt());
            if (leftTime == null && rightTime == null) {
                return 0;
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });
        return sorted;
    }

    private List<InstagramPost> takeRecent(List<InstagramPost> posts, int limit) {
        if (posts == null || posts.isEmpty() || limit <= 0) {
            return List.of();
        }
        return posts.subList(0, Math.min(limit, posts.size()));
    }

    private Double computeUploadFreqPerWeek(List<InstagramPost> posts) {
        if (posts == null || posts.size() < 2) {
            return null;
        }
        List<Instant> instants = new ArrayList<>();
        for (InstagramPost post : posts) {
            Instant instant = parseInstant(post == null ? null : post.takenAt());
            if (instant != null) {
                instants.add(instant);
            }
        }
        if (instants.size() < 2) {
            return null;
        }
        instants.sort(Comparator.naturalOrder());
        Instant earliest = instants.get(0);
        Instant latest = instants.get(instants.size() - 1);
        long days = Duration.between(earliest, latest).toDays();
        if (days < 1) {
            days = 1;
        }
        double weeks = days / 7.0;
        return roundTwoDecimals(instants.size() / weeks);
    }

    private Double averageViews(List<InstagramPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return null;
        }
        long sum = 0;
        for (InstagramPost post : posts) {
            Integer views = post == null ? null : post.videoViewCount();
            sum += views == null ? 0 : views;
        }
        return roundTwoDecimals(sum / (double) posts.size());
    }

    private Double averageMetric(List<InstagramPost> posts, java.util.function.Function<InstagramPost, Integer> extractor) {
        if (posts == null || posts.isEmpty()) {
            return null;
        }
        long sum = 0;
        for (InstagramPost post : posts) {
            if (post == null) {
                continue;
            }
            Integer value = extractor.apply(post);
            sum += value == null ? 0 : value;
        }
        return roundTwoDecimals(sum / (double) posts.size());
    }

    private Double averageTopViews(List<InstagramPost> posts, int topN) {
        if (posts == null || posts.isEmpty() || topN <= 0) {
            return null;
        }
        List<Integer> values = new ArrayList<>();
        for (InstagramPost post : posts) {
            Integer views = post == null ? null : post.videoViewCount();
            values.add(views == null ? 0 : views);
        }
        values.sort(Comparator.reverseOrder());
        int size = Math.min(topN, values.size());
        long sum = 0;
        for (int i = 0; i < size; i++) {
            sum += values.get(i);
        }
        return roundTwoDecimals(sum / (double) size);
    }

    private List<String> detectRecentAds(List<InstagramPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (InstagramPost post : posts) {
            if (post == null) {
                continue;
            }
            if (!containsAdKeyword(post.caption())) {
                continue;
            }
            String permalink = post.permalink();
            if (permalink != null && !permalink.isBlank()) {
                results.add(permalink);
            } else if (post.postId() != null && !post.postId().isBlank()) {
                results.add(post.postId());
            }
        }
        return results;
    }

    private boolean containsAdKeyword(String caption) {
        if (caption == null || caption.isBlank()) {
            return false;
        }
        String lower = caption.toLowerCase(Locale.ROOT);
        return lower.contains("#ad")
                || lower.contains("#sponsored")
                || lower.contains("sponsored")
                || lower.contains("paid partnership")
                || lower.contains("광고")
                || lower.contains("협찬")
                || lower.contains("유료광고")
                || lower.contains("유료 광고")
                || lower.contains("스폰서");
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
