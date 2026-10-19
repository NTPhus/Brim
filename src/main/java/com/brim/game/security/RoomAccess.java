package com.brim.game.security;

import com.brim.game.model.dto.GameRoom;
import com.brim.game.model.dto.GameType;
import com.brim.game.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class RoomAccess {
    private final RoomService rooms;
    public GameRoom room(String roomId) {
        GameRoom room = rooms.getRoom(roomId);
        if (room == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        return room;
    }
    public GameRoom member(String roomId, String playerId) {
        GameRoom room = room(roomId);
        if (room.getPlayers().stream().noneMatch(player -> playerId.equals(player.getId()))) deny();
        return room;
    }
    public GameRoom host(String roomId, String playerId) {
        GameRoom room = member(roomId, playerId);
        if (!playerId.equals(room.getHostId())) deny();
        return room;
    }
    public void claimedIdentity(String claimed, String actual) {
        if (claimed != null && !claimed.equals(actual)) deny();
    }
    public void gameKind(GameRoom room, String kind) {
        boolean matches = switch (kind) {
            case "snake" -> room.getGameType() == GameType.SNAKE;
            case "tank" -> room.getGameType() == GameType.TANK;
            case "turn" -> !room.getGameType().isRealTime() || room.getGameType() == GameType.BOMBERMAN || room.getGameType() == GameType.VIETNAM_CAMPAIGN;
            default -> false;
        };
        if (!matches) deny();
    }
    public static void deny() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed"); }
}
