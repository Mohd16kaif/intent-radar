package com.radar.intentradar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionScheduler {

    private final RedditService reddit;
    private final StorageService storage;

    @Value("${reddit.subreddits}")
    private String subredditsConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        storage.load();
        runCycle();
    }

    @Scheduled(fixedDelayString = "${reddit.refresh-interval-ms}")
    public void runCycle() {
        log.info("=== Ingestion cycle starting ===");
        var subs = Arrays.stream(subredditsConfig.split(",")).map(String::trim).toList();
        int totalNew = 0;
        for (String sub : subs) {
            try {
                int added = storage.upsert(reddit.fetch(sub));
                totalNew += added;
                Thread.sleep(2000); // be respectful to Reddit API
            } catch (Exception e) {
                log.error("Cycle error for r/{}: {}", sub, e.getMessage());
            }
        }
        storage.prune();
        log.info("=== Cycle done. {} new posts. Total: {} ===", totalNew, storage.count());
    }
}