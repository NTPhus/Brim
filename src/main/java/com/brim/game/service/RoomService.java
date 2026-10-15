package com.brim.game.service;

import com.brim.game.model.dto.GameRoom;
import com.brim.game.model.dto.GameSettings;
import com.brim.game.model.dto.GameType;
import com.brim.game.model.dto.Player;
import com.brim.game.model.dto.RoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private final RedisTemplate<String, Object> redisTemplate;
    @org.springframework.beans.factory.annotation.Value("${game.room.max-rooms:128}")
    private int maxRooms = 128;
    private final Map<String, GameRoom> localRooms = new ConcurrentHashMap<>();

    private static final String ROOM_PREFIX = "room:";
    private static final String ROOM_LIST_KEY = "rooms:list";

    public GameRoom createRoom(String name, GameType gameType, String hostId, int maxPlayers) {
        return createRoom(name, gameType, hostId, null, maxPlayers);
    }

    public synchronized GameRoom createRoom(String name, GameType gameType, String hostId, String hostUsername, int maxPlayers) {
        if (getAllRooms().size() >= maxRooms) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Server room limit reached");
        maxPlayers = gameType == GameType.CHESS || gameType == GameType.CHINESE_CHESS ? 2 : Math.max(1, Math.min(16, maxPlayers));
        GameRoom room = GameRoom.create(name, gameType, hostId, maxPlayers, GameSettings.defaultFor(gameType));

        Player host = new Player();
        host.setId(hostId);
        host.setUsername(hostUsername != null && !hostUsername.isBlank() ? hostUsername : "Player_" + hostId.substring(0, Math.min(4, hostId.length())));
        host.setReady(true);
        room.getPlayers().add(host);

        saveRoom(room);
        addToRoomList(room);
        log.info("Created room: {} ({}) with host {}", room.getId(), room.getName(), host.getUsername());
        return room;
    }

    public GameRoom getRoom(String roomId) {
        GameRoom room = localRooms.get(roomId);
        if (room != null) return room;

        room = (GameRoom) redisTemplate.opsForValue().get(ROOM_PREFIX + roomId);
        if (room != null) {
            localRooms.put(roomId, room);
        }
        return room;
    }

    public List<GameRoom> getAllRooms() {
        Set<Object> roomIds = redisTemplate.opsForSet().members(ROOM_LIST_KEY);
        if (roomIds == null) return List.of();

        List<GameRoom> rooms = new ArrayList<>();
        for (Object id : roomIds) {
            GameRoom room = getRoom((String) id);
            if (room != null) rooms.add(room);
        }
        return rooms;
    }

    public List<GameRoom> getRoomsByType(GameType gameType) {
        return getAllRooms().stream()
                .filter(r -> r.getGameType() == gameType)
                .toList();
    }

    public boolean joinRoom(String roomId, Player player) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;

        synchronized (room) {
            boolean alreadyInRoom = room.getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(player.getId()));
            if (!alreadyInRoom) {
                if (!room.canJoin()) return false;
                room.getPlayers().add(player);
                saveRoom(room);
            }
            return true;
        }
    }

    public boolean leaveRoom(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;

        room.getPlayers().removeIf(p -> p.getId().equals(playerId));

        if (room.getPlayers().isEmpty()) {
            deleteRoom(roomId);
        } else {
            if (room.getHostId().equals(playerId) && !room.getPlayers().isEmpty()) {
                room.setHostId(room.getPlayers().get(0).getId());
            }
            saveRoom(room);
        }
        return true;
    }

    public Player addBot(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room == null || !room.canJoin()) return null;

        long botCount = room.getPlayers().stream().filter(p -> p.getId().startsWith("bot-")).count();
        String botId = "bot-" + UUID.randomUUID().toString().substring(0, 8);
        Player bot = new Player();
        bot.setId(botId);
        bot.setUsername("Bot " + (botCount + 1) + " 🤖");
        bot.setSessionId(botId);
        bot.setReady(true);
        bot.setState(Player.PlayerState.WAITING);

        room.getPlayers().add(bot);
        saveRoom(room);
        log.info("Added bot {} to room {}", bot.getUsername(), roomId);
        return bot;
    }

    public boolean removeBot(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;

        Optional<Player> bot = room.getPlayers().stream()
                .filter(p -> p.getId().startsWith("bot-"))
                .reduce((first, second) -> second);

        if (bot.isPresent()) {
            room.getPlayers().remove(bot.get());
            saveRoom(room);
            log.info("Removed bot {} from room {}", bot.get().getUsername(), roomId);
            return true;
        }
        return false;
    }

    public void updateRoomStatus(String roomId, RoomStatus status) {
        GameRoom room = getRoom(roomId);
        if (room != null) {
            room.setStatus(status);
            if (status == RoomStatus.IN_PROGRESS) room.setStartedAt(LocalDateTime.now());
            if (status == RoomStatus.FINISHED) room.setEndedAt(LocalDateTime.now());
            saveRoom(room);
        }
    }

    public void saveRoom(GameRoom room) {
        redisTemplate.opsForValue().set(ROOM_PREFIX + room.getId(), room);
        localRooms.put(room.getId(), room);
    }

    private void addToRoomList(GameRoom room) {
        redisTemplate.opsForSet().add(ROOM_LIST_KEY, room.getId());
    }

    private void deleteRoom(String roomId) {
        redisTemplate.delete(ROOM_PREFIX + roomId);
        redisTemplate.opsForSet().remove(ROOM_LIST_KEY, roomId);
        localRooms.remove(roomId);
    }

    /**
     * Force delete a room (used by cleanup scheduler and admin).
     */
    public void forceDeleteRoom(String roomId) {
        deleteRoom(roomId);
        log.info("Force deleted room: {}", roomId);
    }

    /**
     * Delete a room if the requester is the host.
     */
    public boolean deleteRoomByHost(String roomId, String hostId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;
        if (!room.getHostId().equals(hostId)) return false;
        deleteRoom(roomId);
        log.info("Room {} deleted by host {}", roomId, hostId);
        return true;
    }
}