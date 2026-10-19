package com.brim.game.security;

import com.brim.game.config.RedisConfig;
import com.brim.game.model.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PlayerPrivacyTest {
    @Test void sessionIdIsReadableFromLegacyInputButAbsentFromOutgoingJson() throws Exception {
        var mapper = new ObjectMapper();
        var player = mapper.readValue("{\"id\":\"human\",\"sessionId\":\"legacy-stomp-id\"}", Player.class);
        assertEquals("legacy-stomp-id", player.getSessionId());
        assertFalse(mapper.valueToTree(player).has("sessionId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisRoomRoundTripPreservesMembershipWithoutPersistingTransportSessionId() {
        var redis = new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));
        var serializer = (RedisSerializer<Object>) redis.getValueSerializer();
        var room = GameRoom.create("Private", GameType.CHESS, "human", 2, GameSettings.defaultFor(GameType.CHESS));
        var player = new Player(); player.setId("human"); player.setSessionId("private-stomp-session");
        room.getPlayers().add(player);
        byte[] bytes = serializer.serialize(room);
        assertFalse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains("private-stomp-session"));
        var restored = assertInstanceOf(GameRoom.class, serializer.deserialize(bytes));
        assertEquals(room.getId(), restored.getId());
        assertEquals("human", restored.getPlayers().get(0).getId());
        assertNull(restored.getPlayers().get(0).getSessionId());
        assertTrue(restored.hasHumanPlayers());
    }
}
