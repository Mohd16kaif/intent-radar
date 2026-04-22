package com.radar.intentradar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TemplateService {

    private static final String TEMPLATE_FILE = "/tmp/templates.json";
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Template> templates = new ConcurrentHashMap<>();

    public record Template(String id, String label, String text, String savedBy, long savedAt) {}

    public TemplateService() {
        load();
    }

    private void load() {
        File f = new File(TEMPLATE_FILE);
        if (!f.exists()) return;
        try {
            List<Template> list = mapper.readValue(f, new TypeReference<>() {});
            list.forEach(t -> templates.put(t.id(), t));
            log.info("Loaded {} templates", templates.size());
        } catch (Exception e) {
            log.error("Failed to load templates: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(TEMPLATE_FILE), new ArrayList<>(templates.values()));
        } catch (Exception e) {
            log.error("Failed to save templates: {}", e.getMessage());
        }
    }

    public List<Template> getAll() {
        return templates.values().stream()
                .sorted((a, b) -> Long.compare(b.savedAt(), a.savedAt()))
                .toList();
    }

    public Template add(String label, String text, String savedBy) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Template t = new Template(id, label, text, savedBy, System.currentTimeMillis());
        templates.put(id, t);
        save();
        return t;
    }

    public void delete(String id) {
        templates.remove(id);
        save();
    }
}