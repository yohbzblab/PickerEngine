package org.engine.pickerengine.runner;

import org.engine.pickerengine.service.InstagramInfluencerSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class InstagramInfluencerSyncRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstagramInfluencerSyncRunner.class);

    private final InstagramInfluencerSyncService syncService;
    private final ConfigurableApplicationContext context;
    private final boolean runOnce;

    public InstagramInfluencerSyncRunner(
            InstagramInfluencerSyncService syncService,
            ConfigurableApplicationContext context,
            @Value("${instagram.influencer-sync.run-once:false}") boolean runOnce) {
        this.syncService = syncService;
        this.context = context;
        this.runOnce = runOnce;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!runOnce) {
            return;
        }
        int updated = syncService.syncNextBatch();
        LOGGER.info("Instagram influencer sync run-once done (updated={})", updated);
        int code = org.springframework.boot.SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
