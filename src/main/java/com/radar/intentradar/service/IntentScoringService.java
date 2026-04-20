package com.radar.intentradar.service;

import com.radar.intentradar.model.RedditPost;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class IntentScoringService {

    private static final Map<String, Integer> HIGH_KEYWORDS = new HashMap<>();
    private static final Map<String, Integer> MEDIUM_KEYWORDS = new HashMap<>();
    private static final Map<String, Integer> LOW_KEYWORDS = new HashMap<>();
    private static final Map<String, List<String>> EMOTION_SIGNALS = new HashMap<>();

    static {
        // ── HIGH (30–40) — acute pain, relapse moment, sexual dysfunction ──────
        HIGH_KEYWORDS.put("hate myself", 40);
        HIGH_KEYWORDS.put("porn addiction", 38);
        HIGH_KEYWORDS.put("addicted to porn", 40);
        HIGH_KEYWORDS.put("can't stop watching porn", 40);
        HIGH_KEYWORDS.put("cant stop watching porn", 40);
        HIGH_KEYWORDS.put("porn is ruining my life", 40);
        HIGH_KEYWORDS.put("porn ruined my life", 40);
        HIGH_KEYWORDS.put("porn ruined my", 38);
        HIGH_KEYWORDS.put("masturbation addiction", 38);
        HIGH_KEYWORDS.put("addicted to masturbation", 38);
        HIGH_KEYWORDS.put("can't stop masturbating", 38);
        HIGH_KEYWORDS.put("cant stop masturbating", 38);
        HIGH_KEYWORDS.put("compulsive masturbation", 36);
        HIGH_KEYWORDS.put("erectile dysfunction", 36);
        HIGH_KEYWORDS.put("porn induced erectile", 40);
        HIGH_KEYWORDS.put("pied", 36);
        HIGH_KEYWORDS.put("can't get hard", 36);
        HIGH_KEYWORDS.put("cant get hard", 36);
        HIGH_KEYWORDS.put("can't get it up", 36);
        HIGH_KEYWORDS.put("cant get it up", 36);
        HIGH_KEYWORDS.put("lost attraction to real", 38);
        HIGH_KEYWORDS.put("only attracted to porn", 38);
        HIGH_KEYWORDS.put("can't get aroused", 36);
        HIGH_KEYWORDS.put("cant get aroused", 36);
        HIGH_KEYWORDS.put("desensitized to real sex", 38);
        HIGH_KEYWORDS.put("prefer porn over", 36);
        HIGH_KEYWORDS.put("porn over real", 36);
        HIGH_KEYWORDS.put("hit rock bottom", 40);
        HIGH_KEYWORDS.put("rock bottom", 38);
        HIGH_KEYWORDS.put("feel disgusting", 36);
        HIGH_KEYWORDS.put("feel like a failure", 36);
        HIGH_KEYWORDS.put("no hope", 34);
        HIGH_KEYWORDS.put("hopeless", 32);
        HIGH_KEYWORDS.put("destroying me", 35);
        HIGH_KEYWORDS.put("i give up", 36);
        HIGH_KEYWORDS.put("please help me", 36);
        HIGH_KEYWORDS.put("porn is destroying", 38);
        HIGH_KEYWORDS.put("fapping ruins", 34);
        HIGH_KEYWORDS.put("fap addiction", 36);
        HIGH_KEYWORDS.put("addicted to fapping", 36);
        HIGH_KEYWORDS.put("can't stop fapping", 36);
        HIGH_KEYWORDS.put("cant stop fapping", 36);
        HIGH_KEYWORDS.put("porn brain", 34);
        HIGH_KEYWORDS.put("death grip", 32);
        HIGH_KEYWORDS.put("sexual dysfunction", 36);
        HIGH_KEYWORDS.put("porn has ruined", 38);
        HIGH_KEYWORDS.put("escalating porn", 36);
        HIGH_KEYWORDS.put("extreme porn", 34);
        HIGH_KEYWORDS.put("fetish escalation", 34);
        HIGH_KEYWORDS.put("watching porn every day", 36);
        HIGH_KEYWORDS.put("watching porn daily", 36);
        HIGH_KEYWORDS.put("hours watching porn", 36);
        HIGH_KEYWORDS.put("relationship ruined by porn", 40);
        HIGH_KEYWORDS.put("girlfriend found out", 34);
        HIGH_KEYWORDS.put("wife found out", 34);
        HIGH_KEYWORDS.put("partner found my porn", 36);
        HIGH_KEYWORDS.put("premature ejaculation", 32);

        // ── MEDIUM (15–25) — relapse, struggling, trying to quit ─────────────
        MEDIUM_KEYWORDS.put("relapsed", 25);
        MEDIUM_KEYWORDS.put("relapse", 22);
        MEDIUM_KEYWORDS.put("pmo relapse", 25);
        MEDIUM_KEYWORDS.put("pmo", 20);
        MEDIUM_KEYWORDS.put("no pmo", 20);
        MEDIUM_KEYWORDS.put("nopmo", 20);
        MEDIUM_KEYWORDS.put("failed nofap", 24);
        MEDIUM_KEYWORDS.put("broke my streak", 24);
        MEDIUM_KEYWORDS.put("streak broken", 22);
        MEDIUM_KEYWORDS.put("back to day 0", 24);
        MEDIUM_KEYWORDS.put("day 0", 22);
        MEDIUM_KEYWORDS.put("back to zero", 22);
        MEDIUM_KEYWORDS.put("reset my counter", 20);
        MEDIUM_KEYWORDS.put("fapped again", 24);
        MEDIUM_KEYWORDS.put("masturbated again", 24);
        MEDIUM_KEYWORDS.put("watched porn again", 24);
        MEDIUM_KEYWORDS.put("gave in again", 22);
        MEDIUM_KEYWORDS.put("couldn't resist", 20);
        MEDIUM_KEYWORDS.put("couldn't control", 20);
        MEDIUM_KEYWORDS.put("urge to watch porn", 22);
        MEDIUM_KEYWORDS.put("urge to masturbate", 22);
        MEDIUM_KEYWORDS.put("porn urges", 22);
        MEDIUM_KEYWORDS.put("struggling with porn", 24);
        MEDIUM_KEYWORDS.put("struggling with masturbation", 24);
        MEDIUM_KEYWORDS.put("quitting porn", 20);
        MEDIUM_KEYWORDS.put("quit porn", 20);
        MEDIUM_KEYWORDS.put("stop watching porn", 22);
        MEDIUM_KEYWORDS.put("stop masturbating", 20);
        MEDIUM_KEYWORDS.put("nofap relapse", 25);
        MEDIUM_KEYWORDS.put("flatline", 18);
        MEDIUM_KEYWORDS.put("nofap flatline", 22);
        MEDIUM_KEYWORDS.put("no motivation", 16);
        MEDIUM_KEYWORDS.put("brain fog", 18);
        MEDIUM_KEYWORDS.put("porn brain fog", 22);
        MEDIUM_KEYWORDS.put("need help quitting", 22);
        MEDIUM_KEYWORDS.put("help me quit porn", 24);
        MEDIUM_KEYWORDS.put("withdrawals", 18);
        MEDIUM_KEYWORDS.put("porn withdrawal", 22);
        MEDIUM_KEYWORDS.put("low libido", 18);
        MEDIUM_KEYWORDS.put("lost libido", 20);
        MEDIUM_KEYWORDS.put("libido gone", 20);
        MEDIUM_KEYWORDS.put("no morning wood", 20);
        MEDIUM_KEYWORDS.put("anxiety after", 16);
        MEDIUM_KEYWORDS.put("shame after", 18);
        MEDIUM_KEYWORDS.put("guilt after", 18);
        MEDIUM_KEYWORDS.put("feel empty after", 20);
        MEDIUM_KEYWORDS.put("feel disgusted after", 22);

        // ── LOW (5–10) — general nofap/recovery discussion ───────────────────
        LOW_KEYWORDS.put("nofap", 8);
        LOW_KEYWORDS.put("pornfree", 8);
        LOW_KEYWORDS.put("day 1", 8);
        LOW_KEYWORDS.put("starting over", 10);
        LOW_KEYWORDS.put("reboot", 8);
        LOW_KEYWORDS.put("hardmode", 6);
        LOW_KEYWORDS.put("hard mode", 6);
        LOW_KEYWORDS.put("semen retention", 6);
        LOW_KEYWORDS.put("porn free", 8);
        LOW_KEYWORDS.put("fap free", 8);
        LOW_KEYWORDS.put("no fap", 8);

        // ── EMOTION SIGNALS ───────────────────────────────────────────────────
        EMOTION_SIGNALS.put("guilt", Arrays.asList(
                "hate myself", "ashamed", "shame", "disgusting", "pathetic",
                "loser", "feel dirty", "disgusted with myself", "feel gross"
        ));
        EMOTION_SIGNALS.put("frustration", Arrays.asList(
                "again", "every single time", "can't stop", "cant stop",
                "no willpower", "why can't i", "tried everything", "nothing works"
        ));
        EMOTION_SIGNALS.put("despair", Arrays.asList(
                "hopeless", "no hope", "never going to", "give up",
                "rock bottom", "done trying", "what's the point", "no way out"
        ));
        EMOTION_SIGNALS.put("sexual dysfunction", Arrays.asList(
                "erectile dysfunction", "pied", "can't get hard", "cant get hard",
                "death grip", "desensitized", "no attraction", "premature"
        ));
        EMOTION_SIGNALS.put("relationship pain", Arrays.asList(
                "girlfriend", "wife", "partner", "relationship", "found out",
                "caught me", "trust", "betrayed"
        ));
        EMOTION_SIGNALS.put("shame spiral", Arrays.asList(
                "relapsed again", "fapped again", "watched porn again",
                "broke my streak", "failed again", "gave in"
        ));
    }

    public void scorePost(RedditPost post) {
        String text = (post.getTitle() + " " + post.getSelftext()).toLowerCase();

        // Hard filter — if post has zero porn/masturbation related words, skip it
        if (!isPortnRelated(text)) {
            post.setIntentScore(0);
            post.setIntentLevel("LOW");
            post.setDetectedEmotions("not relevant");
            post.setTags(new String[]{});
            return;
        }

        int recency    = recencyScore(post.getCreatedUtc());
        int emotion    = emotionScore(text);
        int engagement = engagementScore(post.getScore(), post.getNumComments());

        int total = Math.min(100, recency + emotion + engagement);

        post.setIntentScore(total);
        post.setIntentLevel(intentLevel(total));
        post.setDetectedEmotions(detectEmotions(text));
        post.setTags(extractTags(text));
    }

    // Must contain at least one core topic word to be shown at all
    private boolean isPortnRelated(String text) {
        List<String> coreTopics = Arrays.asList(
                "porn", "pornography", "masturbat", "fap", "fapping",
                "nofap", "no fap", "pmo", "nopmo", "reboot",
                "erectile", "pied", "death grip", "libido",
                "sexual addiction", "sex addiction", "flatline",
                "pornfree", "porn free", "porn addiction"
        );
        return coreTopics.stream().anyMatch(text::contains);
    }

    private int recencyScore(long createdUtc) {
        long ageMinutes = (Instant.now().getEpochSecond() - createdUtc) / 60;
        if (ageMinutes <= 60)  return 40;
        if (ageMinutes <= 120) return 35;
        if (ageMinutes <= 240) return 28;
        if (ageMinutes <= 360) return 20;
        if (ageMinutes <= 480) return 12;
        if (ageMinutes <= 720) return 5;
        return 0;
    }

    private int emotionScore(String text) {
        int high = HIGH_KEYWORDS.entrySet().stream()
                .filter(e -> text.contains(e.getKey()))
                .mapToInt(Map.Entry::getValue).max().orElse(0);
        int med = MEDIUM_KEYWORDS.entrySet().stream()
                .filter(e -> text.contains(e.getKey()))
                .mapToInt(Map.Entry::getValue).max().orElse(0);
        int low = LOW_KEYWORDS.entrySet().stream()
                .filter(e -> text.contains(e.getKey()))
                .mapToInt(Map.Entry::getValue).max().orElse(0);
        int best = Math.max(high, med);
        return Math.min(40, best == 0 ? low : best);
    }

    private int engagementScore(int upvotes, int comments) {
        int s = 0;
        if (comments == 0)          s += 10;
        else if (comments <= 5)     s += 15;
        else if (comments <= 15)    s += 8;
        else                        s += 3;
        if (upvotes >= 2 && upvotes <= 20) s += 5;
        return Math.min(20, s);
    }

    private String intentLevel(int score) {
        if (score >= 80) return "VERY HIGH";
        if (score >= 60) return "HIGH";
        if (score >= 35) return "MEDIUM";
        return "LOW";
    }

    private String detectEmotions(String text) {
        List<String> found = new ArrayList<>();
        for (var entry : EMOTION_SIGNALS.entrySet()) {
            if (entry.getValue().stream().anyMatch(text::contains)) found.add(entry.getKey());
        }
        return found.isEmpty() ? "general distress" : String.join(" + ", found);
    }

    private String[] extractTags(String text) {
        List<String> tags = new ArrayList<>();
        if (text.contains("relapse") || text.contains("relapsed"))         tags.add("relapse");
        if (text.contains("day 0") || text.contains("back to zero"))       tags.add("day-0");
        if (text.contains("pied") || text.contains("erectile"))            tags.add("PIED");
        if (text.contains("death grip"))                                    tags.add("death-grip");
        if (text.contains("flatline"))                                      tags.add("flatline");
        if (text.contains("urge") || text.contains("urges"))               tags.add("urge");
        if (text.contains("late night") || text.contains("cant sleep"))    tags.add("late-night");
        if (text.contains("girlfriend") || text.contains("wife") || text.contains("partner")) tags.add("relationship");
        if (text.contains("alone") || text.contains("lonely"))             tags.add("loneliness");
        if (text.contains("shame") || text.contains("ashamed"))            tags.add("shame");
        if (text.contains("brain fog"))                                     tags.add("brain-fog");
        if (text.contains("withdrawal"))                                    tags.add("withdrawal");
        return tags.toArray(new String[0]);
    }

    public boolean isWorthShowing(RedditPost post) {
        return post.getIntentScore() >= 25;
    }
}