package com.brim.game.model.dto;

public record GameScoreEvent(String playerId, String username, GameType gameType, int points) {}
