package com.brim.game.game.tank;

import com.brim.game.model.dto.Player;
import lombok.Data;

import java.util.*;
import com.brim.game.runtime.InterestArea;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class TankGame implements com.brim.game.runtime.GameSimulation<TankGame.TankGameState> {
    private final String roomId;
    private final int mapWidth;
    private final int mapHeight;
    private final double tankSpeed;
    private final double bulletSpeed;
    private final long tickRateMs;
    private final TankMaps.Layout mapLayout;

    private final Map<String, Tank> tanks = new ConcurrentHashMap<>();
    private final List<Bullet> bullets = Collections.synchronizedList(new ArrayList<>());
    private final List<HealthPack> healthPacks = Collections.synchronizedList(new ArrayList<>());
    private final List<Wall> walls = new ArrayList<>();
    private final Random random = new Random();
    private GameState state = GameState.WAITING;
    private long lastTick = 0;
    private int botDecisionTick = 0;

    public TankGame(String roomId, int mapWidth, int mapHeight, double tankSpeed, double bulletSpeed, long tickRateMs) {
        this(roomId, mapWidth, mapHeight, tankSpeed, bulletSpeed, tickRateMs, "ARENA");
    }

    public TankGame(String roomId, int mapWidth, int mapHeight, double tankSpeed, double bulletSpeed, long tickRateMs, String mapId) {
        this.mapLayout = TankMaps.parse(mapId);
        this.roomId = roomId;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.tankSpeed = tankSpeed;
        this.bulletSpeed = bulletSpeed;
        this.tickRateMs = tickRateMs;
        generateWalls();
        spawnHealthPacks();
    }

    public synchronized void addPlayer(Player player) {
        Tank tank = new Tank(player.getId(), generateSpawnPosition());
        if (player.getColor() != null && player.getColor().matches("#[0-9a-fA-F]{6}")) tank.setColor(player.getColor());
        tanks.put(player.getId(), tank);
    }

    public synchronized void removePlayer(String playerId) {
        tanks.remove(playerId);
    }

    public synchronized void handleInput(String playerId, TankInput input) {
        Tank tank = tanks.get(playerId);
        if (tank != null && tank.isAlive()) {
            tank.setInput(input);
            tank.lastInputAt = System.currentTimeMillis();
        }
    }

    public synchronized void setColor(String playerId, String color) {
        Tank tank = tanks.get(playerId);
        if (tank != null && color != null && color.matches("#[0-9a-fA-F]{6}")) tank.setColor(color);
    }

    public synchronized GameState update() {
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

            if (!tank.playerId.startsWith("bot-") && System.currentTimeMillis() - tank.lastInputAt > 750) tank.input = null;
            TankInput input = tank.getInput();
            if (input == null) return;

            // Rotation
            if (input.turnLeft()) tank.setAngle(tank.getAngle() - 0.1);
            if (input.turnRight()) tank.setAngle(tank.getAngle() + 0.1);

            // Movement
            double dx = 0, dy = 0;
            double movementSpeed = tankSpeed * (tank.speedUntil > System.currentTimeMillis() ? 1.35 : 1);
            if (input.forward()) {
                dx = Math.cos(tank.getAngle()) * movementSpeed;
                dy = Math.sin(tank.getAngle()) * movementSpeed;
            } else if (input.backward()) {
                dx = -Math.cos(tank.getAngle()) * movementSpeed * 0.5;
                dy = -Math.sin(tank.getAngle()) * movementSpeed * 0.5;
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
                    if (!tank.isAlive() || (item.type == ItemType.HEALTH && tank.getHealth() >= 100)) continue;
                    double dx = tank.getX() - item.getX();
                    double dy = tank.getY() - item.getY();
                    if (dx * dx + dy * dy <= Math.pow(tank.getRadius() + item.getRadius(), 2)) {
                        long now = System.currentTimeMillis();
                        switch (item.type) {
                            case HEALTH -> tank.setHealth(Math.min(100, tank.getHealth() + item.getHealAmount()));
                            case SHIELD -> tank.shieldUntil = now + 10_000;
                            case SPEED -> tank.speedUntil = now + 6000;
                            case RAPID_FIRE -> tank.rapidFireUntil = now + 6000;
                        }
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
            for (int attempt = 0; healthPacks.size() < 12 && attempt < 100; attempt++) {
                int x = 40 + random.nextInt(Math.max(1, mapWidth - 80));
                int y = 40 + random.nextInt(Math.max(1, mapHeight - 80));
                if (canMove(x, y, 16)) {
                    ItemType type = random.nextInt(10) < 5 ? ItemType.HEALTH : ItemType.values()[1 + random.nextInt(3)];
                    healthPacks.add(new HealthPack(x, y, 12, type == ItemType.HEALTH ? 35 : 0, type));
                }
            }
        }
    }

    public synchronized boolean respawn(String playerId) {
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
        if (bullets.size() >= 512) return;
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

    private void generateWalls() { walls.addAll(TankMaps.walls(mapLayout, mapWidth, mapHeight)); }

    private Position generateSpawnPosition() {
        int margin = 50;
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = margin + random.nextInt(mapWidth - 2 * margin);
            int y = margin + random.nextInt(mapHeight - 2 * margin);
            if (canMove(x, y, 20)) return new Position(x, y);
        }
        return new Position(margin, margin);
    }

    public synchronized TankGameState getGameState() { return snapshot(null); }

    public synchronized TankGameState getGameState(String viewerId) {
        Tank viewer = tanks.get(viewerId);
        InterestArea area = viewer == null ? new InterestArea(mapWidth / 2.0, mapHeight / 2.0, 1300, 900)
                : new InterestArea(viewer.x, viewer.y, 1300, 900);
        return snapshot(area);
    }

    private TankGameState snapshot(InterestArea area) {
        Map<String, Tank> visible = new HashMap<>();
        for (Tank tank : tanks.values()) {
            if (area != null && !area.contains(tank.x, tank.y)) continue;
            Tank copy = new Tank(tank.playerId, new Position(0, 0));
            copy.x = tank.x; copy.y = tank.y;
            copy.color = tank.color; copy.angle = tank.angle; copy.health = tank.health;
            copy.score = tank.score; copy.alive = tank.alive; copy.diedAt = tank.diedAt;
            copy.shieldUntil = tank.shieldUntil; copy.speedUntil = tank.speedUntil; copy.rapidFireUntil = tank.rapidFireUntil;
            visible.put(tank.playerId, copy);
        }
        List<Bullet> visibleBullets = bullets.stream().filter(b -> area == null || area.contains(b.x, b.y))
                .map(b -> { Bullet copy = new Bullet(b.x, b.y, b.angle, b.ownerId); copy.life = b.life; return copy; }).toList();
        List<HealthPack> visibleItems = healthPacks.stream().filter(i -> area == null || area.contains(i.x, i.y)).toList();
        List<Wall> visibleWalls = walls.stream().filter(w -> area == null || area.intersects(w.x, w.y, w.width, w.height)).toList();
        return new TankGameState(visible, visibleBullets, visibleItems, visibleWalls, state, mapWidth, mapHeight);
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
        @JsonIgnore private TankInput input;
        @JsonIgnore private long lastInputAt;
        private int shootCooldown = 0;
        private long diedAt;
        private long shieldUntil, speedUntil, rapidFireUntil;
        private static final int SHOOT_COOLDOWN_MAX = 10;

        public Tank(String playerId, Position pos) {
            this.playerId = playerId;
            this.x = pos.x;
            this.y = pos.y;
        }

        public boolean canShoot() { return shootCooldown <= 0; }
        public void resetShootCooldown() {
            int base = playerId.startsWith("bot-") ? 18 : SHOOT_COOLDOWN_MAX;
            shootCooldown = rapidFireUntil > System.currentTimeMillis() ? Math.max(3, base / 2) : base;
        }
        public void updateCooldown() { if (shootCooldown > 0) shootCooldown--; }
        public void takeDamage(int damage) {
            if (shieldUntil > System.currentTimeMillis()) { shieldUntil = 0; return; }
            health = Math.max(0, health - damage);
            if (health <= 0) alive = false;
        }
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

    public enum ItemType { HEALTH, SHIELD, SPEED, RAPID_FIRE }

    @Data
    public static class HealthPack {
        private final int x, y, radius, healAmount;
        private final ItemType type;
        public HealthPack(int x, int y, int radius, int healAmount) { this(x, y, radius, healAmount, ItemType.HEALTH); }
        public HealthPack(int x, int y, int radius, int healAmount, ItemType type) {
            this.x = x; this.y = y; this.radius = radius; this.healAmount = healAmount; this.type = type;
        }
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
        private final long serverTime = System.currentTimeMillis();
    }
}
