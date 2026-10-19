package com.brim.game.service;

import com.brim.game.kafka.GameEventProducer;
import com.brim.game.model.dto.*;
import com.brim.game.runtime.*;
import com.brim.game.websocket.SnakeGameController;
import com.brim.game.websocket.TankGameController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomLifecycleTest {
    private AnnotationConfigApplicationContext context;
    private RoomService rooms;
    private GameRuntime runtime;
    private final Map<String, Object> stored = new HashMap<>();
    private final Set<Object> listed = new HashSet<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        SetOperations<String, Object> sets = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(anyString(), any());
        when(redis.delete(anyString())).thenAnswer(call -> stored.remove(call.getArgument(0)) != null);
        when(sets.members("rooms:list")).thenAnswer(call -> new HashSet<>(listed));
        when(sets.add(eq("rooms:list"), any())).thenAnswer(call -> listed.add(call.getArgument(1)) ? 1L : 0L);
        when(sets.remove(eq("rooms:list"), any())).thenAnswer(call -> listed.remove(call.getArgument(1)) ? 1L : 0L);

        TurnBasedGameRegistry registry = mock(TurnBasedGameRegistry.class);
        when(registry.create(any(), anyString(), anyString())).thenAnswer(call -> {
            var game = mock(TurnBasedGameService.TurnBasedGame.class);
            when(game.getState()).thenReturn(Map.of());
            when(game.getCurrentPlayerId()).thenReturn("human");
            return game;
        });
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "game.snake.grid-width", 800, "game.snake.grid-height", 600,
                "game.tank.map-width", 800, "game.tank.map-height", 600)));
        context.registerBean(RedisTemplate.class, () -> redis);
        context.registerBean(GameRuntime.class, () -> new GameRuntime(1, 8));
        context.registerBean(BotExecutor.class, () -> mock(BotExecutor.class));
        context.registerBean(GameEventProducer.class, () -> mock(GameEventProducer.class));
        context.registerBean(SimpMessagingTemplate.class, () -> mock(SimpMessagingTemplate.class));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(TurnBasedGameRegistry.class, () -> registry);
        context.register(RoomService.class, TurnBasedGameService.class, SnakeGameController.class, TankGameController.class,
                com.brim.game.config.WebSocketEventListener.class);
        context.refresh();
        rooms = context.getBean(RoomService.class);
        runtime = context.getBean(GameRuntime.class);
    }

    @AfterEach void tearDown() { context.close(); }

    @ParameterizedTest
    @EnumSource(GameType.class)
    void lastHumanLeavingDeletesRoomAndReleasesRunningGame(GameType type) {
        GameRoom room = rooms.createRoom("Test", type, "human", 4);
        if (!type.isSolo()) assertNotNull(rooms.addBot(room.getId()));
        start(room);
        assertTrue(type == GameType.SNAKE || type == GameType.TANK
                ? runtime.roomCount() == 1 : context.getBean(TurnBasedGameService.class).getGame(room.getId()) != null);

        assertTrue(rooms.leaveRoom(room.getId(), "human"));

        assertNull(rooms.getRoom(room.getId()));
        assertTrue(rooms.getAllRooms().isEmpty());
        assertFalse(stored.containsKey("room:" + room.getId()));
        assertEquals(0, runtime.roomCount());
        assertNull(context.getBean(TurnBasedGameService.class).getGame(room.getId()));
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(context.getBean(SnakeGameController.class), "activeGames")).isEmpty());
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(context.getBean(TankGameController.class), "activeGames")).isEmpty());
        context.getBean(TurnBasedGameService.class).resumeWaitingBots();
        verify(context.getBean(BotExecutor.class), never()).submit(any());
    }

    @Test void roomWithAnotherHumanStaysActiveAndTransfersHostToHuman() {
        GameRoom room = rooms.createRoom("Test", GameType.SNAKE, "human", 4);
        rooms.addBot(room.getId());
        Player other = new Player();
        other.setId("other-human");
        assertTrue(rooms.joinRoom(room.getId(), other));
        start(room);

        rooms.leaveRoom(room.getId(), "human");

        assertSame(room, rooms.getRoom(room.getId()));
        assertEquals("other-human", room.getHostId());
        assertEquals(1, runtime.roomCount());
        assertFalse(rooms.deleteRoomIfNoHumans(room.getId()));
    }

    @Test void soloPuzzleHasOneSeatAndProgressPersistsOutsideRoomWithoutDowngradingUnlocks() {
        var room = rooms.createRoom("Puzzle", GameType.MATCH_THREE, "human", 9);
        assertEquals(1, room.getMaxPlayers());
        assertNull(rooms.addBot(room.getId()));
        rooms.savePuzzleProgress("human", Map.of("match3Unlocked", 4, "match3Level", 3));
        rooms.savePuzzleProgress("human", Map.of("match3Unlocked", 2, "match3Level", 1));
        rooms.leaveRoom(room.getId(), "human");
        assertEquals(4, rooms.puzzleProgress("human").get("match3Unlocked"));
        assertEquals(1, rooms.puzzleProgress("human").get("match3Level"));
        assertTrue(rooms.puzzleProgress("other").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = GameType.class, names = {"MATCH_THREE", "WARLORDS_CAMPAIGN", "VIETNAM_CAMPAIGN"}, mode = EnumSource.Mode.EXCLUDE)
    void botOnlyRoomCannotStart(GameType type) {
        GameRoom room = rooms.createRoom("Test", type, "human", 4);
        Player bot = rooms.addBot(room.getId());
        room.getPlayers().removeIf(p -> p.getId().equals("human"));
        var ready = GameMessage.<Void>of(MessageType.PLAYER_READY, room.getId(), bot.getId(), null);
        switch (type) {
            case SNAKE -> context.getBean(SnakeGameController.class).playerReady(room.getId(), ready);
            case TANK -> context.getBean(TankGameController.class).playerReady(room.getId(), ready);
            default -> context.getBean(TurnBasedGameService.class).initializeGame(room);
        }
        assertNull(rooms.getRoom(room.getId()));
        assertEquals(0, runtime.roomCount());
        verify(context.getBean(BotExecutor.class), never()).submit(any());
    }

    @Test void cleanupReleasesAnAlreadyRunningBotOnlyRoom() {
        GameRoom room = rooms.createRoom("Test", GameType.TANK, "human", 4);
        rooms.addBot(room.getId());
        start(room);
        room.getPlayers().removeIf(p -> p.getId().equals("human"));
        new RoomCleanupScheduler(rooms, context.getBean(TurnBasedGameService.class)).cleanupStaleRooms();
        assertNull(rooms.getRoom(room.getId()));
        assertEquals(0, runtime.roomCount());
    }

    @Test void disconnectFromMultipleRoomsDeletesBothAndStopsTheirTicksAfterGrace() {
        var listener = context.getBean(com.brim.game.config.WebSocketEventListener.class);
        String socket = "lifecycle-socket";
        GameRoom first = rooms.createRoom("First", GameType.SNAKE, "human", 4);
        GameRoom second = rooms.createRoom("Second", GameType.TANK, "human", 4);
        for (GameRoom room : List.of(first, second)) {
            rooms.addBot(room.getId()); start(room);
            com.brim.game.config.WebSocketEventListener.bindSession(socket, room.getId(), "human");
        }
        assertEquals(2, runtime.roomCount());
        var headers = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(
                org.springframework.messaging.simp.stomp.StompCommand.DISCONNECT);
        headers.setSessionId(socket);
        var message = org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        listener.handleSessionDisconnect(new org.springframework.web.socket.messaging.SessionDisconnectEvent(
                this, message, socket, org.springframework.web.socket.CloseStatus.NORMAL));
        listener.expireDisconnectedPlayers();
        assertEquals(2, runtime.roomCount(), "Keep the reconnect grace period");
        ReflectionTestUtils.invokeMethod(listener, "expireDisconnectedPlayers", Long.MAX_VALUE);
        assertNull(rooms.getRoom(first.getId()));
        assertNull(rooms.getRoom(second.getId()));
        assertEquals(0, runtime.roomCount());
    }

    private void start(GameRoom room) {
        room.getPlayers().forEach(p -> p.setReady(true));
        var join = GameMessage.of(MessageType.JOIN_ROOM, room.getId(), "human", room.getPlayers().get(0));
        switch (room.getGameType()) {
            case SNAKE -> context.getBean(SnakeGameController.class).joinSnakeGame(room.getId(), join, SimpMessageHeaderAccessor.create());
            case TANK -> context.getBean(TankGameController.class).joinTankGame(room.getId(), join, SimpMessageHeaderAccessor.create());
            default -> context.getBean(TurnBasedGameService.class).initializeGame(room);
        }
    }
}
