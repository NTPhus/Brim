package com.brim.game.service;

import com.brim.game.model.dto.GameScoreEvent;
import com.brim.game.model.dto.GameType;
import com.brim.game.model.dto.LeaderboardEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class LeaderboardService {
    private static final String KEY_PREFIX = "leaderboard:daily:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public void record(GameScoreEvent event) {
        if (event == null || event.playerId() == null || event.points() <= 0) return;
        String member = event.playerId() + "\u001f" + sanitize(event.username());
        String key = keyFor(event.gameType());
        redisTemplate.opsForZSet().incrementScore(key, member, event.points());
        // Expire shortly after the day rolls over so stale daily boards do not accumulate forever.
        redisTemplate.expire(key, java.time.Duration.ofDays(3));
        messagingTemplate.convertAndSend("/topic/leaderboard", getTop(null, 12));
        messagingTemplate.convertAndSend("/topic/leaderboard/" + event.gameType().name(), getTop(event.gameType(), 8));
    }

    public List<LeaderboardEntry> getTop(GameType filter, int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        GameType[] types = filter == null ? GameType.values() : new GameType[]{filter};
        for (GameType type : types) {
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> rows =
                    redisTemplate.opsForZSet().reverseRangeWithScores(keyFor(type), 0, Math.max(0, limit - 1));
            if (rows == null) continue;
            for (var row : rows) {
                String[] member = String.valueOf(row.getValue()).split("\u001f", 2);
                entries.add(new LeaderboardEntry(member[0], member.length > 1 ? member[1] : "Player", type,
                        row.getScore() == null ? 0 : Math.round(row.getScore())));
            }
        }
        return entries.stream().sorted(Comparator.comparingLong(LeaderboardEntry::score).reversed()).limit(limit).toList();
    }

    private String sanitize(String username) {
        return username == null || username.isBlank() ? "Player" : username.replace("\u001f", " ");
    }

    private String keyFor(GameType type) {
        return KEY_PREFIX + LocalDate.now() + ":" + type.name();
    }
}
