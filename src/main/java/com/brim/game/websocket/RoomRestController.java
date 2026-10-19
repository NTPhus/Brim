package com.brim.game.websocket;

import com.brim.game.model.dto.GameRoom;
import com.brim.game.model.dto.GameSettings;
import com.brim.game.model.dto.GameType;
import com.brim.game.model.dto.Player;
import com.brim.game.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.brim.game.security.GuestSession;
import com.brim.game.security.RoomAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomRestController {

    private final RoomService roomService;
    private final RoomAccess access;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ResponseEntity<GameRoom> createRoom(@Valid @RequestBody CreateRoomRequest request, HttpServletRequest http) {
        var guest = GuestSession.require(http.getSession(false));
        access.claimedIdentity(request.hostId(), guest.id());
        String hostId = guest.id();
        if (roomService.getAllRooms().stream().filter(room -> hostId.equals(room.getHostId())).count() >= 3)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Room limit per player reached");
        GameRoom room = roomService.createRoom(
                request.name(),
                request.gameType(),
                hostId,
                guest.username(),
                request.maxPlayers()
        );
        String difficulty = request.difficulty() == null ? "MEDIUM" : request.difficulty().toUpperCase();
        if (!List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) difficulty = "MEDIUM";
        room.getSettings().getCustomSettings().put("difficulty", difficulty);
        room.getSettings().getCustomSettings().put("mapId", com.brim.game.game.tank.TankMaps.parse(request.mapId()).name());
        room.getSettings().getCustomSettings().put("tankMode", com.brim.game.game.tank.TankGame.Mode.parse(request.tankMode()).name());
        room.getSettings().getCustomSettings().put("vietnamChapter", Math.max(1, Math.min(3, request.vietnamChapter() == null ? 1 : request.vietnamChapter())));
        roomService.saveRoom(room);
        return ResponseEntity.ok(room);
    }

    @GetMapping
    public ResponseEntity<List<GameRoom>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/type/{gameType}")
    public ResponseEntity<List<GameRoom>> getRoomsByType(@PathVariable GameType gameType) {
        return ResponseEntity.ok(roomService.getRoomsByType(gameType));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<GameRoom> getRoom(@PathVariable String roomId, HttpServletRequest http) {
        GameRoom room = access.member(roomId, GuestSession.require(http.getSession(false)).id());
        return room != null ? ResponseEntity.ok(room) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<GameRoom> joinRoom(@PathVariable String roomId, @RequestBody JoinRequest request, HttpServletRequest http) {
        var guest = GuestSession.require(http.getSession(false));
        access.claimedIdentity(request.playerId(), guest.id());
        Player player = new Player();
        player.setId(guest.id());
        player.setUsername(guest.username());
        player.setSessionId(null);

        boolean success = roomService.joinRoom(roomId, player);
        if (!success) return ResponseEntity.badRequest().build();

        GameRoom room = roomService.getRoom(roomId);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId, @RequestParam(required = false) String playerId, HttpServletRequest http) {
        String actual = GuestSession.require(http.getSession(false)).id();
        access.claimedIdentity(playerId, actual);
        access.member(roomId, actual);
        roomService.leaveRoom(roomId, actual);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/ready")
    public ResponseEntity<Void> setReady(@PathVariable String roomId, @RequestParam(required = false) String playerId, @RequestParam boolean ready, HttpServletRequest http) {
        String actual = GuestSession.require(http.getSession(false)).id();
        access.claimedIdentity(playerId, actual);
        GameRoom room = access.member(roomId, actual);

        room.getPlayers().stream()
                .filter(p -> p.getId().equals(actual))
                .findFirst()
                .ifPresent(p -> p.setReady(ready));

        roomService.saveRoom(room);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/bot")
    public ResponseEntity<GameRoom> addBot(@PathVariable String roomId, HttpServletRequest http) {
        access.host(roomId, GuestSession.require(http.getSession(false)).id());
        Player bot = roomService.addBot(roomId);
        if (bot == null) return ResponseEntity.badRequest().build();

        GameRoom room = roomService.getRoom(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/{roomId}/bot")
    public ResponseEntity<GameRoom> removeBot(@PathVariable String roomId, HttpServletRequest http) {
        access.host(roomId, GuestSession.require(http.getSession(false)).id());
        boolean removed = roomService.removeBot(roomId);
        if (!removed) return ResponseEntity.badRequest().build();

        GameRoom room = roomService.getRoom(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId, @RequestParam(required = false) String hostId, HttpServletRequest http) {
        String actual = GuestSession.require(http.getSession(false)).id();
        access.claimedIdentity(hostId, actual);
        access.host(roomId, actual);
        boolean deleted = roomService.deleteRoomByHost(roomId, actual);
        if (!deleted) return ResponseEntity.badRequest().build();
        // Notify remaining subscribers that room is gone
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/deleted", roomId);
        return ResponseEntity.ok().build();
    }

    public record CreateRoomRequest(@NotBlank @Size(max = 80) String name, @NotNull GameType gameType, int maxPlayers, String hostId, String hostUsername, String difficulty, String mapId, String tankMode, Integer vietnamChapter) {}
    public record JoinRequest(String username, String sessionId, String playerId) {}
}
