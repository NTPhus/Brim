package com.brim.game.model.dto;

public enum MessageType {
    // Room management
    JOIN_ROOM,
    LEAVE_ROOM,
    ROOM_STATE,
    PLAYER_JOINED,
    PLAYER_LEFT,
    PLAYER_READY,
    START_GAME,
    GAME_STARTED,

    // Real-time game actions
    MOVE,
    SHOOT,
    TURN,
    ACTION,

    // Game state updates
    STATE_UPDATE,
    GAME_OVER,
    SCORE_UPDATE,

    // Turn-based
    TURN_START,
    TURN_END,
    MAKE_MOVE,
    MOVE_RESULT,

    // Error
    ERROR
}