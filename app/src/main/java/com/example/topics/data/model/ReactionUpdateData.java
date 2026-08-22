package com.example.topics.data.model;

public class ReactionUpdateData {
    public ReactionDto reactions;
    public String userReaction;

    public ReactionDto getReactions() {
        if (reactions == null) reactions = new ReactionDto();
        return reactions;
    }
}
