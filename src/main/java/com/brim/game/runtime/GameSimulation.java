package com.brim.game.runtime;

import com.brim.game.model.dto.Player;

/** Rules and snapshots belong to a simulation; transport and scheduling stay outside it. */
public interface GameSimulation<S> {
    String getRoomId();
    void addPlayer(Player player);
    void removePlayer(String playerId);
    Object update();
    S getGameState(String viewerId);
}
