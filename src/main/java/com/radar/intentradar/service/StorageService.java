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

@Slf4j
@Service
public class StorageService {

    @Value("${storage.file-path}")
    private String filePath;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, RedditPost> store = new ConcurrentHashMap<>();

    public void load() {
        File f = new File(filePath);
        if (!f.exists()) { log.info("No posts.json yet, starting fresh"); return; }
        try {
            List<RedditPost> list = mapper.readValue(f, new TypeReference<>() {});
            list.forEach(p -> store.put(p.getId(), p));
            log.info("Loaded {} posts from disk", list.size());
        } catch (Exception e) { log.error("Load failed: {}", e.getMessage()); }
    }

    public void save() {
        try {
            new File(filePath).getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), new ArrayList<>(store.values()));
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

    public void dismiss(String id) {
        store.computeIfPresent(id, (k, p) -> { p.setDismissed(true); return p; });
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
}