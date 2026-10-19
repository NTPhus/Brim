package com.brim.game.game;

import com.brim.game.game.tank.TankGame;
import com.brim.game.game.tank.TankMaps;
import com.brim.game.model.dto.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TankAssaultTest {
    private TankGame game(double speed) {
        var game = new TankGame("assault", 1800, 1200, speed, 15, 0, "ARENA", "ASSAULT");
        game.getWalls().clear();
        game.getHealthPacks().clear();
        game.setState(TankGame.GameState.IN_PROGRESS);
        add(game, "human", 100, 100);
        return game;
    }

    private TankGame.Tank add(TankGame game, String id, double x, double y) {
        var player = new Player(); player.setId(id); game.addPlayer(player);
        var tank = game.getTanks().get(id);
        tank.setX(x); tank.setY(y); tank.setShootCooldown(999);
        return tank;
    }

    private void fire(TankGame game, double x, double y) {
        game.getTanks().get("human").setShootCooldown(0);
        var input = new TankGame.TankInput(); input.setShooting(true); input.setTargetX(x); input.setTargetY(y);
        game.handleInput("human", input); game.update();
        game.handleInput("human", new TankGame.TankInput());
    }

    @Test void blastDamageFallsOffAndStopsAtFixedRadius() {
        assertEquals(70, TankGame.blastDamage(0, 70));
        assertEquals(35, TankGame.blastDamage(60, 70));
        assertEquals(1, TankGame.blastDamage(119, 70));
        assertEquals(0, TankGame.blastDamage(120, 70));
        assertEquals(0, TankGame.blastDamage(Double.NaN, 70));
    }

    @Test void shellReachesChosenPointThenDamagesMultipleEnemiesWithoutFriendlyFire() {
        var game = game(0);
        var center = add(game, "bot-center", 400, 100);
        var edge = add(game, "bot-edge", 460, 100);
        var outside = add(game, "bot-outside", 521, 100);
        var friend = add(game, "friend", 400, 100);
        game.getInfantry().add(new TankGame.Infantry("squad", 400, 100, 40, 999));
        fire(game, 400, 100);
        assertEquals(100, center.getHealth(), "Shells take time to reach the designated location");
        for (int i = 0; i < 12; i++) game.update();
        assertEquals(30, center.getHealth());
        assertEquals(65, edge.getHealth());
        assertEquals(100, outside.getHealth());
        assertEquals(100, friend.getHealth());
        assertEquals(25, game.getTanks().get("human").getCredits());
        assertFalse(game.getExplosions().isEmpty());
        assertEquals(120, game.getExplosions().get(0).radius());
        assertEquals(400, game.getExplosions().get(0).x());
    }

    @Test void shellExplodesAtFirstThinWallEvenBetweenTicks() {
        var game = game(0);
        var protectedTank = add(game, "bot-target", 500, 100);
        // Insert the more distant wall first: collision must choose the earliest along the flight.
        game.getWalls().add(new TankGame.Wall(350, 60, 10, 80));
        game.getWalls().add(new TankGame.Wall(230, 60, 1, 80));
        fire(game, 500, 100);
        for (int i = 0; i < 20; i++) game.update();
        assertTrue(game.getBullets().isEmpty());
        assertEquals(1, game.getExplosions().size());
        assertEquals(225, game.getExplosions().get(0).x(), .001);
        assertEquals(100, game.getExplosions().get(0).y(), .001);
        assertEquals(100, protectedTank.getHealth());
    }

    @Test void nearbyWallBlastDamagesShooterUsingTheSameDistanceFalloff() {
        var game = game(0);
        game.getWalls().add(new TankGame.Wall(150, 60, 10, 80));
        fire(game, 500, 100);
        game.update();
        assertEquals(145, game.getExplosions().get(0).x(), .001);
        assertEquals(100 - TankGame.blastDamage(45, 70), game.getTanks().get("human").getHealth());
    }

    @Test void pointBlankSelfDestructionDoesNotAwardKillCreditOrHurtTeammates() {
        var game = game(0);
        var shooter = game.getTanks().get("human");
        shooter.setHealth(50); shooter.setCredits(75); shooter.setScore(25);
        var friend = add(game, "friend", 140, 100);
        fire(game, 100, 100);
        assertFalse(shooter.isAlive());
        assertEquals(0, shooter.getHealth());
        assertTrue(shooter.getDiedAt() > 0);
        assertEquals(75, shooter.getCredits());
        assertEquals(25, shooter.getScore());
        assertEquals(100, friend.getHealth());
    }

    @Test void diagonalAndParallelShellsUseSweptWallContact() {
        var wall = new TankGame.Wall(200, 200, 1, 50);
        assertEquals(.475, wall.hitFraction(100, 100, 300, 300, 5), .001);
        assertEquals(Double.POSITIVE_INFINITY, wall.hitFraction(100, 100, 100, 500, 5));
        assertEquals(Double.POSITIVE_INFINITY, wall.hitFraction(100, 100, 150, 100, 5));
        assertEquals(Double.POSITIVE_INFINITY, wall.hitFraction(300, 220, 400, 220, 5));
    }

    @Test void aimIsIndependentOfHullAndRangeIsEnforcedByServer() {
        var game = game(5);
        fire(game, 100, 99999);
        var shell = game.getBullets().get(0);
        var tank = game.getTanks().get("human");
        assertEquals(0, tank.getAngle());
        assertEquals(Math.PI / 2, tank.getTurretAngle(), .001);
        assertTrue(shell.isShell());
        assertEquals(700, Math.hypot(shell.getTargetX() - 100, shell.getTargetY() - 100), .001);
        var invalid = new TankGame.TankInput(); invalid.setTargetX(Double.NaN);
        game.handleInput("human", invalid);
        assertNotSame(invalid, tank.getInput());
    }

    @Test void assaultMovesFasterAndUpgradesHaveCostsCapsAndSurviveRespawn() {
        var game = game(5);
        var tank = game.getTanks().get("human");
        var input = new TankGame.TankInput(); input.setMovingForward(true);
        game.handleInput("human", input); game.update();
        assertEquals(109, tank.getX());
        assertFalse(game.upgrade("human", "ENGINE"));
        tank.setCredits(2000);
        assertTrue(game.upgrade("human", "ENGINE"));
        assertTrue(game.upgrade("human", "ENGINE"));
        assertTrue(game.upgrade("human", "ENGINE"));
        assertFalse(game.upgrade("human", "ENGINE"));
        assertFalse(game.upgrade("human", "NOT_A_BRANCH"));
        assertEquals(1400, tank.getCredits());
        assertTrue(game.upgrade("human", "ARMOR"));
        assertEquals(135, tank.getMaxHealth());
        tank.setScore(250); tank.setAlive(false); tank.setHealth(0); tank.setDiedAt(System.currentTimeMillis() - 3100);
        assertFalse(game.upgrade("human", "CANNON"));
        assertTrue(game.respawn("human"));
        var fresh = game.getTanks().get("human");
        assertEquals(3, fresh.getEngineLevel());
        assertEquals(135, fresh.getHealth());
        assertEquals(1300, fresh.getCredits());
        assertEquals(250, fresh.getScore());
    }

    @Test void clearedWavesSpawnReinforcementsAndRotateMapAfterThreeWaves() {
        var game = game(0);
        for (int i = 0; i < 60; i++) game.update();
        assertEquals(1, game.getWave());
        assertEquals(7, game.getInfantry().size());
        assertTrue(game.getTanks().keySet().stream().anyMatch(id -> id.startsWith("bot-wave-")));
        for (int wave = 1; wave <= 3; wave++) {
            game.getInfantry().clear(); game.getTanks().values().removeIf(t -> t.getPlayerId().startsWith("bot-"));
            for (int i = 0; i < 100; i++) game.update();
        }
        assertEquals(4, game.getWave());
        assertEquals(TankMaps.Layout.WAREHOUSE, game.getMapLayout());
        assertEquals(225, game.getTanks().get("human").getCredits());
    }

    @Test void infantryRoutesAroundCoverAndCannotShootThroughWalls() {
        var game = game(0);
        game.getWalls().add(new TankGame.Wall(230, 20, 20, 380));
        var unit = new TankGame.Infantry("patrol", 400, 100, 45, 1);
        game.getInfantry().add(unit);
        game.update();
        assertEquals(100, game.getTanks().get("human").getHealth());
        for (int i = 0; i < 550; i++) game.update();
        assertTrue(unit.getX() < 220, "Infantry must find the corridor around cover to reach the player");
    }

    @Test void classicRetainsDirectFireAndDoesNotSpawnInfantryOrOfferUpgrades() {
        var game = new TankGame("classic", 1800, 1200, 5, 15, 0);
        game.getWalls().clear();
        var tank = add(game, "human", 100, 100);
        game.setState(TankGame.GameState.IN_PROGRESS);
        fire(game, 400, 100);
        assertFalse(game.getBullets().get(0).isShell());
        assertEquals(25, game.getBullets().get(0).getDamage());
        tank.setCredits(1000);
        assertFalse(game.upgrade("human", "ENGINE"));
        assertTrue(game.getInfantry().isEmpty());
        assertEquals(TankGame.Mode.CLASSIC, game.getGameState().getMode());
    }

    @Test void snapshotsCopyInfantryAndIncludeShellTargets() {
        var game = game(0);
        game.getInfantry().add(new TankGame.Infantry("inf", 400, 100, 45, 999));
        fire(game, 600, 100);
        var snapshot = game.getGameState("human");
        game.getInfantry().get(0).setHealth(1);
        game.getBullets().get(0).setTargetX(700);
        assertEquals(45, snapshot.getInfantry().get(0).getHealth());
        assertEquals(600, snapshot.getBullets().get(0).getTargetX());
        assertTrue(snapshot.getBullets().get(0).isShell());
    }
}
