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

@Service
@RequiredArgsConstructor
public class LeaderboardService {
    private static final String KEY_PREFIX = "leaderboard:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public void record(GameScoreEvent event) {
        if (event == null || event.playerId() == null || event.playerId().startsWith("bot-") || event.points() <= 0) return;
        String member = event.playerId() + "\u001f" + sanitize(event.username());
        redisTemplate.opsForZSet().incrementScore(KEY_PREFIX + event.gameType().name(), member, event.points());
        messagingTemplate.convertAndSend("/topic/leaderboard", getTop(null, 12));
        messagingTemplate.convertAndSend("/topic/leaderboard/" + event.gameType().name(), getTop(event.gameType(), 8));
    }

    public List<LeaderboardEntry> getTop(GameType filter, int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        GameType[] types = filter == null ? GameType.values() : new GameType[]{filter};
        for (GameType type : types) {
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> rows =
                    redisTemplate.opsForZSet().reverseRangeWithScores(KEY_PREFIX + type.name(), 0, Math.max(0, limit - 1));
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
}
