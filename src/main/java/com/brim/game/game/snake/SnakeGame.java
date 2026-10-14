package com.brim.game.game.snake;

import com.brim.game.model.dto.Player;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** A small real-time, mouse-steered snake game inspired by Slither.io. */
@Data
public class SnakeGame {
    private static final double NORMAL_SPEED = 4.0;
    private static final double BOOST_SPEED = 6.2;
    private static final double MAX_TURN_PER_TICK = 0.20;
    private static final double HEAD_RADIUS = 9.0;

    private final String roomId;
    private final int gridWidth; // Kept in the wire format; values are world pixels.
    private final int gridHeight;
    private final int initialLength;
    private final long tickRateMs;
    private final Map<String, Snake> snakes = new ConcurrentHashMap<>();
    private final Set<Food> food = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();
    private GameState state = GameState.WAITING;
    private long lastTick = 0;

    public SnakeGame(String roomId, int gridWidth, int gridHeight, int initialLength, long tickRateMs) {
        this.roomId = roomId;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.initialLength = initialLength;
        this.tickRateMs = tickRateMs;
    }

    public void addPlayer(Player player) {
        snakes.put(player.getId(), new Snake(player.getId(), generateSpawnPosition(), initialLength));
    }

    public void removePlayer(String playerId) { snakes.remove(playerId); }

    public void handleInput(String playerId, double angle, boolean boosting) {
        Snake snake = snakes.get(playerId);
        if (snake != null && snake.alive && Double.isFinite(angle)) {
            snake.targetAngle = normalizeAngle(angle);
            snake.boosting = boosting;
        }
    }

    public GameState update() {
        long now = System.currentTimeMillis();
        if (now - lastTick < tickRateMs || state != GameState.IN_PROGRESS) return state;
        lastTick = now;
        updateBots();
        respawnBotsIfReady();
        snakes.values().forEach(this::moveSnake);
        checkCollisionsAndFood();
        spawnFoodIfNeeded();
        checkWinCondition();
        return state;
    }

    private void updateBots() {
        for (Snake snake : snakes.values()) {
            if (!snake.alive || !snake.playerId.startsWith("bot-")) continue;
            Position head = snake.getHead();
            Food target = food.stream().min(Comparator.comparingDouble(p -> distanceSquared(head, p))).orElse(null);
            if (target != null) snake.targetAngle = Math.atan2(target.getY() - head.y, target.getX() - head.x);
            snake.boosting = target != null && distanceSquared(head, target) > 140 * 140;
        }
    }

    private void moveSnake(Snake snake) {
        if (!snake.alive) return;
        double difference = normalizeAngle(snake.targetAngle - snake.angle);
        snake.angle = normalizeAngle(snake.angle + Math.max(-MAX_TURN_PER_TICK, Math.min(MAX_TURN_PER_TICK, difference)));
        double speed = snake.boosting && snake.body.size() > 14 ? BOOST_SPEED : NORMAL_SPEED;
        Position head = snake.getHead();
        snake.body.add(0, new Position(head.x + Math.cos(snake.angle) * speed, head.y + Math.sin(snake.angle) * speed));
        while (snake.body.size() > snake.maxLength) snake.body.remove(snake.body.size() - 1);

        if (snake.boosting && snake.body.size() > 18 && random.nextInt(12) == 0) {
            Position tail = snake.body.get(snake.body.size() - 1);
            food.add(new Food(tail.x, tail.y, 3.0, 1));
            snake.maxLength = Math.max(18, snake.maxLength - 1);
        }
    }

    private void checkCollisionsAndFood() {
        for (Snake snake : snakes.values()) {
            if (!snake.alive) continue;
            Position head = snake.getHead();
            if (head.x < HEAD_RADIUS || head.x > gridWidth - HEAD_RADIUS || head.y < HEAD_RADIUS || head.y > gridHeight - HEAD_RADIUS) {
                killSnake(snake);
                continue;
            }

            boolean hitBody = false;
            for (Snake other : snakes.values()) {
                // Like Slither.io, looping through your own body is allowed.
                if (!other.alive || other == snake) continue;
                for (int i = 0; i < other.body.size(); i += 2) {
                    if (distanceSquared(head, other.body.get(i)) < HEAD_RADIUS * HEAD_RADIUS * 1.6) {
                        hitBody = true;
                        break;
                    }
                }
                if (hitBody) break;
            }
            if (hitBody) {
                killSnake(snake);
                continue;
            }

            Food eaten = food.stream()
                    .filter(pellet -> distanceSquared(head, pellet) < Math.pow(HEAD_RADIUS + pellet.radius, 2))
                    .findFirst()
                    .orElse(null);
            if (eaten != null && food.remove(eaten)) {
                snake.score += eaten.value;
                snake.maxLength += Math.max(1, eaten.value);
            }
        }
    }

