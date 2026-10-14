package com.brim.game.model.dto;

public record LeaderboardEntry(String playerId, String username, GameType gameType, long score) {}
