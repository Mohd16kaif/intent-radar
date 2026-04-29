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

    // Separate file just for counts — survives independent of posts.json
    private String countsFilePath;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, RedditPost> store = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void load() {
        // Derive counts file path from posts file path
        countsFilePath = filePath.replace("posts.json", "counts.json");

        // Always init counters first
        counters.put("kaif", new AtomicInteger(0));
        counters.put("abdul", new AtomicInteger(0));

        // Load counts from dedicated counts.json first
        loadCounts();

        // Load posts
        File f = new File(filePath);
        if (!f.exists()) { log.info("No posts.json yet, starting fresh"); return; }
        try {
            List<RedditPost> list = mapper.readValue(f, new TypeReference<>() {});
            list.forEach(p -> store.put(p.getId(), p));
            log.info("Loaded {} posts from disk", list.size());
        } catch (Exception e) { log.error("Load failed: {}", e.getMessage()); }
    }

    private void loadCounts() {
        File f = new File(countsFilePath);
        if (!f.exists()) { log.info("No counts.json yet, starting at zero"); return; }
        try {
            Map<String, Integer> saved = mapper.readValue(f, new TypeReference<>() {});
            saved.forEach((k, v) -> counters.put(k, new AtomicInteger(v)));
            log.info("Loaded counts: kaif={}, abdul={}",
                    counters.getOrDefault("kaif", new AtomicInteger(0)).get(),
                    counters.getOrDefault("abdul", new AtomicInteger(0)).get());
        } catch (Exception e) { log.error("Failed to load counts: {}", e.getMessage()); }
    }

    private void saveCounts() {
        try {
            Map<String, Integer> toSave = new HashMap<>();
            counters.forEach((k, v) -> toSave.put(k, v.get()));
            File f = new File(countsFilePath);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(f, toSave);
        } catch (Exception e) { log.error("Failed to save counts: {}", e.getMessage()); }
    }

    public void save() {
        try {
            File file = new File(filePath);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, new ArrayList<>(store.values()));
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

    // Sort: recent high-intent posts first, then older ones
    // Primary: intent score bucket (VH, H, M), Secondary: recency within bucket
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
        counters.computeIfAbsent(person.toLowerCase(), k -> new AtomicInteger(0))
                .incrementAndGet();
        save();
        saveCounts(); // Save counts separately so they survive restarts
    }

    public void saveReply(String id, String reply) {
        store.computeIfPresent(id, (k, p) -> { p.setSavedReply(reply); return p; });
        save();
    }

    public void prune() {
        // Extended to 48 hours to keep more posts
        long cutoff = System.currentTimeMillis() / 1000 - (48 * 3600);
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