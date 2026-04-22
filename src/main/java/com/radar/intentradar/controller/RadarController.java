package com.radar.intentradar.controller;

import com.radar.intentradar.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.radar.intentradar.service.TemplateService;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RadarController {

    private final StorageService storage;
    private final ReplyService replyService;
    private final IngestionScheduler scheduler;
    private final TemplateService templateService;

    @GetMapping("/posts")
    public List<?> getPosts(@RequestParam(defaultValue = "ALL") String filter) {
        var posts = storage.getSortedByScore();
        return switch (filter) {
            case "VH"   -> posts.stream().filter(p -> p.getIntentScore() >= 80).toList();
            case "HIGH" -> posts.stream().filter(p -> p.getIntentScore() >= 60 && p.getIntentScore() < 80).toList();
            case "MEDIUM" -> posts.stream().filter(p -> p.getIntentScore() >= 35 && p.getIntentScore() < 60).toList();
            default     -> posts;
        };
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var posts = storage.getSortedByScore();
        Map<String, Object> result = new HashMap<>();
        result.put("total",    posts.size());
        result.put("veryHigh", posts.stream().filter(p -> p.getIntentScore() >= 80).count());
        result.put("high",     posts.stream().filter(p -> p.getIntentScore() >= 60 && p.getIntentScore() < 80).count());
        result.put("medium",   posts.stream().filter(p -> p.getIntentScore() >= 35 && p.getIntentScore() < 60).count());
        // Add partner counters to stats response
        result.putAll(storage.getCounters().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> "count_" + e.getKey(),
                        Map.Entry::getValue
                )));
        return result;
    }

    @PostMapping("/posts/{id}/replies")
    public ResponseEntity<?> replies(@PathVariable String id) {
        return storage.get(id)
                .map(p -> ResponseEntity.ok(replyService.generate(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // New endpoint — dismiss with person name
    @PostMapping("/posts/{id}/dismiss/{person}")
    public Map<String, String> dismissByPerson(
            @PathVariable String id,
            @PathVariable String person
    ) {
        if (!person.equals("kaif") && !person.equals("abdul")) {
            return Map.of("status", "error", "message", "unknown person");
        }
        storage.dismissByPerson(id, person);
        return Map.of("status", "ok", "person", person);
    }

    // Keep old dismiss endpoint for backward compat
    @PostMapping("/posts/{id}/dismiss")
    public Map<String, String> dismiss(@PathVariable String id) {
        storage.dismissByPerson(id, "kaif");
        return Map.of("status", "ok");
    }

    @PostMapping("/posts/{id}/reply")
    public ResponseEntity<?> saveReply(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String reply = body.get("reply");
        if (reply == null || reply.isBlank()) return ResponseEntity.badRequest().build();
        storage.saveReply(id, reply);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }
    @GetMapping("/subreddit-stats")
    public List<?> subredditStats() {
        return storage.getSubredditStats();
    }
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        scheduler.runCycle();
        return Map.of("status", "done", "total", storage.count());
    }

    @GetMapping("/templates")
    public List<?> getTemplates() {
        return templateService.getAll();
    }

    @PostMapping("/templates")
    public ResponseEntity<?> saveTemplate(@RequestBody Map<String, String> body) {
        String label   = body.get("label");
        String text    = body.get("text");
        String savedBy = body.get("savedBy");
        if (label == null || text == null || label.isBlank() || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "label and text required"));
        }
        return ResponseEntity.ok(templateService.add(label, text, savedBy != null ? savedBy : "unknown"));
    }

    @DeleteMapping("/templates/{id}")
    public Map<String, String> deleteTemplate(@PathVariable String id) {
        templateService.delete(id);
        return Map.of("status", "deleted");
    }


}