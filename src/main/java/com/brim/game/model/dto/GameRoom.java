package com.brim.game.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class GameRoom {
    private String id;
    private String name;
    private GameType gameType;
    private RoomStatus status;
    private List<Player> players = new CopyOnWriteArrayList<>();
    private int maxPlayers;
    private String hostId;
    private GameSettings settings;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public static GameRoom create(String name, GameType gameType, String hostId, int maxPlayers, GameSettings settings) {
        GameRoom room = new GameRoom();
        room.id = UUID.randomUUID().toString();
        room.name = name;
        room.gameType = gameType;
        room.status = RoomStatus.WAITING;
        room.maxPlayers = maxPlayers;
        room.hostId = hostId;
        room.settings = settings;
        room.createdAt = LocalDateTime.now();
        return room;
    }

    public List<Player> getPlayers() {
        if (players == null) players = new CopyOnWriteArrayList<>();
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = new CopyOnWriteArrayList<>(players == null ? List.of() : players);
    }

    public boolean canJoin() {
        return (status == RoomStatus.WAITING || (status == RoomStatus.IN_PROGRESS
                && (gameType == GameType.SNAKE || gameType == GameType.TANK)))
                && getPlayers().size() < maxPlayers;
    }

    @JsonIgnore
    public boolean hasHumanPlayers() {
        return getPlayers().stream().anyMatch(player -> player.getId() != null
                && !player.getId().startsWith("bot-"));
    }

    @JsonIgnore
    public boolean isFull() {
        return getPlayers().size() >= maxPlayers;
    }
}
