package com.brim.game.service;

import com.brim.game.model.dto.GameRoom;
import com.brim.game.model.dto.RoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically cleans up rooms that are:
 * - FINISHED or CANCELLED for more than 5 minutes
 * - WAITING with no players
 * - WAITING for more than 30 minutes (stale)
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RoomCleanupScheduler {

    private final RoomService roomService;
    private final TurnBasedGameService turnBasedGameService;

    /**
     * Run cleanup every 2 minutes.
     */
    @Scheduled(fixedRate = 120_000, initialDelay = 60_000)
    public void cleanupStaleRooms() {
        List<GameRoom> allRooms = roomService.getAllRooms();
        LocalDateTime now = LocalDateTime.now();
        int cleaned = 0;

        for (GameRoom room : allRooms) {
            boolean shouldDelete = false;
            String reason = "";

            // 1. Empty rooms (no players)
            if (room.getPlayers() == null || room.getPlayers().stream().noneMatch(p -> !p.getId().startsWith("bot-"))) {
                shouldDelete = true;
                reason = "empty (no players)";
            }

            // 2. FINISHED/CANCELLED rooms older than 5 minutes
            if (!shouldDelete && (room.getStatus() == RoomStatus.FINISHED || room.getStatus() == RoomStatus.CANCELLED)) {
                LocalDateTime endTime = room.getEndedAt() != null ? room.getEndedAt() : room.getCreatedAt();
                if (endTime != null && endTime.plusMinutes(5).isBefore(now)) {
                    shouldDelete = true;
                    reason = "finished/cancelled > 5 min ago";
                }
            }

            // 3. WAITING rooms older than 30 minutes (stale)
            if (!shouldDelete && room.getStatus() == RoomStatus.WAITING) {
                if (room.getCreatedAt() != null && room.getCreatedAt().plusMinutes(30).isBefore(now)) {
                    shouldDelete = true;
                    reason = "waiting > 30 min (stale)";
                }
            }

            if (shouldDelete) {
                log.info("Cleaning up room {} ({}): {}", room.getId(), room.getName(), reason);
                turnBasedGameService.removeGame(room.getId());
                roomService.forceDeleteRoom(room.getId());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("Room cleanup: deleted {} stale rooms", cleaned);
        }
    }
}
