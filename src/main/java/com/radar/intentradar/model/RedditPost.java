package com.radar.intentradar.model;

import lombok.Data;

@Data
public class RedditPost {

    private String id;
    private String title;
    private String selftext;
    private String author;
    private String subreddit;
    private long createdUtc;
    private int score;
    private int numComments;
    private String url;
    private String handledBy;
    private int intentScore;
    private String intentLevel;
    private String detectedEmotions;
    private String[] tags;

    private boolean dismissed;
    private String savedReply;
}