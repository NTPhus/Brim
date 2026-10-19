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
    private TankMaps.Layout mapLayout;
    private final Mode mode;
    public static final int BLAST_RADIUS = 120;
    public static final int SHELL_RANGE = 700;
    private final List<Infantry> infantry = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private int wave;
    private int waveDelay = 60;
    private long explosionSequence;
    private int[] navigation;
    private static final int NAV_CELL = 40;
    public enum Mode { CLASSIC, ASSAULT;
        public static Mode parse(String value) { return "ASSAULT".equalsIgnoreCase(value) ? ASSAULT : CLASSIC; }
    }

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
        this(roomId, mapWidth, mapHeight, tankSpeed, bulletSpeed, tickRateMs, mapId, "CLASSIC");
    }

    public TankGame(String roomId, int mapWidth, int mapHeight, double tankSpeed, double bulletSpeed, long tickRateMs, String mapId, String mode) {
        this.mode = Mode.parse(mode);
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
            if (input != null && ((input.targetX != null && !Double.isFinite(input.targetX))
                    || (input.targetY != null && !Double.isFinite(input.targetY)))) return;
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
        explosions.removeIf(e -> e.expiresAt < now);
        if (mode == Mode.ASSAULT) updateAssault();
        else respawnBotsIfReady();
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
                if (other == botTank || !other.isAlive() || (mode == Mode.ASSAULT && other.playerId.startsWith("bot-"))) continue;
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

            if (mode == Mode.ASSAULT) {
                input.targetX = closestEnemy.x; input.targetY = closestEnemy.y;
            }
            botTank.setInput(input);
        }
    }

    private void updateTanks() {
        tanks.values().forEach(tank -> {
            if (!tank.isAlive()) return;

            if (!tank.playerId.startsWith("bot-") && System.currentTimeMillis() - tank.lastInputAt > 750) tank.input = null;
            TankInput input = tank.getInput();
            tank.updateCooldown();
            if (input == null) return;
            if (mode == Mode.ASSAULT && input.targetX != null && input.targetY != null)
                tank.turretAngle = Math.atan2(input.targetY - tank.y, input.targetX - tank.x);
            else tank.turretAngle = tank.angle;

            // Rotation
            if (input.turnLeft()) tank.setAngle(tank.getAngle() - (mode == Mode.ASSAULT ? 0.15 : 0.1));
            if (input.turnRight()) tank.setAngle(tank.getAngle() + (mode == Mode.ASSAULT ? 0.15 : 0.1));

            // Movement
            double dx = 0, dy = 0;
            double movementSpeed = tankSpeed * (mode == Mode.ASSAULT ? 1.8 + tank.engineLevel * .18 : 1) * (tank.speedUntil > System.currentTimeMillis() ? 1.35 : 1);
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

        });
    }

    private void updateBullets() {
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet bullet = it.next();
            if (bullet.shell) {
                double distance = Math.hypot(bullet.targetX - bullet.x, bullet.targetY - bullet.y);
                double step = Math.min(distance, bulletSpeed * 1.8);
                double nx = distance <= step ? bullet.targetX : bullet.x + Math.cos(bullet.angle) * step;
                double ny = distance <= step ? bullet.targetY : bullet.y + Math.sin(bullet.angle) * step;
                double hit = Double.POSITIVE_INFINITY;
                for (Wall wall : walls) hit = Math.min(hit, wall.hitFraction(bullet.x, bullet.y, nx, ny, 5));
                if (hit <= 1 || distance <= step || bullet.life <= 1) {
                    double fraction = Math.min(1, hit);
                    detonate(bullet, bullet.x + (nx - bullet.x) * fraction, bullet.y + (ny - bullet.y) * fraction);
                    it.remove();
                    continue;
                }
                bullet.x = nx; bullet.y = ny; bullet.life--;
                continue;
            }
            bullet.setX(bullet.getX() + Math.cos(bullet.getAngle()) * bulletSpeed * (bullet.shell ? 1.8 : 1));
            bullet.setY(bullet.getY() + Math.sin(bullet.getAngle()) * bulletSpeed * (bullet.shell ? 1.8 : 1));
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
            if (bullet.shell) continue;
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
                    if (!tank.isAlive() || (item.type == ItemType.HEALTH && tank.getHealth() >= tank.maxHealth)) continue;
                    double dx = tank.getX() - item.getX();
                    double dy = tank.getY() - item.getY();
                    if (dx * dx + dy * dy <= Math.pow(tank.getRadius() + item.getRadius(), 2)) {
                        long now = System.currentTimeMillis();
                        switch (item.type) {
                            case HEALTH -> tank.setHealth(Math.min(tank.maxHealth, tank.getHealth() + item.getHealAmount()));
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
        if (mode == Mode.ASSAULT) {
            fresh.engineLevel = tank.engineLevel; fresh.cannonLevel = tank.cannonLevel; fresh.armorLevel = tank.armorLevel;
            fresh.maxHealth = tank.maxHealth; fresh.health = tank.maxHealth; fresh.credits = tank.credits; fresh.score = tank.score;
            fresh.shieldUntil = System.currentTimeMillis() + 3000;
        }
        // Classic respawns start a fresh run.
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
        double angle = mode == Mode.ASSAULT ? tank.turretAngle : tank.angle;
        double bx = tank.x + Math.cos(angle) * (tank.radius + 10);
        double by = tank.y + Math.sin(angle) * (tank.radius + 10);
        Bullet bullet = new Bullet(bx, by, angle, tank.playerId);
        if (mode == Mode.ASSAULT) {
            TankInput input = tank.input;
            double tx = input.targetX == null ? tank.x + Math.cos(angle) * 450 : input.targetX;
            double ty = input.targetY == null ? tank.y + Math.sin(angle) * 450 : input.targetY;
            tx = Math.max(25, Math.min(mapWidth - 25, tx)); ty = Math.max(25, Math.min(mapHeight - 25, ty));
            double distance = Math.hypot(tx - tank.x, ty - tank.y);
            double range = Math.min(distance, SHELL_RANGE);
            bullet.angle = Math.atan2(ty - tank.y, tx - tank.x);
            bullet.x = tank.x; bullet.y = tank.y;
            bullet.targetX = tank.x + Math.cos(bullet.angle) * range;
            bullet.targetY = tank.y + Math.sin(bullet.angle) * range;
            bullet.shell = true; bullet.damage = 70 + tank.cannonLevel * 15;
        }
        bullets.add(bullet);
        tank.resetShootCooldown();
    }

    public synchronized boolean upgrade(String playerId, String branch) {
        Tank tank = tanks.get(playerId);
        if (mode != Mode.ASSAULT || state != GameState.IN_PROGRESS || tank == null || !tank.alive || branch == null) return false;
        int level = switch (branch) { case "ENGINE" -> tank.engineLevel; case "CANNON" -> tank.cannonLevel; case "ARMOR" -> tank.armorLevel; default -> -1; };
        if (level < 0 || level >= 3 || tank.credits < (level + 1) * 100) return false;
        tank.credits -= (level + 1) * 100;
        switch (branch) {
            case "ENGINE" -> tank.engineLevel++;
            case "CANNON" -> tank.cannonLevel++;
            case "ARMOR" -> { tank.armorLevel++; tank.maxHealth += 35; tank.health += 35; }
        }
        return true;
    }

    public static int blastDamage(double distance, int damage) {
        if (!Double.isFinite(distance) || distance >= BLAST_RADIUS) return 0;
        return (int) Math.ceil(damage * (1 - Math.max(0, distance) / BLAST_RADIUS));
    }

    private void detonate(Bullet bullet, double x, double y) {
        explosions.add(new Explosion(++explosionSequence, x, y, BLAST_RADIUS, System.currentTimeMillis() + 650));
        Tank owner = tanks.get(bullet.ownerId);
        boolean enemy = bullet.ownerId.startsWith("bot-");
        for (Tank target : tanks.values()) {
            if (!target.alive || (target != owner && target.playerId.startsWith("bot-") == enemy)) continue;
            int damage = blastDamage(Math.hypot(target.x - x, target.y - y), bullet.damage);
            if (damage <= 0) continue;
            target.takeDamage(damage);
            if (!target.alive) {
                target.diedAt = System.currentTimeMillis();
                if (owner != null && target != owner) { owner.increaseScore(100); owner.credits += 100; }
            }
        }
        if (!enemy) for (Infantry unit : infantry) {
            if (unit.health <= 0) continue;
            int damage = blastDamage(Math.hypot(unit.x - x, unit.y - y), bullet.damage);
            unit.health = Math.max(0, unit.health - damage);
            if (damage > 0 && unit.health == 0 && owner != null) { owner.increaseScore(25); owner.credits += 25; }
        }
    }

    private void updateAssault() {
        infantry.removeIf(i -> i.health <= 0);
        boolean humansAlive = tanks.values().stream().anyMatch(t -> t.alive && !t.playerId.startsWith("bot-"));
        if (!humansAlive) return;
        boolean clear = infantry.isEmpty() && tanks.values().stream().noneMatch(t -> t.alive && t.playerId.startsWith("bot-"));
        if (clear && --waveDelay <= 0) {
            wave++;
            tanks.values().removeIf(t -> t.playerId.startsWith("bot-"));
            if (wave > 1) tanks.values().forEach(t -> { t.credits += 75; t.health = Math.min(t.maxHealth, t.health + 30); });
            if (wave > 1 && (wave - 1) % 3 == 0) {
                mapLayout = TankMaps.Layout.values()[(mapLayout.ordinal() + 1) % TankMaps.Layout.values().length];
                walls.clear(); generateWalls(); bullets.clear(); healthPacks.clear(); explosions.clear(); navigation = null;
                tanks.values().forEach(t -> { Position p = generateSpawnPosition(); t.x = p.x; t.y = p.y; });
                spawnHealthPacks();
            }
            int count = Math.min(30, 5 + wave * 2);
            for (int n = 0; n < count; n++) {
                Position p = enemySpawn();
                infantry.add(new Infantry("inf-" + wave + "-" + n, p.x, p.y, 45 + Math.min(45, wave * 3), 30 + n % 20));
            }
            for (int n = 0; n < Math.min(5, 1 + wave / 2); n++) {
                Position p = enemySpawn();
                Tank bot = new Tank("bot-wave-" + wave + "-" + n, p);
                bot.color = "#ce654b"; tanks.put(bot.playerId, bot);
            }
            waveDelay = 100;
        }
        if (navigation == null || botDecisionTick % 10 == 0) updateNavigation();
        for (Infantry unit : infantry) {
            Tank target = tanks.values().stream().filter(t -> t.alive && !t.playerId.startsWith("bot-"))
                    .min(Comparator.comparingDouble(t -> Math.hypot(t.x - unit.x, t.y - unit.y))).orElse(null);
            if (target == null) continue;
            double distance = Math.hypot(target.x - unit.x, target.y - unit.y);
            double angle = Math.atan2(target.y - unit.y, target.x - unit.x);
            boolean blocked = !lineClear(unit.x, unit.y, target.x, target.y, 12);
            if (distance > 150 || blocked) {
                if (blocked) {
                    int cols = (mapWidth + NAV_CELL - 1) / NAV_CELL;
                    int cell = Math.min(navigation.length - 1, (int) unit.y / NAV_CELL * cols + (int) unit.x / NAV_CELL);
                    int next = cell;
                    for (int neighbor : new int[]{cell - cols, cell + cols, cell % cols > 0 ? cell - 1 : -1, cell % cols < cols - 1 ? cell + 1 : -1})
                        if (neighbor >= 0 && neighbor < navigation.length && navigation[neighbor] < navigation[next]) next = neighbor;
                    if (next != cell) angle = Math.atan2((next / cols + .5) * NAV_CELL - unit.y, (next % cols + .5) * NAV_CELL - unit.x);
                }
                double speed = 1.8 + Math.min(1.2, wave * .08);
                double nx = unit.x + Math.cos(angle) * speed, ny = unit.y + Math.sin(angle) * speed;
                if (canMove(nx, ny, 9)) { unit.x = nx; unit.y = ny; }
                else { // Slide along cover rather than walking through it.
                    if (canMove(nx, unit.y, 9)) unit.x = nx;
                    else if (canMove(unit.x, ny, 9)) unit.y = ny;
                }
            }
            if (--unit.cooldown <= 0 && distance < 310) {
                boolean cover = !lineClear(unit.x, unit.y, target.x, target.y, 2);
                if (!cover) {
                    target.takeDamage(4 + Math.min(4, wave / 3));
                    if (!target.alive) target.diedAt = System.currentTimeMillis();
                }
                unit.cooldown = 35;
            }
        }
    }

    private boolean lineClear(double x, double y, double tx, double ty, int radius) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(tx - x, ty - y) / 15));
        for (Wall wall : walls) for (int step = 0; step <= steps; step++)
            if (wall.intersects(x + (tx - x) * step / steps, y + (ty - y) * step / steps, radius)) return false;
        return true;
    }

    /** Shared flow field lets squads find corridors around buildings without a search per soldier. */
    private void updateNavigation() {
        int cols = (mapWidth + NAV_CELL - 1) / NAV_CELL, rows = (mapHeight + NAV_CELL - 1) / NAV_CELL;
        navigation = new int[cols * rows];
        Arrays.fill(navigation, Integer.MAX_VALUE);
        boolean[] blocked = new boolean[navigation.length];
        for (int i = 0; i < navigation.length; i++) {
            double x = (i % cols + .5) * NAV_CELL, y = (i / cols + .5) * NAV_CELL;
            blocked[i] = x >= mapWidth - 12 || y >= mapHeight - 12 || walls.stream().anyMatch(w -> w.intersects(x, y, 12));
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Tank tank : tanks.values()) if (tank.alive && !tank.playerId.startsWith("bot-")) {
            int cell = (int) tank.y / NAV_CELL * cols + (int) tank.x / NAV_CELL;
            navigation[cell] = 0; queue.add(cell);
        }
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            for (int neighbor : new int[]{cell - cols, cell + cols, cell % cols > 0 ? cell - 1 : -1, cell % cols < cols - 1 ? cell + 1 : -1}) {
                if (neighbor < 0 || neighbor >= navigation.length || blocked[neighbor] || navigation[neighbor] != Integer.MAX_VALUE) continue;
                navigation[neighbor] = navigation[cell] + 1; queue.addLast(neighbor);
            }
        }
    }

    private Position enemySpawn() {
        Position p = generateSpawnPosition();
        for (int attempt = 0; attempt < 60; attempt++) {
            Position candidate = generateSpawnPosition();
            if (tanks.values().stream().filter(t -> t.alive && !t.playerId.startsWith("bot-"))
                    .allMatch(t -> Math.hypot(t.x - candidate.x, t.y - candidate.y) > 450)) return candidate;
            p = candidate;
        }
        return p;
    }

    @Data @lombok.AllArgsConstructor
    public static class Infantry {
        private final String id;
        private double x, y;
        private int health;
        private int cooldown;
    }
    public record Explosion(long id, double x, double y, int radius, long expiresAt) {}

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
            copy.turretAngle = tank.turretAngle; copy.maxHealth = tank.maxHealth; copy.credits = tank.credits;
            copy.engineLevel = tank.engineLevel; copy.cannonLevel = tank.cannonLevel; copy.armorLevel = tank.armorLevel;
            copy.score = tank.score; copy.alive = tank.alive; copy.diedAt = tank.diedAt;
            copy.shieldUntil = tank.shieldUntil; copy.speedUntil = tank.speedUntil; copy.rapidFireUntil = tank.rapidFireUntil;
            visible.put(tank.playerId, copy);
        }
        List<Bullet> visibleBullets = bullets.stream().filter(b -> area == null || area.contains(b.x, b.y))
                .map(b -> { Bullet copy = new Bullet(b.x, b.y, b.angle, b.ownerId); copy.life = b.life; copy.shell = b.shell; copy.targetX = b.targetX; copy.targetY = b.targetY; copy.damage = b.damage; return copy; }).toList();
        List<HealthPack> visibleItems = healthPacks.stream().filter(i -> area == null || area.contains(i.x, i.y)).toList();
        List<Wall> visibleWalls = walls.stream().filter(w -> area == null || area.intersects(w.x, w.y, w.width, w.height)).toList();
        return new TankGameState(visible, visibleBullets, visibleItems, visibleWalls, state, mapWidth, mapHeight, mode, mapLayout.name(), wave,
                (int) infantry.stream().filter(i -> i.health > 0).count() + (int) tanks.values().stream().filter(t -> t.alive && t.playerId.startsWith("bot-")).count(),
                waveDelay, infantry.stream().filter(i -> area == null || area.contains(i.x, i.y)).map(i -> new Infantry(i.id, i.x, i.y, i.health, i.cooldown)).toList(),
                explosions.stream().filter(e -> area == null || area.intersects(e.x - BLAST_RADIUS, e.y - BLAST_RADIUS, BLAST_RADIUS * 2, BLAST_RADIUS * 2)).toList(), enemyMarkers());
    }

    public record EnemyMarker(double x, double y) {}
    private List<EnemyMarker> enemyMarkers() {
        if (mode != Mode.ASSAULT) return List.of();
        List<EnemyMarker> markers = new ArrayList<>();
        infantry.stream().filter(i -> i.health > 0).forEach(i -> markers.add(new EnemyMarker(i.x, i.y)));
        tanks.values().stream().filter(t -> t.alive && t.playerId.startsWith("bot-"))
                .forEach(t -> markers.add(new EnemyMarker(t.x, t.y)));
        return markers;
    }

    public enum GameState { WAITING, IN_PROGRESS, FINISHED }

    @Data
    public static class Tank {
        private final String playerId;
        private String color = "#2ecc71";
        private double x, y;
        private double angle = 0;
        private double turretAngle;
        private int maxHealth = 100;
        private int credits, engineLevel, cannonLevel, armorLevel;
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
            base = Math.max(4, base - cannonLevel);
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
        private int damage = 25;
        private boolean shell;
        private double targetX, targetY;

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

        /** Earliest contact along a swept shell segment; thin walls cannot be skipped between ticks. */
        public double hitFraction(double fromX, double fromY, double toX, double toY, double radius) {
            double enter = 0, exit = 1;
            double[] origin = {fromX, fromY}, delta = {toX - fromX, toY - fromY};
            double[] min = {x - radius, y - radius}, max = {x + width + radius, y + height + radius};
            for (int axis = 0; axis < 2; axis++) {
                if (Math.abs(delta[axis]) < 1e-9) {
                    if (origin[axis] < min[axis] || origin[axis] > max[axis]) return Double.POSITIVE_INFINITY;
                } else {
                    double near = (min[axis] - origin[axis]) / delta[axis], far = (max[axis] - origin[axis]) / delta[axis];
                    enter = Math.max(enter, Math.min(near, far)); exit = Math.min(exit, Math.max(near, far));
                    if (enter > exit) return Double.POSITIVE_INFINITY;
                }
            }
            return enter;
        }

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
        private Double targetX, targetY;

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
        private final Mode mode;
        private final String mapId;
        private final int wave, enemiesRemaining, nextWaveTicks;
        private final List<Infantry> infantry;
        private final List<Explosion> explosions;
        private final List<EnemyMarker> enemyMarkers;
        private final long serverTime = System.currentTimeMillis();
    }
}
