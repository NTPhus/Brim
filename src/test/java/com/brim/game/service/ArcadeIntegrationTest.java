package com.brim.game.service;

import com.brim.game.config.BoardGameModules;
import com.brim.game.game.battleship.BattleshipGame;
import com.brim.game.kafka.GameEventProducer;
import com.brim.game.model.dto.*;
import com.brim.game.runtime.*;
import com.brim.game.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArcadeIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private RoomService rooms;
    private SimpMessagingTemplate socket;
    private GameRuntime runtime;
    private TurnBasedGameService service;
    private GameRoom room;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(BoardGameModules.class,TurnBasedGameRegistry.class);
        rooms=mock(RoomService.class);socket=mock(SimpMessagingTemplate.class);runtime=mock(GameRuntime.class);
        when(runtime.start(anyString(),anyLong(),any())).thenReturn(true);
        service=new TurnBasedGameService(rooms,mock(GameEventProducer.class),socket,new ObjectMapper(),mock(BotExecutor.class),context.getBean(TurnBasedGameRegistry.class),runtime);
    }
    @AfterEach void close() {context.close();}
    private void start(GameType type) {
        room=GameRoom.create("test",type,"a",type.maximumPlayers(),GameSettings.defaultFor(type));room.setId("room");
        for(String id:type==GameType.MATCH_THREE?List.of("a"):List.of("a","b")) {
            var player=new Player();player.setId(id);player.setUsername(id);room.getPlayers().add(player);
        }
        when(rooms.getRoom("room")).thenReturn(room);service.initializeGame(room);
    }
    @Test void battleshipTransportStaysPrivateThroughFinalStateAndReconnect() throws Exception {
        start(GameType.BATTLESHIP);
        for(String id:List.of("a","b"))for(int i=0;i<5;i++)service.processMove("room",id,Map.of("action","PLACE","row",i*2,"column",0,"horizontal",true));
        var game=(BattleshipGame)service.getGame("room");
        assertEquals("BATTLE",game.getState("a").phase());
        int misses=0;
        for(int ship=0;ship<5;ship++)for(int c=0;c<BattleshipGame.FLEET.get(ship);c++) {
            service.processMove("room","a",Map.of("action","FIRE","row",ship*2,"column",c));
            if(!game.isGameOver()) {service.processMove("room","b",Map.of("action","FIRE","row",misses<10?9:7,"column",misses%10));misses++;}
        }
        assertNull(service.getGame("room"));assertEquals(RoomStatus.FINISHED,room.getStatus());
        verify(socket,never()).convertAndSend(eq("/topic/game/room/state"),any(Object.class));
        verify(socket,never()).convertAndSend(eq("/topic/game/room/over"),any(Object.class));
        clearInvocations(socket);service.sendState("room");
        verify(socket).convertAndSend(eq("/topic/game/room/state/a"),any(BattleshipGame.State.class));
        verify(socket).convertAndSend(eq("/topic/game/room/state/b"),any(BattleshipGame.State.class));
        verify(socket,never()).convertAndSend(eq("/topic/game/room/state"),any(Object.class));
        verify(socket,never()).convertAndSend(eq("/topic/game/room/over"),any(Object.class));
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(room).contains("ownBoard"));
    }
    @Test void subscriptionGuardDeniesEnemyPrivateStateAndSharedBattleshipState() {
        start(GameType.BATTLESHIP);
        var guard=new GameMessageGuard(new RoomAccess(rooms),mock(RequestLimits.class),new ObjectMapper());
        assertDoesNotThrow(()->guard.authorizeSubscription("/topic/game/room/state/a","a"));
        assertThrows(ResponseStatusException.class,()->guard.authorizeSubscription("/topic/game/room/state/b","a"));
        assertThrows(ResponseStatusException.class,()->guard.authorizeSubscription("/topic/game/room/state","a"));
        assertThrows(ResponseStatusException.class,()->guard.authorizeSubscription("/topic/game/room/over","a"));
        assertThrows(ResponseStatusException.class,()->guard.authorizeSubscription("/topic/game/room/state/a","outsider"));
    }
    @Test void bombermanUsesSharedRuntimeAndOldTickCannotEndRestartedGame() {
        start(GameType.BOMBERMAN);
        var callback=ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(runtime).start(eq("room"),eq(100L),callback.capture());
        var old=service.getGame("room");
        service.initializeGame(room);
        assertNotSame(old,service.getGame("room"));assertFalse(callback.getValue().getAsBoolean());
        service.onRoomDeleted(new RoomDeletedEvent("room"));
        verify(runtime).stop("room");assertNull(service.getGame("room"));
    }
    @Test void runtimeCapacityRejectionLeavesRoomWaitingAndNoActiveGame() {
        when(runtime.start(anyString(),anyLong(),any())).thenReturn(false);
        start(GameType.BOMBERMAN);
        assertEquals(RoomStatus.WAITING,room.getStatus());assertNull(service.getGame("room"));
    }
    @Test void soloPuzzleRestoresSavedLevelAndPersistsCheckpointOnRetry() {
        when(rooms.puzzleProgress("a")).thenReturn(Map.of("match3Unlocked",4,"match3Level",3));
        start(GameType.MATCH_THREE);
        var game=(com.brim.game.game.matchthree.MatchThreeGame)service.getGame("room");
        assertEquals(3,game.getState().level());
        service.processMove("room","a",Map.of("action","RETRY"));
        verify(rooms).savePuzzleProgress(eq("a"),eq(Map.of("match3Unlocked",4,"match3Level",3)));
    }
}
