package com.brim.game.service;

import com.brim.game.config.BoardGameModules;
import com.brim.game.game.connectfour.ConnectFourGame;
import com.brim.game.kafka.GameEventProducer;
import com.brim.game.model.dto.*;
import com.brim.game.runtime.*;
import com.brim.game.security.RoomAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConnectFourIntegrationTest {
    @Test void registeredGameProcessesTwoPlayersAndPublishesResult() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(BoardGameModules.class, TurnBasedGameRegistry.class);
            context.refresh();
            var rooms = mock(RoomService.class);
            var events = mock(GameEventProducer.class);
            var socket = mock(SimpMessagingTemplate.class);
            var service = new TurnBasedGameService(rooms, events, socket, new ObjectMapper(),
                    mock(BotExecutor.class), context.getBean(TurnBasedGameRegistry.class), mock(GameRuntime.class));
            var room = new GameRoom();
            room.setId("room");
            room.setGameType(GameType.CONNECT_FOUR);
            room.setSettings(GameSettings.defaultFor(GameType.CONNECT_FOUR));
            for (String id : new String[]{"red", "yellow"}) {
                var player = new Player();
                player.setId(id);
                player.setUsername(id);
                room.getPlayers().add(player);
            }
            when(rooms.getRoom("room")).thenReturn(room);
            assertEquals(2, room.getSettings().getMaxPlayers());
            assertEquals(GameType.CONNECT_FOUR, GameType.fromCode("connect_four"));
            assertDoesNotThrow(() -> new RoomAccess(rooms).gameKind(room, "turn"));
            service.initializeGame(room);
            assertEquals(RoomStatus.IN_PROGRESS, room.getStatus());
            var game = (ConnectFourGame) service.getGame("room");
            service.processMove("room", "yellow", Map.of("column", 0));
            assertEquals(0, game.getState().moveCount());
            for (int column : new int[]{0, 6, 1, 6, 2, 5, 3}) {
                service.processMove("room", game.getCurrentPlayerId(), Map.of("column", column));
            }
            assertEquals("red", game.getWinner());
            assertEquals(RoomStatus.FINISHED, room.getStatus());
            assertNull(service.getGame("room"));
            verify(socket, atLeastOnce()).convertAndSend(eq("/topic/game/room/state"), any(ConnectFourGame.State.class));
            verify(socket).convertAndSend(eq("/topic/game/room/over"), any(GameMessage.class));
            verify(events, times(2)).sendLeaderboardScore(eq("room"), any(GameScoreEvent.class));
        }
    }
}
