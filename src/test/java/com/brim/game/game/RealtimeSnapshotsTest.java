package com.brim.game.game;

import com.brim.game.game.snake.SnakeGame;
import com.brim.game.game.tank.TankGame;
import com.brim.game.game.tank.TankMaps;
import com.brim.game.model.dto.Player;
import com.brim.game.model.dto.GameRoom;
import com.brim.game.model.dto.GameSettings;
import com.brim.game.model.dto.GameType;
import com.brim.game.model.dto.RoomStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RealtimeSnapshotsTest {
    private Player player(String id) { Player p = new Player(); p.setId(id); p.setUsername(id); return p; }
    @Test void snakeInterestSnapshotExcludesDistantFoodAndBodiesAndIsDetached() throws Exception {
        SnakeGame game = new SnakeGame("r", 6000, 3600, 3, 0);
        game.addPlayer(player("me")); game.addPlayer(player("far"));
        game.getSnakes().get("me").getBody().clear(); game.getSnakes().get("me").getBody().add(new SnakeGame.Position(300, 300));
        game.getSnakes().get("far").getBody().clear(); game.getSnakes().get("far").getBody().add(new SnakeGame.Position(5500, 3000));
        game.getFood().add(new SnakeGame.Food(300, 320, 3, 1));
        for (int i = 0; i < 320; i++) game.getFood().add(new SnakeGame.Food(5000 + i, 3000, 3, 1));
        var view = game.getGameState("me");
        assertEquals(1, view.getSnakes().size()); assertEquals(1, view.getFood().size());
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(mapper.writeValueAsBytes(view).length < mapper.writeValueAsBytes(game.getGameState()).length / 3);
        game.getSnakes().get("me").getHead().setX(999);
        assertEquals(300, view.getSnakes().get("me").getHead().getX());
    }
    @Test void tankInterestSnapshotExcludesDistantEntitiesAndCopiesPositionExactly() {
        TankGame game = new TankGame("r", 6000, 3600, 5, 15, 0);
        game.addPlayer(player("me")); game.addPlayer(player("far"));
        game.getTanks().get("me").setX(300.25); game.getTanks().get("me").setY(300.75);
        game.getTanks().get("far").setX(5500); game.getTanks().get("far").setY(3000);
        var view = game.getGameState("me");
        assertEquals(1, view.getTanks().size());
        assertEquals(300.25, view.getTanks().get("me").getX());
        game.getTanks().get("me").setX(900);
        assertEquals(300.25, view.getTanks().get("me").getX());
    }
    @Test void tankStopsExpiredHumanInput() {
        TankGame game = new TankGame("r", 800, 600, 5, 15, 0);
        game.addPlayer(player("me")); game.getWalls().clear();
        var tank = game.getTanks().get("me"); tank.setX(400); tank.setY(300);
        var input = new TankGame.TankInput(); input.setMovingForward(true); input.setShooting(true);
        game.handleInput("me", input); tank.setLastInputAt(System.currentTimeMillis() - 1000);
        game.setState(TankGame.GameState.IN_PROGRESS); game.update();
        assertEquals(400, tank.getX()); assertTrue(game.getBullets().isEmpty());
    }
    @Test void shieldAbsorbsExactlyOneShotAndExpires() {
        var tank = new TankGame.Tank("me", new TankGame.Position(300, 300));
        tank.setShieldUntil(System.currentTimeMillis() + 10000);
        tank.takeDamage(25); assertEquals(100, tank.getHealth());
        tank.takeDamage(25); assertEquals(75, tank.getHealth());
        tank.setShieldUntil(System.currentTimeMillis() - 1); tank.takeDamage(25);
        assertEquals(50, tank.getHealth());
    }
    @Test void itemPickupAndTimedEffectsAreServerOwned() {
        TankGame game = new TankGame("r", 800, 600, 5, 15, 0);
        game.addPlayer(player("me")); game.getWalls().clear(); game.getHealthPacks().clear();
        var tank = game.getTanks().get("me"); tank.setX(400); tank.setY(300);
        game.getHealthPacks().add(new TankGame.HealthPack(400, 300, 12, 0, TankGame.ItemType.SPEED));
        game.setState(TankGame.GameState.IN_PROGRESS); game.update();
        assertTrue(tank.getSpeedUntil() > System.currentTimeMillis());
        var input = new TankGame.TankInput(); input.setMovingForward(true); game.handleInput("me", input); game.update();
        assertEquals(406.75, tank.getX(), .001);
        tank.setSpeedUntil(0); game.update(); assertEquals(411.75, tank.getX(), .001);
        tank.setRapidFireUntil(System.currentTimeMillis() + 6000); tank.resetShootCooldown();
        assertEquals(5, tank.getShootCooldown());
    }
    @Test void allMapsHaveBordersAndPlayableSpawnPositions() {
        for (TankMaps.Layout layout : TankMaps.Layout.values()) {
            TankGame game = new TankGame("r", 3600, 2400, 5, 15, 0, layout.name());
            assertTrue(game.getWalls().size() >= 8);
            for (int i = 0; i < 16; i++) {
                game.addPlayer(player("p" + i)); var tank = game.getTanks().get("p" + i);
                assertTrue(game.getWalls().stream().noneMatch(w -> w.intersects(tank.getX(), tank.getY(), tank.getRadius())));
            }
        }
    }
    @Test void realtimeRoomsAllowJoiningInProgressButBoardRoomsDoNot() {
        for (GameType type : GameType.values()) {
            GameRoom room = GameRoom.create("test", type, "host", 4, GameSettings.defaultFor(type));
            room.setStatus(RoomStatus.IN_PROGRESS);
            assertEquals(type == GameType.SNAKE || type == GameType.TANK, room.canJoin());
        }
    }
    @Test void snakeLengthCannotGrowWithoutBound() {
        SnakeGame game = new SnakeGame("r", 6000, 3600, 3, 0); game.addPlayer(player("me"));
        var snake = game.getSnakes().get("me"); snake.setAngle(0); snake.setTargetAngle(0); snake.setMaxLength(511);
        snake.getBody().clear(); snake.getBody().add(new SnakeGame.Position(300, 300));
        game.getFood().add(new SnakeGame.Food(304, 300, 10, 100));
        game.setState(SnakeGame.GameState.IN_PROGRESS); game.update(); assertEquals(512, snake.getMaxLength());
    }
}
