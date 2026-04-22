package com.radar.intentradar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.radar.intentradar.model.RedditPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.util.*;

@Slf4j
@Service
public class ReplyService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private static final String SYSTEM = """
        You are someone who personally struggled with porn addiction for years and is now in recovery.
        You browse Reddit and see people in pain. You reply like a real person who has been through it,
        not a therapist, not a coach, not anyone selling anything.

        MOST IMPORTANT RULE: Every reply must be specific to what THIS person wrote.
        Read their post carefully. Reference something specific they said, their exact situation,
        their specific words. A generic reply that could apply to anyone is a failure.
        If they mentioned their girlfriend, reference the relationship. If they mentioned a number
        of days, reference that. If they described a specific trigger, reference that trigger.
        Make them feel like someone actually read their post, not a copy paste response.

        Write 3 different reply options. Each one should feel like it came from a real Reddit user
        who read every word of this specific post.

        Hard rules:
        - Never use em dashes or dashes to connect thoughts. Use plain sentences instead.
        - Never mention any app, tool, product, or website
        - No motivational cliches. Never say "you got this", "stay strong", "keep pushing", "one day at a time"
        - Do not start any reply with the word "I"
        - No bullet points or lists inside the reply
        - No lecturing. No 5 step plans. No advice unless they asked for it.
        - Write casually like you are texting a friend, not writing an essay
        - Each reply must be 3 to 5 sentences long. Give some substance.
        - Match the emotional energy of the post exactly
        - Sound like someone who gets it because they lived it
        - No hashtags, no emojis unless the post itself uses them

        The 3 replies must have these different approaches:
        1. Just acknowledge their specific pain, reference something they actually said, make them feel heard
        2. Honest and raw, like a close friend who has been through the same exact thing they described
        3. Ask one single genuine question about something specific in their post

        Return ONLY a valid JSON array with exactly 3 objects. No markdown, no explanation, nothing else:
        [{"tone":"empathetic","reply":"..."},{"tone":"honest","reply":"..."},{"tone":"curious","reply":"..."}]
        """;
    public List<ReplyOption> generate(RedditPost post) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("YOUR_GROQ_KEY_HERE")) {
            log.error("Groq API key is not set");
            return fallback();
        }
        try {
            String userMessage = buildMessage(post);

            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", "llama-3.3-70b-versatile");
            requestBody.put("temperature", 0.85);
            requestBody.put("max_tokens", 800);

            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM);
            messages.addObject().put("role", "user").put("content", userMessage);

            String requestJson = mapper.writeValueAsString(requestBody);
            log.info("Sending to Groq for post: {}", post.getId());

            var req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString());

            log.info("Groq status: {}", res.statusCode());

            if (res.statusCode() != 200) {
                log.error("Groq error: {} — {}", res.statusCode(), res.body());
                return fallback();
            }

            String content = mapper.readTree(res.body())
                    .path("choices").get(0)
                    .path("message")
                    .path("content").asText();

            log.info("Groq raw reply: {}", content);

            content = content.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            // Find the JSON array — sometimes model adds text before or after
            int start = content.indexOf('[');
            int end   = content.lastIndexOf(']');
            if (start == -1 || end == -1) {
                log.error("No JSON array found in Groq response: {}", content);
                return fallback();
            }
            content = content.substring(start, end + 1);

            List<ReplyOption> replies = new ArrayList<>();
            for (JsonNode r : mapper.readTree(content)) {
                replies.add(new ReplyOption(
                        r.path("tone").asText("empathetic"),
                        r.path("reply").asText()
                ));
            }

            if (replies.isEmpty()) {
                log.error("Parsed empty replies from: {}", content);
                return fallback();
            }

            log.info("Generated {} replies for post {}", replies.size(), post.getId());
            return replies;

        } catch (Exception e) {
            log.error("Groq exception: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            return fallback();
        }
    }

    private String buildMessage(RedditPost post) {
        String body = (post.getSelftext() == null || post.getSelftext().isBlank())
                ? "(no body text, only the title was posted)"
                : truncate(post.getSelftext(), 800);

        return "READ THIS POST CAREFULLY BEFORE WRITING. YOUR REPLIES MUST REFERENCE SPECIFIC DETAILS FROM THIS POST.\n\n"
                + "Subreddit: r/" + post.getSubreddit() + "\n"
                + "Title: " + post.getTitle() + "\n"
                + "Body: " + body + "\n"
                + "Detected emotions: " + post.getDetectedEmotions() + "\n"
                + "Tags: " + String.join(", ", post.getTags() != null ? Arrays.asList(post.getTags()) : List.of()) + "\n\n"
                + "Now write 3 replies that are SPECIFIC to what this person wrote above. "
                + "Do not write generic replies. Reference their actual situation, their actual words, their actual struggle.";
    }

    private List<ReplyOption> fallback() {
        return List.of(
                new ReplyOption("empathetic", "That sounds really exhausting and the fact you are talking about it at all says a lot about where you want to be."),
                new ReplyOption("honest", "Nobody tells you how long this actually takes or how many times you will slip before it starts to click. That part is normal even when it does not feel like it."),
                new ReplyOption("curious", "What does your day usually look like when this happens, like is there a pattern you have noticed or does it feel completely random?")
        );
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public record ReplyOption(String tone, String reply) {}
}