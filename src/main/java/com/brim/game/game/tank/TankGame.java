package com.brim.game.game.tank;

import com.brim.game.model.dto.Player;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class TankGame {
    private final String roomId;
    private final int mapWidth;
    private final int mapHeight;
    private final double tankSpeed;
    private final double bulletSpeed;
    private final long tickRateMs;

    private final Map<String, Tank> tanks = new ConcurrentHashMap<>();
    private final List<Bullet> bullets = Collections.synchronizedList(new ArrayList<>());
    private final List<HealthPack> healthPacks = Collections.synchronizedList(new ArrayList<>());
    private final List<Wall> walls = new ArrayList<>();
    private final Random random = new Random();
    private GameState state = GameState.WAITING;
    private long lastTick = 0;
    private int botDecisionTick = 0;

    public TankGame(String roomId, int mapWidth, int mapHeight, double tankSpeed, double bulletSpeed, long tickRateMs) {
        this.roomId = roomId;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.tankSpeed = tankSpeed;
        this.bulletSpeed = bulletSpeed;
        this.tickRateMs = tickRateMs;
        generateWalls();
        spawnHealthPacks();
    }

    public void addPlayer(Player player) {
        Tank tank = new Tank(player.getId(), generateSpawnPosition());
        if (player.getColor() != null && player.getColor().matches("#[0-9a-fA-F]{6}")) tank.setColor(player.getColor());
        tanks.put(player.getId(), tank);
    }

    public void removePlayer(String playerId) {
        tanks.remove(playerId);
    }

    public void handleInput(String playerId, TankInput input) {
        Tank tank = tanks.get(playerId);
        if (tank != null && tank.isAlive()) {
            tank.setInput(input);
        }
    }

    public void setColor(String playerId, String color) {
        Tank tank = tanks.get(playerId);
        if (tank != null && color != null && color.matches("#[0-9a-fA-F]{6}")) tank.setColor(color);
    }

    public GameState update() {
        long now = System.currentTimeMillis();
        if (now - lastTick < tickRateMs) return state;
        lastTick = now;

        if (state != GameState.IN_PROGRESS) return state;

        if (++botDecisionTick % 3 == 0) updateBots();
        respawnBotsIfReady();
        updateTanks();
        updateBullets();
        checkCollisions();
        checkHealthPacks();
        spawnHealthPacks();
        checkWinCondition();

        return state;
    }

    private void updateBots() {
        for (Tank botTank : tanks.values()) {
            if (!botTank.isAlive() || !botTank.getPlayerId().startsWith("bot-")) continue;

            Tank closestEnemy = null;
            double minDist = Double.MAX_VALUE;
            for (Tank other : tanks.values()) {
                if (other == botTank || !other.isAlive()) continue;
                double dist = Math.hypot(other.getX() - botTank.getX(), other.getY() - botTank.getY());
                if (dist < minDist) {
                    minDist = dist;
                    closestEnemy = other;
                }
            }

            if (closestEnemy == null) continue;

            TankInput input = new TankInput();

            // 1. Né đạn bay tới gần (Bullet Dodging)
            boolean dodging = false;
            for (Bullet b : bullets) {
                if (b.getOwnerId().equals(botTank.getPlayerId())) continue;
                double distToBullet = Math.hypot(b.getX() - botTank.getX(), b.getY() - botTank.getY());
                if (distToBullet < 120) {
                    // Đạn đang ở gần, né bằng cách lùi hoặc quay góc
                    input.setMovingBackward(true);
                    input.setTurningRight(true);
                    dodging = true;
                    break;
                }
            }

            if (!dodging) {
                // 2. Góc hướng về kẻ địch (Trigonometry chuẩn bằng Radians)
                double dx = closestEnemy.getX() - botTank.getX();
                double dy = closestEnemy.getY() - botTank.getY();
                double targetAngle = Math.atan2(dy, dx); // [-PI, PI]
                double currentAngle = botTank.getAngle();

                double diff = targetAngle - currentAngle;
                while (diff < -Math.PI) diff += 2 * Math.PI;
                while (diff > Math.PI) diff -= 2 * Math.PI;

                // Xoay tháp pháo/xe hướng vào kẻ địch
                if (diff > 0.1) {
                    input.setTurningRight(true);
                } else if (diff < -0.1) {
                    input.setTurningLeft(true);
                }

                // 3. Né vật cản / Tường phía trước (Obstacle Avoidance)
                double lookAheadDist = 35;
                double futureX = botTank.getX() + Math.cos(botTank.getAngle()) * lookAheadDist;
                double futureY = botTank.getY() + Math.sin(botTank.getAngle()) * lookAheadDist;
                boolean pathBlocked = !canMove(futureX, futureY, botTank.getRadius(), botTank);

                if (pathBlocked) {
                    // Nếu phía trước có tường, quay xe để tìm lối đi
                    input.setTurningRight(true);
                    input.setMovingBackward(true);
                } else {
                    // 4. Giữ khoảng cách chiến đấu tối ưu (120px - 280px)
                    if (minDist > 250) {
                        input.setMovingForward(true);
                    } else if (minDist < 90) {
                        input.setMovingBackward(true);
                    } else {
                        // Di chuyển linh hoạt ở tầm trung
                        if (random.nextInt(10) < 6) {
                            input.setMovingForward(true);
                        }
                    }
                }

                // 5. Bắn đạn khi đã ngắm chuẩn (độ lệch góc < 0.2 rad ~ 11 độ)
                // Bots fire less often and add a little aim variance, avoiding bullet spam.
                if (Math.abs(diff) < 0.20 && minDist < 500 && random.nextInt(4) == 0) {
                    input.setShooting(true);
                }
            }

            botTank.setInput(input);
        }
    }

    private void updateTanks() {
        tanks.values().forEach(tank -> {
            if (!tank.isAlive()) return;

            TankInput input = tank.getInput();
            if (input == null) return;

            // Rotation
            if (input.turnLeft()) tank.setAngle(tank.getAngle() - 0.1);
            if (input.turnRight()) tank.setAngle(tank.getAngle() + 0.1);

            // Movement
            double dx = 0, dy = 0;
            if (input.forward()) {
                dx = Math.cos(tank.getAngle()) * tankSpeed;
                dy = Math.sin(tank.getAngle()) * tankSpeed;
            } else if (input.backward()) {
                dx = -Math.cos(tank.getAngle()) * tankSpeed * 0.5;
                dy = -Math.sin(tank.getAngle()) * tankSpeed * 0.5;
            }

            double newX = tank.getX() + dx;
            double newY = tank.getY() + dy;

            // Wall collision check
            if (canMove(newX, newY, tank.getRadius(), tank)) {
                tank.setX(newX);
                tank.setY(newY);
            }

            // Shooting
            if (input.shoot() && tank.canShoot()) {
                shoot(tank);
            }

            tank.updateCooldown();
        });
    }

    private void updateBullets() {
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet bullet = it.next();
            bullet.setX(bullet.getX() + Math.cos(bullet.getAngle()) * bulletSpeed);
            bullet.setY(bullet.getY() + Math.sin(bullet.getAngle()) * bulletSpeed);
            bullet.setLife(bullet.getLife() - 1);

            if (bullet.getLife() <= 0 || bullet.getX() < 0 || bullet.getX() > mapWidth
                    || bullet.getY() < 0 || bullet.getY() > mapHeight) {
                it.remove();
            }
        }
    }

    private void checkCollisions() {
        // Bullet - Tank collisions
        Iterator<Bullet> bit = bullets.iterator();
        bulletLoop:
        while (bit.hasNext()) {
            Bullet bullet = bit.next();
            for (Tank tank : tanks.values()) {
                if (tank.getPlayerId().equals(bullet.getOwnerId()) || !tank.isAlive()) continue;

                double dx = bullet.getX() - tank.getX();
                double dy = bullet.getY() - tank.getY();
                if (dx * dx + dy * dy < tank.getRadius() * tank.getRadius()) {
                    tank.takeDamage(bullet.getDamage());
                    bit.remove();
                    if (!tank.isAlive()) {
                        tank.setDiedAt(System.currentTimeMillis());
                        // Award points to shooter
                        Tank shooter = tanks.get(bullet.getOwnerId());
                        if (shooter != null) shooter.increaseScore(100);
                    }
                    continue bulletLoop;
                }
            }

            // Bullet - Wall collisions
            for (Wall wall : walls) {
                if (wall.intersects(bullet.getX(), bullet.getY(), 5)) {
                    bit.remove();
                    break;
                }
            }
        }
    }

    private void checkWinCondition() {
        // Arena matches stay active; destroyed tanks can respawn after cooldown.
    }

    private void checkHealthPacks() {
        synchronized (healthPacks) {
            Iterator<HealthPack> items = healthPacks.iterator();
            while (items.hasNext()) {
                HealthPack item = items.next();
                boolean pickedUp = false;
                for (Tank tank : tanks.values()) {
                    if (!tank.isAlive() || tank.getHealth() >= 100) continue;
                    double dx = tank.getX() - item.getX();
                    double dy = tank.getY() - item.getY();
                    if (dx * dx + dy * dy <= Math.pow(tank.getRadius() + item.getRadius(), 2)) {
                        tank.setHealth(Math.min(100, tank.getHealth() + item.getHealAmount()));
                        pickedUp = true;
                        break;
                    }
                }
                if (pickedUp) items.remove();
            }
        }
    }

    private void spawnHealthPacks() {
        synchronized (healthPacks) {
            while (healthPacks.size() < 12) {
                int x = 40 + random.nextInt(Math.max(1, mapWidth - 80));
                int y = 40 + random.nextInt(Math.max(1, mapHeight - 80));
                if (canMove(x, y, 16)) healthPacks.add(new HealthPack(x, y, 12, 35));
            }
        }
    }

    public boolean respawn(String playerId) {
        Tank tank = tanks.get(playerId);
        if (tank == null || tank.alive || System.currentTimeMillis() - tank.diedAt < 3000) return false;
        String color = tank.color;
        Tank fresh = new Tank(playerId, generateSpawnPosition());
        fresh.color = color;
        // A respawn starts a fresh run on the map; score is intentionally reset.
        tanks.put(playerId, fresh);
        return true;
    }

    private void respawnBotsIfReady() {
        List<String> ready = tanks.values().stream()
                .filter(tank -> !tank.alive && tank.playerId.startsWith("bot-") && System.currentTimeMillis() - tank.diedAt >= 3000)
                .map(Tank::getPlayerId).toList();
        ready.forEach(this::respawn);
    }

    private void shoot(Tank tank) {
        double bx = tank.getX() + Math.cos(tank.getAngle()) * (tank.getRadius() + 10);
        double by = tank.getY() + Math.sin(tank.getAngle()) * (tank.getRadius() + 10);
        bullets.add(new Bullet(bx, by, tank.getAngle(), tank.getPlayerId()));
        tank.resetShootCooldown();
    }

    private boolean canMove(double x, double y, double radius) {
        return canMove(x, y, radius, null);
    }

    private boolean canMove(double x, double y, double radius, Tank movingTank) {
        if (x - radius < 0 || x + radius > mapWidth || y - radius < 0 || y + radius > mapHeight) {
            return false;
        }
        for (Wall wall : walls) {
            if (wall.intersects(x, y, radius)) return false;
        }
        for (Tank other : tanks.values()) {
            if (other != movingTank && other.isAlive()) {
                double dx = x - other.getX();
                double dy = y - other.getY();
                if (dx * dx + dy * dy < (radius + other.getRadius()) * (radius + other.getRadius())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void generateWalls() {
        // Border walls
        walls.add(new Wall(0, 0, mapWidth, 20)); // Top
        walls.add(new Wall(0, mapHeight - 20, mapWidth, 20)); // Bottom
        walls.add(new Wall(0, 0, 20, mapHeight)); // Left
        walls.add(new Wall(mapWidth - 20, 0, 20, mapHeight)); // Right

        // Random internal walls
        int wallCount = Math.max(16, (mapWidth * mapHeight) / 130000);
        for (int i = 0; i < wallCount; i++) {
            int x = 100 + random.nextInt(mapWidth - 200);
            int y = 100 + random.nextInt(mapHeight - 200);
            int w = 40 + random.nextInt(80);
            int h = 40 + random.nextInt(80);
            walls.add(new Wall(x, y, w, h));
        }
    }

    private Position generateSpawnPosition() {
        int margin = 50;
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = margin + random.nextInt(mapWidth - 2 * margin);
            int y = margin + random.nextInt(mapHeight - 2 * margin);
            if (canMove(x, y, 20)) return new Position(x, y);
        }
        return new Position(margin, margin);
    }

    public TankGameState getGameState() {
        return new TankGameState(
                new HashMap<>(tanks),
                new ArrayList<>(bullets),
                new ArrayList<>(healthPacks),
                walls,
                state,
                mapWidth,
                mapHeight
        );
    }

    public enum GameState { WAITING, IN_PROGRESS, FINISHED }

    @Data
    public static class Tank {
        private final String playerId;
        private String color = "#2ecc71";
        private double x, y;
        private double angle = 0;
        private int health = 100;
        private int score = 0;
        private boolean alive = true;
        private final int radius = 20;
        private TankInput input;
        private int shootCooldown = 0;
        private long diedAt;
        private static final int SHOOT_COOLDOWN_MAX = 10;

        public Tank(String playerId, Position pos) {
            this.playerId = playerId;
            this.x = pos.x;
            this.y = pos.y;
        }

        public boolean canShoot() { return shootCooldown <= 0; }
        public void resetShootCooldown() { shootCooldown = playerId.startsWith("bot-") ? 18 : SHOOT_COOLDOWN_MAX; }
        public void updateCooldown() { if (shootCooldown > 0) shootCooldown--; }
        public void takeDamage(int damage) { health -= damage; if (health <= 0) alive = false; }
        public void increaseScore(int points) { this.score += points; }
    }

    @Data
    public static class Bullet {
        private double x, y;
        private double angle;
        private final String ownerId;
        private int life = 60;
        private final int damage = 25;

        public Bullet(double x, double y, double angle, String ownerId) {
            this.x = x; this.y = y; this.angle = angle; this.ownerId = ownerId;
        }
    }

    @Data
    public static class HealthPack {
        private final int x, y, radius, healAmount;
    }

    @Data
    public static class Wall {
        private final int x, y, width, height;

        public boolean intersects(double px, double py, double radius) {
            double closestX = Math.max(x, Math.min(px, x + width));
            double closestY = Math.max(y, Math.min(py, y + height));
            double dx = px - closestX;
            double dy = py - closestY;
            return dx * dx + dy * dy < radius * radius;
        }
    }

    @Data
    public static class Position { private int x, y; public Position(int x, int y) { this.x = x; this.y = y; } }

    @Data
    public static class TankInput {
        private boolean movingForward, movingBackward, turningLeft, turningRight, shooting;

        public boolean forward() { return movingForward; }
        public boolean backward() { return movingBackward; }
        public boolean turnLeft() { return turningLeft; }
        public boolean turnRight() { return turningRight; }
        public boolean shoot() { return shooting; }
    }

    @Data
    public static class TankGameState {
        private final Map<String, Tank> tanks;
        private final List<Bullet> bullets;
        private final List<HealthPack> healthPacks;
        private final List<Wall> walls;
        private final GameState state;
        private final int mapWidth;
        private final int mapHeight;
    }
}
