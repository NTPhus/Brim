package com.brim.game.game.tank;

import com.brim.game.model.dto.Player;
import com.brim.game.game.tactical.*;
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
    private final TacticalBalance balance = TacticalBalance.standard();
    private final List<SupplyCrate> supplies = new ArrayList<>();
    private double simulationTime;
    private final Map<String, Map<String, Observation>> observations = new HashMap<>();
    private record Observation(double x, double y, double observedAt) {}
    public record ContactView(String id, double x, double y, long ageMs, boolean current) {}
    public enum SupplyKind { SHELLS, FUEL, REPAIR }
    @Data
    public static class SupplyCrate {
        private final String id;
        private final double x, y;
        private final SupplyKind kind;
        private double quantity;
        public SupplyCrate(String id, double x, double y, SupplyKind kind, double quantity) {
            this.id = id; this.x = x; this.y = y; this.kind = kind; this.quantity = quantity;
        }
    }
    public record SupplyView(String id, double x, double y, SupplyKind kind, double quantity) {}
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
    @JsonIgnore private final Map<String, Tank> departedTanks = new HashMap<>();
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
        if (this.mode == Mode.ASSAULT) createSupplies();
    }

    public synchronized void addPlayer(Player player) {
        if (tanks.containsKey(player.getId())) return;
        Tank previous = departedTanks.remove(player.getId());
        if (previous != null) { tanks.put(player.getId(), previous); return; }
        Tank tank = new Tank(player.getId(), generateSpawnPosition());
        if (mode == Mode.ASSAULT) {
            tank.maxHealth = player.getId().startsWith("bot-") ? balance.enemyHp() : balance.playerHp();
            tank.health = tank.maxHealth;
            tank.tactical = new HeavyVehicle(balance, ((long) roomId.hashCode() << 32) ^ player.getId().hashCode());
        }
        if (player.getColor() != null && player.getColor().matches("#[0-9a-fA-F]{6}")) tank.setColor(player.getColor());
        tanks.put(player.getId(), tank);
    }

    public synchronized void removePlayer(String playerId) {
        Tank removed = tanks.remove(playerId);
        if (mode == Mode.ASSAULT && removed != null) departedTanks.put(playerId, removed);
    }

    public synchronized void handleInput(String playerId, TankInput input) {
        Tank tank = tanks.get(playerId);
        if (tank != null && tank.isAlive()) {
            if (input != null && ((input.targetX != null && !Double.isFinite(input.targetX))
                    || (input.targetY != null && !Double.isFinite(input.targetY)))) return;
            if (mode == Mode.ASSAULT && input != null && input.action != null && input.commandSeq > 0
                    && state == GameState.IN_PROGRESS && tank.tactical.acceptCommand(input.commandSeq)) {
                String result = switch (input.action) {
                    case "INTERACT" -> takeSupply(tank);
                    case "REPAIR" -> tank.tactical.startRepair(tank.health, tank.maxHealth) ? "REPAIR_STARTED" : "CANNOT_REPAIR";
                    case "RELOAD" -> { tank.tactical.cannon().beginLoading(); yield "ACK"; }
                    default -> "INVALID_ACTION";
                };
                tank.tactical.commandStatus(result);
            }
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

        simulationTime += stepSeconds();
        if (++botDecisionTick % 3 == 0) updateBots();
        explosions.removeIf(e -> e.expiresAt < now);
        if (mode == Mode.ASSAULT) updateAssault();
        else respawnBotsIfReady();
        updateTanks();
        updateBullets();
        checkCollisions();
        checkHealthPacks();
        spawnHealthPacks();
        updateObservations();
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
                if (mode == Mode.ASSAULT && !sees(botTank, other.x, other.y)) continue;
                double dist = Math.hypot(other.getX() - botTank.getX(), other.getY() - botTank.getY());
                if (dist < minDist) {
                    minDist = dist;
                    closestEnemy = other;
                }
            }

            if (closestEnemy == null) { if (mode == Mode.ASSAULT) botTank.input = null; continue; }

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
            if (mode == Mode.ASSAULT) { updateHeavyTank(tank, input); return; }
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

    private double stepSeconds() { return tickRateMs > 0 ? tickRateMs / 1000.0 : .05; }

    private void updateHeavyTank(Tank tank, TankInput input) {
        double dt = stepSeconds();
        boolean forward = input != null && input.forward(), backward = input != null && input.backward();
        double throttle = forward == backward ? 0 : forward ? 1 : -1;
        double turn = input == null ? 0 : (input.turnRight() ? 1 : 0) - (input.turnLeft() ? 1 : 0);
        double aim = input != null && input.targetX != null && input.targetY != null
                ? Math.atan2(input.targetY - tank.y, input.targetX - tank.x) : tank.turretAngle;
        var motion = tank.tactical.advance(dt, throttle, turn, tank.angle, tank.turretAngle, aim);
        double nx = tank.x + Math.cos((tank.angle + motion.hullAngle()) * .5) * motion.distance();
        double ny = tank.y + Math.sin((tank.angle + motion.hullAngle()) * .5) * motion.distance();
        tank.angle = motion.hullAngle(); tank.turretAngle = motion.turretAngle();
        if (canMove(nx, ny, tank.radius, tank)) {
            tank.tactical.traveled(Math.hypot(nx - tank.x, ny - tank.y) / (1 + .1 * tank.engineLevel)); tank.x = nx; tank.y = ny;
        } else tank.tactical.blocked();
        boolean fire = input != null && input.shoot();
        tank.health = tank.tactical.advanceRepair(dt, throttle != 0 || turn != 0 || fire, tank.health, tank.maxHealth);
        if (fire && !tank.tactical.repairing()) shoot(tank);
    }

    private void createSupplies() {
        supplies.clear();
        // Deposits belong to this map visit, not a tick or wave.
        for (int n = 0; n < 8; n++) {
            Position point = generateSpawnPosition();
            SupplyKind kind = n < 4 ? SupplyKind.SHELLS : n < 6 ? SupplyKind.FUEL : SupplyKind.REPAIR;
            supplies.add(new SupplyCrate(mapLayout.name() + "-" + ((Math.max(1, wave) - 1) / 3) + "-" + n, point.x, point.y, kind,
                    kind == SupplyKind.SHELLS ? 4 : kind == SupplyKind.FUEL ? 750 : 1));
        }
    }

    private String takeSupply(Tank tank) {
        SupplyCrate crate = supplies.stream().filter(c -> c.quantity > 0 && Math.hypot(c.x - tank.x, c.y - tank.y) <= 65
                && TacticalGeometry.clear(walls, tank.x, tank.y, c.x, c.y, BattleObstacle::blocksMovement))
                .min(Comparator.comparingDouble(c -> Math.hypot(c.x - tank.x, c.y - tank.y))).orElse(null);
        if (crate == null) return "NO_SUPPLY_NEARBY";
        double used = switch (crate.kind) {
            case SHELLS -> tank.tactical.cannon().refill((int) crate.quantity);
            case FUEL -> tank.tactical.refuel(crate.quantity);
            case REPAIR -> tank.tactical.addRepairKits((int) crate.quantity);
        };
        crate.quantity = Math.max(0, crate.quantity - used);
        return used > 0 ? "SUPPLIED" : "INVENTORY_FULL";
    }

    private void updateBullets() {
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet bullet = it.next();
            if (bullet.shell) {
                double distance = Math.hypot(bullet.targetX - bullet.x, bullet.targetY - bullet.y);
                double step = Math.min(distance, balance.projectileSpeed() * stepSeconds());
                double nx = distance <= step ? bullet.targetX : bullet.x + Math.cos(bullet.angle) * step;
                double ny = distance <= step ? bullet.targetY : bullet.y + Math.sin(bullet.angle) * step;
                double hit = Double.POSITIVE_INFINITY;
                for (Wall wall : walls) if (wall.blocksProjectile) hit = Math.min(hit, wall.hitFraction(bullet.x, bullet.y, nx, ny, 5));
                Tank directHit = null;
                for (Tank target : tanks.values()) if (target.alive && !target.playerId.equals(bullet.ownerId)) {
                    double contact = TacticalGeometry.circleHit(bullet.x, bullet.y, nx, ny, target.x, target.y, target.radius + 3);
                    if (contact < hit) { hit = contact; directHit = target; }
                }
                if (hit <= 1 || distance <= step || bullet.life <= 1) {
                    double fraction = Math.min(1, hit);
                    detonate(bullet, bullet.x + (nx - bullet.x) * fraction, bullet.y + (ny - bullet.y) * fraction, directHit);
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
        if (mode == Mode.ASSAULT && tanks.values().stream().noneMatch(t -> t.alive && !t.playerId.startsWith("bot-"))) state = GameState.FINISHED;
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
        if (mode == Mode.ASSAULT) return;
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
        if (mode == Mode.ASSAULT) return false;
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
            if (!tank.tactical.cannon().fire()) return;
            TankInput input = tank.input;
            double tx = input.targetX == null ? tank.x + Math.cos(tank.turretAngle) * 450 : input.targetX;
            double ty = input.targetY == null ? tank.y + Math.sin(tank.turretAngle) * 450 : input.targetY;
            double range = Math.min(Math.hypot(tx - tank.x, ty - tank.y), balance.range());
            bullet.angle = tank.tactical.shotAngle(tank.turretAngle);
            bullet.x = tank.x; bullet.y = tank.y;
            bullet.targetX = tank.x + Math.cos(bullet.angle) * range;
            bullet.targetY = tank.y + Math.sin(bullet.angle) * range;
            bullet.shell = true; bullet.damage = balance.shellDamage() + tank.cannonLevel * 15;
            bullet.life = (int) Math.ceil(range / (balance.projectileSpeed() * stepSeconds())) + 2;
        }
        bullets.add(bullet);
        if (mode == Mode.CLASSIC) tank.resetShootCooldown();
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

    private void detonate(Bullet bullet, double x, double y, Tank directHit) {
        explosions.add(new Explosion(++explosionSequence, x, y, BLAST_RADIUS, System.currentTimeMillis() + 650));
        Tank owner = tanks.get(bullet.ownerId);
        boolean enemy = bullet.ownerId.startsWith("bot-");
        for (Tank target : tanks.values()) {
            if (!target.alive || (target != owner && target.playerId.startsWith("bot-") == enemy)) continue;
            if (!TacticalGeometry.clear(walls, x, y, target.x, target.y, BattleObstacle::blocksBlast)) continue;
            double sourceX = target == directHit ? bullet.x : x, sourceY = target == directHit ? bullet.y : y;
            int damage = (int) Math.round(TacticalDamage.radial(Math.hypot(target.x - x, target.y - y), balance.blastRadius(), bullet.damage)
                    * TacticalDamage.armor(target.angle, sourceX, sourceY, target.x, target.y));
            if (damage <= 0) continue;
            target.takeDamage(damage);
            if (!target.alive) {
                target.diedAt = System.currentTimeMillis();
                if (owner != null && target != owner) { owner.increaseScore(100); owner.credits += 100; }
            }
        }
        if (!enemy) for (Infantry unit : infantry) {
            if (unit.health <= 0) continue;
            if (!TacticalGeometry.clear(walls, x, y, unit.x, unit.y, BattleObstacle::blocksBlast)) continue;
            int damage = TacticalDamage.radial(Math.hypot(unit.x - x, unit.y - y), balance.blastRadius(), bullet.damage);
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
            if (wave > 1) tanks.values().forEach(t -> { t.credits += 75; });
            if (wave > 1 && (wave - 1) % 3 == 0) {
                mapLayout = TankMaps.Layout.values()[(mapLayout.ordinal() + 1) % TankMaps.Layout.values().length];
                walls.clear(); generateWalls(); bullets.clear(); healthPacks.clear(); explosions.clear(); navigation = null;
                createSupplies();
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
                bot.maxHealth = balance.enemyHp(); bot.health = bot.maxHealth;
                bot.tactical = new HeavyVehicle(balance, roomId.hashCode() ^ bot.playerId.hashCode());
                bot.color = "#ce654b"; tanks.put(bot.playerId, bot);
            }
            waveDelay = 100;
        }
        if (navigation == null || botDecisionTick % 10 == 0) updateNavigation();
        for (Infantry unit : infantry) {
            Tank target = tanks.values().stream().filter(t -> t.alive && !t.playerId.startsWith("bot-"))
                    .min(Comparator.comparingDouble(t -> Math.hypot(t.x - unit.x, t.y - unit.y))).orElse(null);
            if (target == null || !sees(target, unit.x, unit.y)) continue;
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
                double speed = (36 + Math.min(24, wave * 1.6)) * stepSeconds();
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
            if (wall.blocksMovement && wall.intersects(x, y, radius)) return false;
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

    private boolean sees(Tank viewer, double x, double y) {
        return viewer != null && viewer.alive && Math.hypot(x - viewer.x, y - viewer.y) <= balance.sightRange()
                && TacticalGeometry.clear(walls, viewer.x, viewer.y, x, y, BattleObstacle::blocksVision);
    }

    private void updateObservations() {
        if (mode != Mode.ASSAULT) return;
        observations.keySet().retainAll(tanks.keySet());
        for (Tank viewer : tanks.values()) if (!viewer.playerId.startsWith("bot-")) {
            Map<String, Observation> memory = observations.computeIfAbsent(viewer.playerId, ignored -> new HashMap<>());
            for (Tank target : tanks.values()) if (target.playerId.startsWith("bot-") && sees(viewer, target.x, target.y)) {
                if (target.alive) memory.put(target.playerId, new Observation(target.x, target.y, simulationTime));
                else memory.remove(target.playerId);
            }
            for (Infantry unit : infantry) if (sees(viewer, unit.x, unit.y)) {
                if (unit.health > 0) memory.put(unit.id, new Observation(unit.x, unit.y, simulationTime));
                else memory.remove(unit.id);
            }
            memory.values().removeIf(o -> simulationTime - o.observedAt > 4);
        }
    }

    public synchronized TankGameState getGameState() { return snapshot(null, null); }

    public synchronized TankGameState getGameState(String viewerId) {
        Tank viewer = tanks.get(viewerId);
        InterestArea area = viewer == null ? new InterestArea(-10000, -10000, 1, 1)
                : new InterestArea(viewer.x, viewer.y, 1300, 900);
        return snapshot(area, viewer);
    }

    private TankGameState snapshot(InterestArea area, Tank viewer) {
        boolean admin = area == null;
        Map<String, Tank> visible = new HashMap<>();
        for (Tank tank : tanks.values()) {
            if (!admin && (!area.contains(tank.x, tank.y) || mode == Mode.ASSAULT && tank.playerId.startsWith("bot-") && !sees(viewer, tank.x, tank.y))) continue;
            Tank copy = new Tank(tank.playerId, new Position(0, 0));
            copy.x = tank.x; copy.y = tank.y;
            copy.color = tank.color; copy.angle = tank.angle; copy.health = tank.health;
            copy.turretAngle = tank.turretAngle; copy.maxHealth = tank.maxHealth;
            if (admin || viewer == tank) {
                copy.credits = tank.credits; copy.engineLevel = tank.engineLevel; copy.cannonLevel = tank.cannonLevel; copy.armorLevel = tank.armorLevel;
                copy.tacticalView = tank.tactical == null ? null : tank.tactical.view();
                copy.shootCooldown = tank.shootCooldown;
            }
            copy.score = tank.score; copy.alive = tank.alive; copy.diedAt = tank.diedAt;
            copy.shieldUntil = tank.shieldUntil; copy.speedUntil = tank.speedUntil; copy.rapidFireUntil = tank.rapidFireUntil;
            visible.put(tank.playerId, copy);
        }
        List<Bullet> visibleBullets = bullets.stream().filter(b -> admin || area.contains(b.x, b.y)
                && (mode != Mode.ASSAULT || sees(viewer, b.x, b.y)))
                .map(b -> { Bullet copy = new Bullet(b.x, b.y, b.angle, b.ownerId); copy.life = b.life; copy.shell = b.shell; copy.damage = b.damage; return copy; }).toList();
        List<HealthPack> visibleItems = healthPacks.stream().filter(i -> admin || area.contains(i.x, i.y)).toList();
        List<Wall> visibleWalls = walls.stream().filter(w -> admin || area.intersects(w.x, w.y, w.width, w.height))
                .map(w -> { Wall copy = new Wall(w.x,w.y,w.width,w.height); copy.blocksMovement=w.blocksMovement;
                    copy.blocksVision=w.blocksVision;copy.blocksProjectile=w.blocksProjectile;copy.blocksBlast=w.blocksBlast;return copy; }).toList();
        List<ContactView> contacts = new ArrayList<>();
        if (mode == Mode.ASSAULT && viewer != null) observations.getOrDefault(viewer.playerId, Map.of()).forEach((id, o) ->
                contacts.add(new ContactView(id, o.x, o.y, (long) ((simulationTime - o.observedAt) * 1000), simulationTime - o.observedAt < 1e-8)));
        List<SupplyView> supplyViews = supplies.stream().map(c -> new SupplyView(c.id, c.x, c.y, c.kind, c.quantity)).toList();
        return new TankGameState(visible, visibleBullets, visibleItems, visibleWalls, state, mapWidth, mapHeight, mode, mapLayout.name(), wave,
                mode == Mode.ASSAULT && !admin ? -1 : (int) infantry.stream().filter(i -> i.health > 0).count()
                    + (int) tanks.values().stream().filter(t -> t.alive && t.playerId.startsWith("bot-")).count(),
                waveDelay, infantry.stream().filter(i -> admin || area.contains(i.x, i.y) && (mode != Mode.ASSAULT || sees(viewer, i.x, i.y)))
                    .map(i -> new Infantry(i.id, i.x, i.y, i.health, i.cooldown)).toList(),
                explosions.stream().filter(e -> admin || sees(viewer, e.x, e.y)).toList(),
                contacts.stream().filter(ContactView::current).map(c -> new EnemyMarker(c.x, c.y)).toList(),
                supplyViews, mode == Mode.ASSAULT ? balance.version() : "classic", List.copyOf(contacts));
    }
    public record EnemyMarker(double x, double y) {}

    public enum GameState { WAITING, IN_PROGRESS, FINISHED }

    @Data
    public static class Tank {
        private final String playerId;
        private String color = "#2ecc71";
        private double x, y;
        private double angle = 0;
        private double turretAngle;
        @JsonIgnore private HeavyVehicle tactical;
        private HeavyVehicle.View tacticalView;
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
            if (tactical != null && damage > 0) tactical.interruptRepair();
            if (shieldUntil > System.currentTimeMillis()) { shieldUntil = 0; return; }
            health = Math.max(0, health - damage);
            if (health <= 0) { alive = false; if (tactical != null) tactical.cannon().disable(); }
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
        @JsonIgnore private double targetX, targetY;

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
    public static class Wall implements BattleObstacle {
        private final int x, y, width, height;
        private boolean blocksMovement = true, blocksVision = true, blocksProjectile = true, blocksBlast = true;
        @Override public double left() { return x; }
        @Override public double top() { return y; }
        @Override public double width() { return width; }
        @Override public double height() { return height; }
        @Override public boolean blocksMovement() { return blocksMovement; }
        @Override public boolean blocksVision() { return blocksVision; }
        @Override public boolean blocksProjectile() { return blocksProjectile; }
        @Override public boolean blocksBlast() { return blocksBlast; }

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
        private long commandSeq;
        private String action;

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
        private final int mapWidth, mapHeight;
        private final Mode mode;
        private final String mapId;
        private final int wave, enemiesRemaining, nextWaveTicks;
        private final List<Infantry> infantry;
        private final List<Explosion> explosions;
        private final List<EnemyMarker> enemyMarkers;
        private final List<SupplyView> supplies;
        private final String rulesVersion;
        private final List<ContactView> contacts;
        private final long serverTime = System.currentTimeMillis();
    }
}
