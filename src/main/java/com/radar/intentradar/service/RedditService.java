package com.radar.intentradar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radar.intentradar.model.RedditPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedditService {

    @Value("${reddit.user-agent}")
    private String userAgent;

    @Value("${reddit.max-post-age-hours}")
    private int maxAgeHours;

    private final IntentScoringService scorer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public List<RedditPost> fetch(String subreddit) {
        List<RedditPost> posts = new ArrayList<>();
        try {
            long cutoff = Instant.now().getEpochSecond() - (maxAgeHours * 3600L);

            // Public JSON API - no auth needed
            String url = "https://www.reddit.com/r/" + subreddit + "/new.json?limit=50";

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 429) {
                log.warn("Rate limited on r/{} - will retry next cycle", subreddit);
                return posts;
            }
            if (res.statusCode() != 200) {
                log.error("Failed to fetch r/{}: status {}", subreddit, res.statusCode());
                return posts;
            }

            JsonNode children = mapper.readTree(res.body()).path("data").path("children");

            for (JsonNode child : children) {
                JsonNode d = child.path("data");
                long created = d.path("created_utc").asLong();
                if (created < cutoff) continue;

                // Skip removed/deleted posts
                String selftext = d.path("selftext").asText("");
                if (selftext.equals("[removed]") || selftext.equals("[deleted]")) continue;

                RedditPost p = new RedditPost();
                p.setId(d.path("id").asText());
                p.setTitle(d.path("title").asText());
                p.setSelftext(selftext);
                p.setAuthor(d.path("author").asText());
                p.setSubreddit(subreddit);
                p.setCreatedUtc(created);
                p.setScore(d.path("score").asInt(0));
                p.setNumComments(d.path("num_comments").asInt(0));
                p.setUrl("https://reddit.com" + d.path("permalink").asText());

                scorer.scorePost(p);
                if (scorer.isWorthShowing(p)) posts.add(p);
            }

            log.info("r/{}: {} qualifying posts found", subreddit, posts.size());

        } catch (Exception e) {
            log.error("Error fetching r/{}: {}", subreddit, e.getMessage());
        }
        return posts;
    }
}