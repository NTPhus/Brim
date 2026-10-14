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
import java.util.UUID;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomRestController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ResponseEntity<GameRoom> createRoom(@RequestBody CreateRoomRequest request) {
        String hostId = request.hostId() != null && !request.hostId().isBlank()
                ? request.hostId()
                : UUID.randomUUID().toString();
        GameRoom room = roomService.createRoom(
                request.name(),
                request.gameType(),
                hostId,
                request.hostUsername(),
                request.maxPlayers()
        );
        String difficulty = request.difficulty() == null ? "MEDIUM" : request.difficulty().toUpperCase();
        if (!List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) difficulty = "MEDIUM";
        room.getSettings().getCustomSettings().put("difficulty", difficulty);
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
    public ResponseEntity<GameRoom> getRoom(@PathVariable String roomId) {
        GameRoom room = roomService.getRoom(roomId);
        return room != null ? ResponseEntity.ok(room) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<GameRoom> joinRoom(@PathVariable String roomId, @RequestBody JoinRequest request) {
        Player player = new Player();
        String id = request.playerId() != null && !request.playerId().isBlank()
                ? request.playerId()
                : request.sessionId();
        player.setId(id);
        player.setUsername(request.username());
        player.setSessionId(request.sessionId());

        boolean success = roomService.joinRoom(roomId, player);
        if (!success) return ResponseEntity.badRequest().build();

        GameRoom room = roomService.getRoom(roomId);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId, @RequestParam String playerId) {
        roomService.leaveRoom(roomId, playerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/ready")
    public ResponseEntity<Void> setReady(@PathVariable String roomId, @RequestParam String playerId, @RequestParam boolean ready) {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();

        room.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .ifPresent(p -> p.setReady(ready));

        roomService.saveRoom(room);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/bot")
    public ResponseEntity<GameRoom> addBot(@PathVariable String roomId) {
        Player bot = roomService.addBot(roomId);
        if (bot == null) return ResponseEntity.badRequest().build();

        GameRoom room = roomService.getRoom(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/{roomId}/bot")
    public ResponseEntity<GameRoom> removeBot(@PathVariable String roomId) {
        boolean removed = roomService.removeBot(roomId);
        if (!removed) return ResponseEntity.badRequest().build();

        GameRoom room = roomService.getRoom(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId, @RequestParam String hostId) {
        boolean deleted = roomService.deleteRoomByHost(roomId, hostId);
        if (!deleted) return ResponseEntity.badRequest().build();
        // Notify remaining subscribers that room is gone
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/deleted", roomId);
        return ResponseEntity.ok().build();
    }

    public record CreateRoomRequest(String name, GameType gameType, int maxPlayers, String hostId, String hostUsername, String difficulty) {}
    public record JoinRequest(String username, String sessionId, String playerId) {}
}
