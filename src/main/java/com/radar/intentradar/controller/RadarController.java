package com.radar.intentradar.controller;

import com.radar.intentradar.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RadarController {

    private final StorageService storage;
    private final ReplyService replyService;
    private final IngestionScheduler scheduler;

    @GetMapping("/posts")
    public List<?> getPosts(@RequestParam(defaultValue = "ALL") String filter) {
        var posts = storage.getSortedByScore();
        return switch (filter) {
            case "VH"   -> posts.stream().filter(p -> p.getIntentScore() >= 80).toList();
            case "HIGH" -> posts.stream().filter(p -> p.getIntentScore() >= 60).toList();
            default     -> posts;
        };
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var posts = storage.getSortedByScore();
        return Map.of(
                "total",    posts.size(),
                "veryHigh", posts.stream().filter(p -> p.getIntentScore() >= 80).count(),
                "high",     posts.stream().filter(p -> p.getIntentScore() >= 60 && p.getIntentScore() < 80).count(),
                "medium",   posts.stream().filter(p -> p.getIntentScore() >= 35 && p.getIntentScore() < 60).count()
        );
    }

    @PostMapping("/posts/{id}/replies")
    public ResponseEntity<?> replies(@PathVariable String id) {
        return storage.get(id)
                .map(p -> ResponseEntity.ok(replyService.generate(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/posts/{id}/dismiss")
    public Map<String, String> dismiss(@PathVariable String id) {
        storage.dismiss(id);
        return Map.of("status", "ok");
    }

    @PostMapping("/posts/{id}/reply")
    public ResponseEntity<?> saveReply(@PathVariable String id, @RequestBody Map<String, String> body) {
        String reply = body.get("reply");
        if (reply == null || reply.isBlank()) return ResponseEntity.badRequest().build();
        storage.saveReply(id, reply);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        scheduler.runCycle();
        return Map.of("status", "done", "total", storage.count());
    }
}