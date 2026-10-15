package com.brim.game.service;

import com.brim.game.config.RedisConfig;
import com.brim.game.model.dto.GameScoreEvent;
import com.brim.game.model.dto.GameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in with BRIM_REDIS_TEST_PORT pointing to a disposable Redis instance. */
@EnabledIfEnvironmentVariable(named = "BRIM_REDIS_TEST_PORT", matches = "[0-9]+")
class LeaderboardRedisTest {
    @Test void duplicateEventIsAppliedOnceAndDistinctEventStillAddsPoints() {
        var factory = new LettuceConnectionFactory("localhost", Integer.parseInt(System.getenv("BRIM_REDIS_TEST_PORT")));
        factory.afterPropertiesSet(); factory.start();
        try {
            var redis = new RedisConfig().redisTemplate(factory);
            var service = new LeaderboardService(redis, new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
            String id = "test-" + UUID.randomUUID();
            var event = new GameScoreEvent(id, "Student", GameType.TANK, 100);
            service.record(event); service.record(event);
            assertEquals(100, service.getTop(GameType.TANK, 100).stream().filter(e -> e.playerId().equals(id)).findFirst().orElseThrow().score());
            service.record(new GameScoreEvent(id, "Student", GameType.TANK, 50));
            assertEquals(150, service.getTop(GameType.TANK, 100).stream().filter(e -> e.playerId().equals(id)).findFirst().orElseThrow().score());
            String day = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
            assertTrue(redis.getExpire("leaderboard:events:" + day) > 0);
            service.broadcastChangedBoards(); // Redis results can also be serialized and broadcast.
        } finally { factory.destroy(); }
    }
    @Test void replayUsesEventDayAndRejectsExpiredEvents() {
        var factory = new LettuceConnectionFactory("localhost", Integer.parseInt(System.getenv("BRIM_REDIS_TEST_PORT")));
        factory.afterPropertiesSet(); factory.start();
        try {
            var redis = new RedisConfig().redisTemplate(factory);
            var service = new LeaderboardService(redis, new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
            String player = "test-" + UUID.randomUUID();
            LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);
            long when = yesterday.atTime(12, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
            service.record(new GameScoreEvent(UUID.randomUUID().toString(), player, "Student", GameType.TANK, 100, when));
            assertEquals(100d, redis.opsForZSet().score("leaderboard:daily:" + yesterday + ":TANK", player + "\u001fStudent"));
            assertTrue(service.getTop(GameType.TANK, 100).stream().noneMatch(e -> e.playerId().equals(player)));
            String stale = UUID.randomUUID().toString();
            service.record(new GameScoreEvent(stale, player, "Student", GameType.TANK, 100, when - 5 * 86400000L));
            assertFalse(Boolean.TRUE.equals(redis.hasKey("leaderboard:events:" + yesterday.minusDays(5))));
        } finally { factory.destroy(); }
    }
}
