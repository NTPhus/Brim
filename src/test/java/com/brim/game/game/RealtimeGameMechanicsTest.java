package com.brim.game.game;

import com.brim.game.game.snake.SnakeGame;
import com.brim.game.game.tank.TankGame;
import com.brim.game.model.dto.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeGameMechanicsTest {

    @Test
    void tankCanMoveWithoutCollidingWithItself() {
        TankGame game = new TankGame("tank-test", 800, 600, 5, 15, 0);
        Player player = player("human");
        game.addPlayer(player);
        game.getWalls().clear();
        TankGame.Tank tank = game.getTanks().get(player.getId());
        tank.setX(400);
        tank.setY(300);
        double startX = tank.getX();

        TankGame.TankInput input = new TankGame.TankInput();
        input.setMovingForward(true);
        game.handleInput(player.getId(), input);
        game.setState(TankGame.GameState.IN_PROGRESS);
        game.update();

        assertNotEquals(startX, tank.getX());
    }

    @Test
    void snakeDoesNotCollideWithItsOwnBody() {
        SnakeGame game = new SnakeGame("snake-test", 1200, 700, 3, 0);
        Player player = player("human");
        game.addPlayer(player);
        SnakeGame.Snake snake = game.getSnakes().get(player.getId());
        snake.getBody().clear();
        snake.getBody().add(new SnakeGame.Position(200, 200));
        for (int i = 1; i < 18; i++) {
            snake.getBody().add(new SnakeGame.Position(i == 12 ? 204 : 200 - i * 3, 200));
        }
        snake.setAngle(0);
        snake.setTargetAngle(0);
        game.setState(SnakeGame.GameState.IN_PROGRESS);
        game.update();

        assertTrue(snake.isAlive());
    }

    @Test
    void eliminatedPlayersCanRespawnAfterCooldown() {
        SnakeGame snakeGame = new SnakeGame("snake-respawn", 4000, 2400, 3, 0);
        Player player = player("human");
        snakeGame.addPlayer(player);
        SnakeGame.Snake snake = snakeGame.getSnakes().get(player.getId());
        snake.setAlive(false);
        snake.setDiedAt(System.currentTimeMillis() - 3100);
        assertTrue(snakeGame.respawn(player.getId()));
        assertTrue(snakeGame.getSnakes().get(player.getId()).isAlive());

        TankGame tankGame = new TankGame("tank-respawn", 2400, 1600, 5, 15, 0);
        tankGame.addPlayer(player);
        TankGame.Tank tank = tankGame.getTanks().get(player.getId());
        tank.setAlive(false);
        tank.setDiedAt(System.currentTimeMillis() - 3100);
        assertTrue(tankGame.respawn(player.getId()));
        assertTrue(tankGame.getTanks().get(player.getId()).isAlive());
    }

    private Player player(String id) {
        Player player = new Player();
        player.setId(id);
        player.setUsername(id);
        player.setReady(true);
        return player;
    }
}
