package org.engine.pickerengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.engine.pickerengine.dto.InstagramProfile;
import org.engine.pickerengine.dto.InstagramProfileInsights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class InstagramInfluencerSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstagramInfluencerSyncService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SELECT_SQL = """
            SELECT username
            FROM influencer
            WHERE platform = 'instagram'
              AND priority_tier = 'A'
              AND username IS NOT NULL
              AND username <> ''
            ORDER BY username
            LIMIT ? OFFSET ?
            """;
    private static final String UPDATE_SQL = """
            UPDATE influencer
            SET "accountId" = ?,
                email = ?,
                name = ?,
                bio = ?,
                followers = ?,
                "profileLink" = ?,
                categories = ?,
                "hasLinks" = ?,
                "uploadFreq" = ?,
                "recentAvgViews" = ?,
                "captureLinks" = ?,
                "pinnedAvgViews" = ?,
                "recent18AvgViews" = ?,
                "recentAds" = ?,
                "contactMethod" = ?,
                updated_at = now()
            WHERE platform = 'instagram'
              AND priority_tier = 'A'
              AND username = ?
            """;
    private static final String CREATE_STATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS sync_state (
                state_key text PRIMARY KEY,
                state_value text NOT NULL,
                updated_at timestamptz NOT NULL DEFAULT now()
            )
            """;
    private static final String SELECT_STATE_SQL = """
            SELECT state_value
            FROM sync_state
            WHERE state_key = ?
            """;
    private static final String UPSERT_STATE_SQL = """
            INSERT INTO sync_state (state_key, state_value, updated_at)
            VALUES (?, ?, now())
            ON CONFLICT (state_key)
            DO UPDATE SET state_value = EXCLUDED.state_value, updated_at = now()
            """;
    private static final String OFFSET_STATE_KEY = "instagram_influencer_sync_offset";

    private final DataSource dataSource;
    private final InstagramProfileInsightsService insightsService;
    private final int batchSize;
    private final long rateLimitMs;
    private final int maxRetries;
    private final long retryDelayMs;
    private final AtomicInteger offset = new AtomicInteger(0);
    private final AtomicBoolean offsetInitialized = new AtomicBoolean(false);
    private final Object offsetLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicInteger patchSuccessCount = new AtomicInteger(0);
    private final AtomicLong lastRunStarted = new AtomicLong(0);
    private final AtomicLong lastRunFinished = new AtomicLong(0);
    private final AtomicInteger lastRunUpdated = new AtomicInteger(0);
    private final AtomicInteger lastRunBatches = new AtomicInteger(0);
    private final AtomicInteger lastRunFetched = new AtomicInteger(0);
    private final AtomicReference<String> lastRunMode = new AtomicReference<>("");
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    public InstagramInfluencerSyncService(
            DataSource dataSource,
            InstagramProfileInsightsService insightsService,
            @Value("${instagram.influencer-sync.batch-size:200}") int batchSize,
            @Value("${instagram.influencer-sync.rate-limit-ms:200}") long rateLimitMs,
            @Value("${instagram.influencer-sync.max-retries:1}") int maxRetries,
            @Value("${instagram.influencer-sync.retry-delay-ms:1000}") long retryDelayMs) {
        this.dataSource = dataSource;
        this.insightsService = insightsService;
        this.batchSize = Math.max(1, batchSize);
        this.rateLimitMs = Math.max(0, rateLimitMs);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryDelayMs = Math.max(0, retryDelayMs);
    }

    public int syncNextBatch() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Instagram influencer sync already running; skip this cycle.");
            return 0;
        }
        ensureOffsetInitialized();
        lastRunMode.set("batch");
        lastRunStarted.set(System.currentTimeMillis());
        lastRunFinished.set(0);
        lastRunUpdated.set(0);
        lastRunBatches.set(0);
        lastRunFetched.set(0);
        patchSuccessCount.set(0);
        lastError.set(null);
        try {
            BatchResult result = syncBatchInternal();
            lastRunUpdated.set(result.updated());
            lastRunBatches.set(result.fetched() > 0 ? 1 : 0);
            lastRunFetched.set(result.fetched());
            return result.updated();
        } catch (RuntimeException exception) {
            lastError.set(exception.getMessage());
            LOGGER.warn("Instagram influencer sync batch failed", exception);
            throw exception;
        } finally {
            stopRequested.set(false);
            lastRunFinished.set(System.currentTimeMillis());
            running.set(false);
        }
    }

    public boolean requestStop() {
        stopRequested.set(true);
        return running.get();
    }

    public boolean triggerRunAllAsync() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Thread worker = new Thread(this::runAllInternal, "instagram-influencer-run-all");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    public SyncStatus getStatus() {
        ensureOffsetInitialized();
        return new SyncStatus(
                running.get(),
                lastRunMode.get(),
                formatEpochMillis(lastRunStarted.get()),
                formatEpochMillis(lastRunFinished.get()),
                lastRunUpdated.get(),
                lastRunBatches.get(),
                lastError.get(),
                offset.get(),
                lastRunFetched.get());
    }

    private void runAllInternal() {
        ensureOffsetInitialized();
        lastRunMode.set("run-all");
        lastRunStarted.set(System.currentTimeMillis());
        lastRunFinished.set(0);
        lastRunUpdated.set(0);
        lastRunBatches.set(0);
        lastRunFetched.set(0);
        patchSuccessCount.set(0);
        lastError.set(null);
        int updatedTotal = 0;
        int batches = 0;
        int fetchedTotal = 0;
        try {
            while (true) {
                if (stopRequested.get()) {
                    break;
                }
                BatchResult result = syncBatchInternal();
                if (result.fetched() == 0) {
                    break;
                }
                batches += 1;
                fetchedTotal += result.fetched();
                updatedTotal += result.updated();
                if (stopRequested.get()) {
                    break;
                }
            }
        } catch (Exception exception) {
            lastError.set(exception.getMessage());
            LOGGER.warn("Instagram influencer run-all failed", exception);
        } finally {
            lastRunUpdated.set(updatedTotal);
            lastRunBatches.set(batches);
            lastRunFetched.set(fetchedTotal);
            lastRunFinished.set(System.currentTimeMillis());
            stopRequested.set(false);
            running.set(false);
        }
    }

    private BatchResult syncBatchInternal() {
        if (stopRequested.get()) {
            return new BatchResult(0, 0);
        }
        int startOffset = Math.max(0, offset.get());
        List<String> usernames = fetchUsernames(startOffset, batchSize);
        if (usernames.isEmpty()) {
            offset.set(0);
            persistOffset(0);
            return new BatchResult(0, 0);
        }
        int updated = 0;
        int processed = 0;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(UPDATE_SQL)) {
            for (String username : usernames) {
                if (stopRequested.get()) {
                    break;
                }
                processed += 1;
                UpdatePayload payload = buildPayloadWithRetry(username);
                if (payload == null) {
                    continue;
                }
                bindUpdate(update, payload);
                int applied = update.executeUpdate();
                if (applied > 0) {
                    int successTotal = patchSuccessCount.incrementAndGet();
                    LOGGER.info("Patched influencer {} (patchSuccessCount={})", username, successTotal);
                }
                updated += applied;
                sleepRateLimit();
            }
        } catch (SQLException exception) {
            LOGGER.warn("Instagram influencer sync failed", exception);
        }

        int nextOffset = startOffset + processed;
        boolean processedAll = processed >= usernames.size();
        if (!processedAll) {
            offset.set(nextOffset);
            persistOffset(nextOffset);
        } else if (usernames.size() < batchSize) {
            offset.set(0);
            persistOffset(0);
        } else {
            offset.set(nextOffset);
            persistOffset(nextOffset);
        }
        return new BatchResult(processed, updated);
    }

    private List<String> fetchUsernames(int startOffset, int limit) {
        List<String> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setInt(1, limit);
            statement.setInt(2, startOffset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String username = resultSet.getString(1);
                    if (username != null && !username.isBlank()) {
                        results.add(username.trim());
                    }
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to fetch influencer usernames", exception);
        }
        return results;
    }

    private void ensureOffsetInitialized() {
        if (offsetInitialized.get()) {
            return;
        }
        synchronized (offsetLock) {
            if (offsetInitialized.get()) {
                return;
            }
            try (Connection connection = dataSource.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(CREATE_STATE_TABLE_SQL);
                }
                try (PreparedStatement statement = connection.prepareStatement(SELECT_STATE_SQL)) {
                    statement.setString(1, OFFSET_STATE_KEY);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            Integer loaded = parseOffset(resultSet.getString(1));
                            if (loaded != null && loaded >= 0) {
                                offset.set(loaded);
                            }
                        }
                    }
                }
                offsetInitialized.set(true);
            } catch (SQLException exception) {
                LOGGER.warn("Failed to load instagram influencer sync offset", exception);
            }
        }
    }

    private void persistOffset(int value) {
        int safeValue = Math.max(0, value);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_STATE_SQL)) {
            statement.setString(1, OFFSET_STATE_KEY);
            statement.setString(2, Integer.toString(safeValue));
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to persist instagram influencer sync offset", exception);
        }
    }

    private UpdatePayload buildPayloadWithRetry(String lookupUsername) {
        int attempts = Math.max(1, maxRetries + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            UpdatePayload payload = buildPayloadOnce(lookupUsername);
            if (payload != null) {
                return payload;
            }
            if (attempt < attempts) {
                LOGGER.info("Retrying instagram fetch (attempt {}/{}) for {}", attempt + 1, attempts, lookupUsername);
                sleepRetryDelay();
            }
        }
        return null;
    }

    private UpdatePayload buildPayloadOnce(String lookupUsername) {
        try {
            InstagramProfileInsights insights = insightsService.fetchInsights(lookupUsername);
            InstagramProfile profile = insights == null ? null : insights.profile();
            if (profile == null) {
                return null;
            }
            Long accountId = parseLong(insights.accountId());
            Long followers = toLong(insights.followers());
            Long recentAvgViews = toLong(insights.recentAvgViews());
            Long pinnedAvgViews = toLong(insights.pinnedAvgViews());
            Long recent18AvgViews = toLong(insights.recent18AvgViews());
            String categories = toJson(insights.categories());
            String recentAds = toJson(insights.recentAds());
            String uploadFreq = formatDouble(insights.uploadFreq());
            String captureLinks = profile.externalUrl();
            LOGGER.info(
                    "Fetched instagram profile {} (accountId={}, followers={}, email={}, category={}, hasLinks={}, link={})",
                    lookupUsername,
                    accountId,
                    followers,
                    insights.email(),
                    profile.categoryName(),
                    insights.hasLinks(),
                    insights.profileLink());
            return new UpdatePayload(
                    lookupUsername,
                    accountId,
                    insights.email(),
                    insights.name(),
                    insights.bio(),
                    followers,
                    insights.profileLink(),
                    categories,
                    insights.hasLinks(),
                    uploadFreq,
                    recentAvgViews,
                    captureLinks,
                    pinnedAvgViews,
                    recent18AvgViews,
                    recentAds,
                    insights.contactMethod());
        } catch (Exception exception) {
            LOGGER.warn("Failed to build influencer payload for {}", lookupUsername, exception);
            return null;
        }
    }

    private void bindUpdate(PreparedStatement statement, UpdatePayload payload) throws SQLException {
        bindLong(statement, 1, payload.accountId);
        statement.setString(2, payload.email);
        statement.setString(3, payload.name);
        statement.setString(4, payload.bio);
        bindLong(statement, 5, payload.followers);
        statement.setString(6, payload.profileLink);
        statement.setString(7, payload.categories);
        statement.setObject(8, payload.hasLinks, Types.BOOLEAN);
        statement.setString(9, payload.uploadFreq);
        bindLong(statement, 10, payload.recentAvgViews);
        statement.setString(11, payload.captureLinks);
        bindLong(statement, 12, payload.pinnedAvgViews);
        bindLong(statement, 13, payload.recent18AvgViews);
        statement.setString(14, payload.recentAds);
        statement.setString(15, payload.contactMethod);
        statement.setString(16, payload.lookupUsername);
    }

    private void bindLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseOffset(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private Long toLong(Double value) {
        return value == null ? null : Math.round(value);
    }

    private String formatDouble(Double value) {
        if (value == null) {
            return null;
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String toJson(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (Exception ignored) {
            return values.toString();
        }
    }

    private void sleepRateLimit() {
        if (rateLimitMs <= 0) {
            return;
        }
        long delay = ThreadLocalRandom.current().nextLong(rateLimitMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepRetryDelay() {
        if (retryDelayMs <= 0) {
            return;
        }
        long delay = ThreadLocalRandom.current().nextLong(retryDelayMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class UpdatePayload {
        private final String lookupUsername;
        private final Long accountId;
        private final String email;
        private final String name;
        private final String bio;
        private final Long followers;
        private final String profileLink;
        private final String categories;
        private final Boolean hasLinks;
        private final String uploadFreq;
        private final Long recentAvgViews;
        private final String captureLinks;
        private final Long pinnedAvgViews;
        private final Long recent18AvgViews;
        private final String recentAds;
        private final String contactMethod;

        private UpdatePayload(
                String lookupUsername,
                Long accountId,
                String email,
                String name,
                String bio,
                Long followers,
                String profileLink,
                String categories,
                Boolean hasLinks,
                String uploadFreq,
                Long recentAvgViews,
                String captureLinks,
                Long pinnedAvgViews,
                Long recent18AvgViews,
                String recentAds,
                String contactMethod) {
            this.lookupUsername = lookupUsername;
            this.accountId = accountId;
            this.email = email;
            this.name = name;
            this.bio = bio;
            this.followers = followers;
            this.profileLink = profileLink;
            this.categories = categories;
            this.hasLinks = hasLinks;
            this.uploadFreq = uploadFreq;
            this.recentAvgViews = recentAvgViews;
            this.captureLinks = captureLinks;
            this.pinnedAvgViews = pinnedAvgViews;
            this.recent18AvgViews = recent18AvgViews;
            this.recentAds = recentAds;
            this.contactMethod = contactMethod;
        }
    }

    public record SyncStatus(
            boolean running,
            String mode,
            String lastRunStartedAt,
            String lastRunFinishedAt,
            int lastRunUpdated,
            int lastRunBatches,
            String lastError,
            int offset,
            int lastRunFetched
    ) {
    }

    private record BatchResult(int fetched, int updated) {
    }

    private String formatEpochMillis(long value) {
        if (value <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(value).toString();
    }
}