    private void killSnake(Snake snake) {
        snake.alive = false;
        snake.diedAt = System.currentTimeMillis();
        for (int i = 0; i < snake.body.size(); i += 3) {
            Position p = snake.body.get(i);
            double radius = 3 + random.nextDouble() * 4;
            food.add(new Food(p.x, p.y, radius, foodValue(radius)));
        }
    }

    private void spawnFoodIfNeeded() {
        while (food.size() < 320) {
            double radius = 2.5 + random.nextDouble() * 7.5;
            food.add(new Food(18 + random.nextDouble() * (gridWidth - 36),
                    18 + random.nextDouble() * (gridHeight - 36), radius, foodValue(radius)));
        }
    }

    private void checkWinCondition() {
        // Arena matches stay active; eliminated players can respawn after cooldown.
    }

    private int foodValue(double radius) { return Math.max(1, (int) Math.round(radius * radius / 7.0)); }

    public boolean respawn(String playerId) {
        Snake snake = snakes.get(playerId);
        if (snake == null || snake.alive || System.currentTimeMillis() - snake.diedAt < 3000) return false;
        snakes.put(playerId, new Snake(playerId, generateSpawnPosition(), initialLength));
        return true;
    }

    private void respawnBotsIfReady() {
        List<String> ready = snakes.values().stream()
                .filter(snake -> !snake.alive && snake.playerId.startsWith("bot-") && System.currentTimeMillis() - snake.diedAt >= 3000)
                .map(Snake::getPlayerId).toList();
        ready.forEach(this::respawn);
    }

    private Position generateSpawnPosition() {
        Position candidate = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            candidate = new Position(80 + random.nextDouble() * (gridWidth - 160), 80 + random.nextDouble() * (gridHeight - 160));
            Position checked = candidate;
            boolean clear = snakes.values().stream().allMatch(snake -> distanceSquared(checked, snake.getHead()) > 130 * 130);
            if (clear) return candidate;
        }
        return candidate == null ? new Position(gridWidth / 2.0, gridHeight / 2.0) : candidate;
    }

    private double distanceSquared(Position a, Position b) {
        double dx = a.x - b.x, dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    private double normalizeAngle(double value) {
        while (value <= -Math.PI) value += Math.PI * 2;
        while (value > Math.PI) value -= Math.PI * 2;
        return value;
    }

    public SnakeGameState getGameState() {
        return new SnakeGameState(new HashMap<>(snakes), new HashSet<>(food), state, gridWidth, gridHeight);
    }

    public enum GameState { WAITING, IN_PROGRESS, FINISHED }

    @Data
    public static class Snake {
        private final String playerId;
        private final List<Position> body = new ArrayList<>();
        private double angle;
        private double targetAngle;
        private boolean boosting;
        private boolean alive = true;
        private int score;
        private int maxLength;
        private long diedAt;

        public Snake(String playerId, Position head, int initialLength) {
            this.playerId = playerId;
            this.angle = Math.random() * Math.PI * 2 - Math.PI;
            this.targetAngle = angle;
            this.maxLength = Math.max(18, initialLength * 6);
            for (int i = 0; i < maxLength; i++) {
                body.add(new Position(head.x - Math.cos(angle) * i * 3, head.y - Math.sin(angle) * i * 3));
            }
        }

        public Position getHead() { return body.get(0); }
    }

    @Data
    public static class Position {
        private double x;
        private double y;
        public Position(double x, double y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Position p)) return false;
            return Double.compare(x, p.x) == 0 && Double.compare(y, p.y) == 0;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Food extends Position {
        private final double radius;
        private final int value;
        public Food(double x, double y, double radius, int value) {
            super(x, y);
            this.radius = radius;
            this.value = value;
        }
    }

    @Data
    public static class SnakeGameState {
        private final Map<String, Snake> snakes;
        private final Set<Food> food;
        private final GameState state;
        private final int gridWidth;
        private final int gridHeight;
    }
}
