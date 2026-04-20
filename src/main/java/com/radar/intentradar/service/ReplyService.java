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

        Write 3 different reply options for the post given to you.

        Hard rules:
        - Never use em dashes (--) or dashes to connect thoughts. Use plain sentences instead.
        - Never mention any app, tool, product, or website
        - No motivational cliches. Never say "you got this", "stay strong", "keep pushing", "one day at a time"
        - Do not start any reply with the word "I"
        - No bullet points or lists inside the reply
        - No lecturing. No 5 step plans. No advice unless they asked for it.
        - Write casually like you are texting a friend, not writing an essay
        - Each reply must be 3 to 5 sentences long. Not shorter. Give some substance.
        - Match the emotional energy of the post exactly. Angry post gets a raw reply. Sad post gets a gentle reply.
        - Sound like someone who gets it because they lived it
        - No hashtags
        - No emojis unless the post itself uses them

        The 3 replies must have these different approaches:
        1. Just acknowledge the pain, no fixing, no advice, just making them feel heard and less alone
        2. Honest and raw, like a close friend who has been through the same thing telling the truth
        3. Ask one single genuine question that shows you actually read their post and want to understand more

        Return ONLY a valid JSON array with exactly 3 objects. No markdown, no explanation, nothing else:
        [{"tone":"empathetic","reply":"..."},{"tone":"honest","reply":"..."},{"tone":"curious","reply":"..."}]
        """;

    public List<ReplyOption> generate(RedditPost post) {
        try {
            String userMessage = buildMessage(post);

            // Groq uses OpenAI-compatible API format
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", "llama-3.3-70b-versatile");
            requestBody.put("temperature", 0.85);
            requestBody.put("max_tokens", 800);

            ArrayNode messages = requestBody.putArray("messages");

            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM);

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            var req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString());




            if (res.statusCode() != 200) {
                log.error("Groq API error status={} body={}", res.statusCode(), res.body());
                return fallback();
            }

            // Groq returns OpenAI format: choices[0].message.content
            String content = mapper.readTree(res.body())
                    .path("choices").get(0)
                    .path("message")
                    .path("content").asText();



            // Strip markdown fences if model adds them
            content = content.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            List<ReplyOption> replies = new ArrayList<>();
            for (JsonNode r : mapper.readTree(content)) {
                replies.add(new ReplyOption(
                        r.path("tone").asText("empathetic"),
                        r.path("reply").asText()
                ));
            }

            log.info("Generated {} replies successfully", replies.size());
            return replies;

        } catch (Exception e) {
            log.error("Reply generation error: {}", e.getMessage());
            return fallback();
        }
    }

    private String buildMessage(RedditPost post) {
        String body = (post.getSelftext() == null || post.getSelftext().isBlank())
                ? "(no body, title only post)"
                : truncate(post.getSelftext(), 500);

        return "Subreddit: r/" + post.getSubreddit() + "\n"
                + "Title: " + post.getTitle() + "\n"
                + "Body: " + body + "\n"
                + "Detected emotions: " + post.getDetectedEmotions() + "\n"
                + "Tags: " + String.join(", ", post.getTags() != null ? Arrays.asList(post.getTags()) : List.of()) + "\n\n"
                + "Write 3 replies for this person following all the rules.";
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