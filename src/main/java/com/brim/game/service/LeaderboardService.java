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
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
public class LeaderboardService {
    private static final String KEY_PREFIX = "leaderboard:daily:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Set<GameType> dirty = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final DefaultRedisScript<Long> APPLY_SCORE = new DefaultRedisScript<>("""
        if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 0 end
        redis.call('ZINCRBY', KEYS[1], ARGV[2], ARGV[3])
        redis.call('SADD', KEYS[2], ARGV[1])
        redis.call('EXPIRE', KEYS[1], 259200)
        redis.call('EXPIRE', KEYS[2], 259200)
        return 1
        """, Long.class);

    public void record(GameScoreEvent event) {
        if (event == null || event.eventId() == null || event.playerId() == null || event.gameType() == null || event.points() <= 0) return;
        LocalDate day = Instant.ofEpochMilli(event.occurredAt()).atZone(ZONE).toLocalDate();
        LocalDate today = LocalDate.now(ZONE);
        if (day.isBefore(today.minusDays(2)) || day.isAfter(today)) return;
        String member = event.playerId() + "\u001f" + sanitize(event.username());
        String key = KEY_PREFIX + day + ":" + event.gameType().name();
        Long applied = redisTemplate.execute(APPLY_SCORE, List.of(key, "leaderboard:events:" + day),
                event.eventId(), event.points(), member);
        if (Long.valueOf(1).equals(applied)) dirty.add(event.gameType());
    }

    @Scheduled(fixedDelay = 1000)
    public void broadcastChangedBoards() {
        boolean changed = false;
        for (GameType type : GameType.values()) {
            if (dirty.remove(type)) {
                changed = true;
                messagingTemplate.convertAndSend("/topic/leaderboard/" + type.name(), getTop(type, 8));
            }
        }
        if (changed) messagingTemplate.convertAndSend("/topic/leaderboard", getTop(null, 12));
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
        return KEY_PREFIX + LocalDate.now(ZONE) + ":" + type.name();
    }
}
