package com.radar.intentradar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radar.intentradar.model.RedditPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class StorageService {

    @Value("${storage.file-path}")
    private String filePath;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, RedditPost> store = new ConcurrentHashMap<>();

    // Per-person counters — stored in memory, reset on restart
    // Since /tmp resets on Render redeploy anyway, in-memory is fine
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void load() {
        // Init counters
        counters.put("kaif", new AtomicInteger(0));
        counters.put("abdul", new AtomicInteger(0));

        File f = new File(filePath);
        if (!f.exists()) { log.info("No posts.json yet, starting fresh"); return; }
        try {
            List<RedditPost> list = mapper.readValue(f, new TypeReference<>() {});
            list.forEach(p -> {
                store.put(p.getId(), p);
                // Rebuild counters from saved data for today
                if (p.getHandledBy() != null && !p.getHandledBy().isBlank()) {
                    String person = p.getHandledBy().toLowerCase();
                    counters.computeIfAbsent(person, k -> new AtomicInteger(0)).incrementAndGet();
                }
            });
            log.info("Loaded {} posts from disk", list.size());
        } catch (Exception e) { log.error("Load failed: {}", e.getMessage()); }
    }

    public void save() {
        try {
            File file = new File(filePath);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, new ArrayList<>(store.values()));
        } catch (Exception e) { log.error("Save failed: {}", e.getMessage()); }
    }

    public int upsert(List<RedditPost> posts) {
        int added = 0;
        for (RedditPost p : posts) {
            if (!store.containsKey(p.getId())) { store.put(p.getId(), p); added++; }
        }
        if (added > 0) save();
        return added;
    }

    public List<RedditPost> getSortedByScore() {
        return store.values().stream()
                .filter(p -> !p.isDismissed())
                .sorted(Comparator.comparingInt(RedditPost::getIntentScore).reversed())
                .toList();
    }

    public Optional<RedditPost> get(String id) { return Optional.ofNullable(store.get(id)); }

    public void dismissByPerson(String id, String person) {
        store.computeIfPresent(id, (k, p) -> {
            p.setDismissed(true);
            p.setHandledBy(person.toLowerCase());
            return p;
        });
        counters.computeIfAbsent(person.toLowerCase(), k -> new AtomicInteger(0)).incrementAndGet();
        save();
    }

    public void saveReply(String id, String reply) {
        store.computeIfPresent(id, (k, p) -> { p.setSavedReply(reply); return p; });
        save();
    }

    public void prune() {
        long cutoff = System.currentTimeMillis() / 1000 - 86400;
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().getCreatedUtc() < cutoff);
        int pruned = before - store.size();
        if (pruned > 0) { log.info("Pruned {} old posts", pruned); save(); }
    }

    public int count() { return store.size(); }

    public Map<String, Integer> getCounters() {
        Map<String, Integer> result = new HashMap<>();
        counters.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public List<Map<String, Object>> getSubredditStats() {
        Map<String, Map<String, Object>> stats = new HashMap<>();

        for (RedditPost post : store.values()) {
            String sub = post.getSubreddit();
            stats.computeIfAbsent(sub, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("subreddit", k);
                m.put("total", 0);
                m.put("handled", 0);
                m.put("veryHigh", 0);
                return m;
            });

            Map<String, Object> s = stats.get(sub);
            s.put("total", (int) s.get("total") + 1);
            if (post.getHandledBy() != null) s.put("handled", (int) s.get("handled") + 1);
            if (post.getIntentScore() >= 80)  s.put("veryHigh", (int) s.get("veryHigh") + 1);
        }

        return stats.values().stream()
                .sorted((a, b) -> (int) b.get("total") - (int) a.get("total"))
                .toList();
    }



}