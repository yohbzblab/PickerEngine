package org.engine.pickerengine.scheduler;

import org.engine.pickerengine.service.InstagramInfluencerSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InstagramInfluencerSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstagramInfluencerSyncScheduler.class);

    private final InstagramInfluencerSyncService syncService;
    private final boolean enabled;

    public InstagramInfluencerSyncScheduler(
            InstagramInfluencerSyncService syncService,
            @Value("${instagram.influencer-sync.enabled:true}") boolean enabled) {
        this.syncService = syncService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${instagram.influencer-sync.delay-ms:60000}",
            initialDelayString = "${instagram.influencer-sync.initial-delay-ms:10000}")
    public void runBatch() {
        if (!enabled) {
            return;
        }
        int updated = syncService.syncNextBatch();
        LOGGER.info("Instagram influencer sync batch done (updated={})", updated);
    }
}
