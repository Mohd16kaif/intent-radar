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
        You browse Reddit and see someone struggling. You want to help them genuinely.

        MOST IMPORTANT RULE: Read the post carefully and write replies that are specific to their
        exact situation. Do not repeat back what they said. Do not summarize their post to them.
        Just respond like a human who read it and wants to help.

        Write 3 different reply options. Each reply should offer genuine support or a practical
        insight that actually helps this specific person with their specific situation.

        Hard rules:
        - Never repeat or summarize what the person said back to them
        - Never use em dashes or dashes to connect thoughts. Use plain sentences instead.
        - Never mention any app, tool, product, or website
        - No motivational cliches. No "you got this", "stay strong", "keep pushing", "one day at a time"
        - Do not start any reply with the word "I"
        - No bullet points or lists inside the reply
        - No lecturing. No 5 step plans.
        - Write casually like texting a friend who has been through the same thing
        - Each reply must be 3 to 5 sentences long
        - Match the emotional energy of the post
        - Sound like someone who gets it because they lived it
        - No hashtags, no emojis unless the post uses them
        - Focus on actually helping them, giving them something real to hold onto

        The 3 replies should each take a slightly different angle on how to help:
        1. Emotional support — make them feel less alone, less ashamed, more human
        2. Practical insight — something real that actually helped you when you were in the same place
        3. Perspective shift — a different way to look at their situation that might help them move forward

        Return ONLY a valid JSON array with exactly 3 objects. No markdown, no explanation, nothing else:
        [{"tone":"support","reply":"..."},{"tone":"insight","reply":"..."},{"tone":"perspective","reply":"..."}]
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
            requestBody.put("temperature", 0.9);
            requestBody.put("max_tokens", 900);

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
                log.error("Groq error: {} {}", res.statusCode(), res.body());
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

            int start = content.indexOf('[');
            int end   = content.lastIndexOf(']');
            if (start == -1 || end == -1) {
                log.error("No JSON array in Groq response: {}", content);
                return fallback();
            }
            content = content.substring(start, end + 1);

            List<ReplyOption> replies = new ArrayList<>();
            for (JsonNode r : mapper.readTree(content)) {
                replies.add(new ReplyOption(
                        r.path("tone").asText("support"),
                        r.path("reply").asText()
                ));
            }

            if (replies.isEmpty()) {
                log.error("Empty replies parsed from: {}", content);
                return fallback();
            }

            log.info("Generated {} replies for post {}", replies.size(), post.getId());
            return replies;

        } catch (Exception e) {
            log.error("Groq exception: {} {}", e.getClass().getSimpleName(), e.getMessage());
            return fallback();
        }
    }

    private String buildMessage(RedditPost post) {
        String body = (post.getSelftext() == null || post.getSelftext().isBlank())
                ? "(no body text, title only post)"
                : truncate(post.getSelftext(), 800);

        return "Here is the Reddit post you need to reply to:\n\n"
                + "Subreddit: r/" + post.getSubreddit() + "\n"
                + "Title: " + post.getTitle() + "\n"
                + "Body: " + body + "\n"
                + "Emotions detected: " + post.getDetectedEmotions() + "\n"
                + "Tags: " + String.join(", ", post.getTags() != null ? Arrays.asList(post.getTags()) : List.of()) + "\n\n"
                + "Write 3 replies that genuinely help this specific person. "
                + "Do NOT repeat back what they said. Do NOT summarize their post. "
                + "Just respond with something real and helpful based on their specific situation.";
    }

    private List<ReplyOption> fallback() {
        return List.of(
                new ReplyOption("support", "That feeling after a relapse is one of the worst parts of this whole thing and it does not mean you are back to square one even when it feels that way."),
                new ReplyOption("insight", "The shame spiral after a slip is usually what makes the next one more likely. Breaking that cycle matters more than the streak number."),
                new ReplyOption("perspective", "Every single person who has gotten through this has a long list of relapses behind them. The ones who made it are not the ones who never slipped.")
        );
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public record ReplyOption(String tone, String reply) {}
}