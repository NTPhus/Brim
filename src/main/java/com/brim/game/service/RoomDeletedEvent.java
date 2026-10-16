package com.brim.game.service;

/** Releases simulations and scheduled work whenever a room is deleted. */
public record RoomDeletedEvent(String roomId) {}
