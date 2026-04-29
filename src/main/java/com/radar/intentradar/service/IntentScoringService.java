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
        // ── HIGH INTENT ───────────────────────────────────────────────────────

        // Help seeking
        HIGH_KEYWORDS.put("i need help", 40);
        HIGH_KEYWORDS.put("please help me", 40);
        HIGH_KEYWORDS.put("can someone help me", 40);
        HIGH_KEYWORDS.put("i need advice", 38);
        HIGH_KEYWORDS.put("any advice", 35);
        HIGH_KEYWORDS.put("what should i do", 38);
        HIGH_KEYWORDS.put("what do i do", 38);
        HIGH_KEYWORDS.put("how do i stop", 38);
        HIGH_KEYWORDS.put("how to stop", 36);
        HIGH_KEYWORDS.put("how do i quit", 38);
        HIGH_KEYWORDS.put("how to quit", 36);
        HIGH_KEYWORDS.put("i don't know what to do", 40);
        HIGH_KEYWORDS.put("i dont know what to do", 40);

        // Loss of control
        HIGH_KEYWORDS.put("i can't stop", 38);
        HIGH_KEYWORDS.put("i cant stop", 38);
        HIGH_KEYWORDS.put("i can't quit", 38);
        HIGH_KEYWORDS.put("i cant quit", 38);
        HIGH_KEYWORDS.put("i keep doing it", 36);
        HIGH_KEYWORDS.put("i keep going back", 36);
        HIGH_KEYWORDS.put("i always end up", 34);
        HIGH_KEYWORDS.put("i keep relapsing", 40);
        HIGH_KEYWORDS.put("i can't control it", 38);
        HIGH_KEYWORDS.put("i cant control it", 38);
        HIGH_KEYWORDS.put("i feel out of control", 38);
        HIGH_KEYWORDS.put("out of control", 36);
        HIGH_KEYWORDS.put("no control", 34);
        HIGH_KEYWORDS.put("compulsive", 32);
        HIGH_KEYWORDS.put("automatic habit", 30);

        // Deep pain and despair
        HIGH_KEYWORDS.put("porn ruined my life", 40);
        HIGH_KEYWORDS.put("ruining my life", 40);
        HIGH_KEYWORDS.put("destroying my life", 40);
        HIGH_KEYWORDS.put("this is ruining me", 40);
        HIGH_KEYWORDS.put("this is destroying me", 40);
        HIGH_KEYWORDS.put("i hate myself", 40);
        HIGH_KEYWORDS.put("feel like a failure", 38);
        HIGH_KEYWORDS.put("feel disgusting", 38);
        HIGH_KEYWORDS.put("feel ashamed", 36);
        HIGH_KEYWORDS.put("ashamed", 32);
        HIGH_KEYWORDS.put("feel gross", 34);
        HIGH_KEYWORDS.put("regret", 30);
        HIGH_KEYWORDS.put("hopeless", 36);
        HIGH_KEYWORDS.put("no hope", 36);
        HIGH_KEYWORDS.put("i give up", 38);
        HIGH_KEYWORDS.put("give up", 34);
        HIGH_KEYWORDS.put("what's the point", 36);
        HIGH_KEYWORDS.put("i feel stuck", 34);
        HIGH_KEYWORDS.put("i feel trapped", 36);
        HIGH_KEYWORDS.put("i don't want to do this anymore", 40);
        HIGH_KEYWORDS.put("i dont want to do this anymore", 40);

        // Long term struggle
        HIGH_KEYWORDS.put("for years", 32);
        HIGH_KEYWORDS.put("since i was", 30);
        HIGH_KEYWORDS.put("been struggling with", 36);
        HIGH_KEYWORDS.put("tried everything", 38);
        HIGH_KEYWORDS.put("nothing works", 38);
        HIGH_KEYWORDS.put("i always fail", 36);
        HIGH_KEYWORDS.put("never works", 36);
        HIGH_KEYWORDS.put("i'm tired of relapsing", 40);
        HIGH_KEYWORDS.put("i am tired of relapsing", 40);
        HIGH_KEYWORDS.put("i can't keep doing this", 40);
        HIGH_KEYWORDS.put("i cant keep doing this", 40);
        HIGH_KEYWORDS.put("i need to stop", 36);
        HIGH_KEYWORDS.put("i have to stop", 36);
        HIGH_KEYWORDS.put("this is getting worse", 36);
        HIGH_KEYWORDS.put("it's getting worse", 36);
        HIGH_KEYWORDS.put("its getting worse", 36);
        HIGH_KEYWORDS.put("i'm tired of this", 36);
        HIGH_KEYWORDS.put("i am tired of this", 36);
        HIGH_KEYWORDS.put("i feel horrible", 34);
        HIGH_KEYWORDS.put("i feel awful", 34);
        HIGH_KEYWORDS.put("i feel broken", 38);
        HIGH_KEYWORDS.put("i feel alone", 36);
        HIGH_KEYWORDS.put("i have no one to talk to", 40);
        HIGH_KEYWORDS.put("i don't want to be like this", 40);
        HIGH_KEYWORDS.put("i dont want to be like this", 40);
        HIGH_KEYWORDS.put("long time", 28);

        // ── MEDIUM INTENT ─────────────────────────────────────────────────────

        // Relapse and struggle
        MEDIUM_KEYWORDS.put("relapsed", 25);
        MEDIUM_KEYWORDS.put("relapse", 22);
        MEDIUM_KEYWORDS.put("gave in", 22);
        MEDIUM_KEYWORDS.put("gave in again", 25);
        MEDIUM_KEYWORDS.put("did it again", 24);
        MEDIUM_KEYWORDS.put("i did it again", 24);
        MEDIUM_KEYWORDS.put("failed again", 25);
        MEDIUM_KEYWORDS.put("messed up again", 24);
        MEDIUM_KEYWORDS.put("slipped up", 22);
        MEDIUM_KEYWORDS.put("slipped again", 22);
        MEDIUM_KEYWORDS.put("almost relapsed", 20);
        MEDIUM_KEYWORDS.put("close to relapse", 20);

        // Urges and conflict
        MEDIUM_KEYWORDS.put("strong urge", 22);
        MEDIUM_KEYWORDS.put("urges", 18);
        MEDIUM_KEYWORDS.put("fighting urges", 22);
        MEDIUM_KEYWORDS.put("can't resist", 22);
        MEDIUM_KEYWORDS.put("cant resist", 22);
        MEDIUM_KEYWORDS.put("temptation", 18);
        MEDIUM_KEYWORDS.put("triggered", 18);

        // Emotional signals
        MEDIUM_KEYWORDS.put("guilt", 20);
        MEDIUM_KEYWORDS.put("shame", 20);
        MEDIUM_KEYWORDS.put("feel empty", 22);
        MEDIUM_KEYWORDS.put("feel bad after", 22);
        MEDIUM_KEYWORDS.put("anxiety after", 20);
        MEDIUM_KEYWORDS.put("why can't i", 24);
        MEDIUM_KEYWORDS.put("why cant i", 24);
        MEDIUM_KEYWORDS.put("tired of this", 20);
        MEDIUM_KEYWORDS.put("sick of this", 20);

        // Quitting attempts
        MEDIUM_KEYWORDS.put("trying to quit", 22);
        MEDIUM_KEYWORDS.put("want to quit", 20);
        MEDIUM_KEYWORDS.put("quitting porn", 22);
        MEDIUM_KEYWORDS.put("stop watching porn", 22);
        MEDIUM_KEYWORDS.put("stop masturbating", 20);
        MEDIUM_KEYWORDS.put("about to relapse", 25);
        MEDIUM_KEYWORDS.put("feel like relapsing", 25);
        MEDIUM_KEYWORDS.put("might relapse", 24);
        MEDIUM_KEYWORDS.put("thinking about relapsing", 24);
        MEDIUM_KEYWORDS.put("bad habit", 16);
        MEDIUM_KEYWORDS.put("unhealthy habit", 18);
        MEDIUM_KEYWORDS.put("can't break this habit", 24);
        MEDIUM_KEYWORDS.put("cant break this habit", 24);

        // ── LOW INTENT (base detection + topic filter) ────────────────────────
        LOW_KEYWORDS.put("porn", 8);
        LOW_KEYWORDS.put("pornography", 8);
        LOW_KEYWORDS.put("masturbation", 8);
        LOW_KEYWORDS.put("masturbating", 8);
        LOW_KEYWORDS.put("masturbat", 8);
        LOW_KEYWORDS.put("fap", 8);
        LOW_KEYWORDS.put("fapping", 8);
        LOW_KEYWORDS.put("porn addiction", 10);
        LOW_KEYWORDS.put("sex addiction", 10);
        LOW_KEYWORDS.put("sexual addiction", 10);
        LOW_KEYWORDS.put("libido", 8);
        LOW_KEYWORDS.put("erectile", 8);
        LOW_KEYWORDS.put("pied", 8);
        LOW_KEYWORDS.put("nofap", 8);
        LOW_KEYWORDS.put("pornfree", 8);
        LOW_KEYWORDS.put("day 1", 8);
        LOW_KEYWORDS.put("day 0", 8);
        LOW_KEYWORDS.put("reboot", 8);
        LOW_KEYWORDS.put("pmo", 8);
        LOW_KEYWORDS.put("no pmo", 8);
        LOW_KEYWORDS.put("semen retention", 6);
        LOW_KEYWORDS.put("flatline", 8);
        LOW_KEYWORDS.put("streak", 6);

        // ── EMOTION SIGNALS ───────────────────────────────────────────────────
        EMOTION_SIGNALS.put("guilt", Arrays.asList(
                "hate myself", "ashamed", "shame", "disgusting", "feel gross",
                "regret", "feel like a failure", "i feel broken"
        ));
        EMOTION_SIGNALS.put("despair", Arrays.asList(
                "hopeless", "no hope", "give up", "i give up", "what's the point",
                "feel trapped", "feel stuck", "no one to talk to", "feel alone"
        ));
        EMOTION_SIGNALS.put("frustration", Arrays.asList(
                "i keep relapsing", "tried everything", "nothing works", "always fail",
                "why can't i", "why cant i", "never works", "i always end up"
        ));
        EMOTION_SIGNALS.put("loss of control", Arrays.asList(
                "can't stop", "cant stop", "can't control", "cant control",
                "out of control", "compulsive", "keep going back", "keep doing it"
        ));
        EMOTION_SIGNALS.put("exhaustion", Arrays.asList(
                "tired of this", "tired of relapsing", "sick of this",
                "i feel awful", "i feel horrible", "i cant keep doing this"
        ));
        EMOTION_SIGNALS.put("seeking help", Arrays.asList(
                "i need help", "please help", "any advice", "what should i do",
                "how do i stop", "how to quit", "i dont know what to do"
        ));
    }

    public void scorePost(RedditPost post) {
        String text = (post.getTitle() + " " + post.getSelftext()).toLowerCase();

        if (!isPornRelated(text)) {
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

    private boolean isPornRelated(String text) {
        List<String> coreTopics = Arrays.asList(
                "porn", "pornography", "masturbat", "fap", "fapping",
                "nofap", "no fap", "pmo", "nopmo", "reboot",
                "erectile", "pied", "death grip", "libido",
                "sexual addiction", "sex addiction", "flatline",
                "pornfree", "porn free", "porn addiction",
                "semen retention", "relapse", "urge", "streak",
                "quit porn", "stop watching", "no nut"
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
        if (text.contains("relapse") || text.contains("relapsed"))          tags.add("relapse");
        if (text.contains("day 0") || text.contains("back to zero"))        tags.add("day-0");
        if (text.contains("pied") || text.contains("erectile"))             tags.add("PIED");
        if (text.contains("urge") || text.contains("urges"))                tags.add("urge");
        if (text.contains("flatline"))                                       tags.add("flatline");
        if (text.contains("death grip"))                                     tags.add("death-grip");
        if (text.contains("shame") || text.contains("ashamed"))             tags.add("shame");
        if (text.contains("alone") || text.contains("no one to talk"))      tags.add("loneliness");
        if (text.contains("girlfriend") || text.contains("wife") || text.contains("partner")) tags.add("relationship");
        if (text.contains("brain fog"))                                      tags.add("brain-fog");
        if (text.contains("withdrawal"))                                     tags.add("withdrawal");
        if (text.contains("late night") || text.contains("cant sleep"))     tags.add("late-night");
        if (text.contains("muslim") || text.contains("islam") || text.contains("haram")) tags.add("faith");
        if (text.contains("christian") || text.contains("god") || text.contains("prayer")) tags.add("faith");
        return tags.toArray(new String[0]);
    }

    public boolean isWorthShowing(RedditPost post) {
        return post.getIntentScore() >= 20;
    }
}