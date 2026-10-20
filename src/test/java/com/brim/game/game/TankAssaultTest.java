package com.brim.game.game;

import com.brim.game.game.tank.TankGame;
import com.brim.game.game.tank.TankMaps;
import com.brim.game.game.tactical.*;
import com.brim.game.model.dto.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TankAssaultTest {
    private TankGame game(){
        var game=new TankGame("assault",1800,1200,5,15,0,"ARENA","ASSAULT");
        game.getWalls().clear();game.setState(TankGame.GameState.IN_PROGRESS);add(game,"human",100,100);return game;
    }
    private TankGame.Tank add(TankGame game,String id,double x,double y){
        var p=new Player();p.setId(id);game.addPlayer(p);var tank=game.getTanks().get(id);tank.setX(x);tank.setY(y);return tank;
    }
    private void fire(TankGame g,double x,double y){
        var i=new TankGame.TankInput();i.setShooting(true);i.setTargetX(x);i.setTargetY(y);g.handleInput("human",i);g.update();g.handleInput("human",new TankGame.TankInput());
    }
    private void command(TankGame g,long seq,String action){var i=new TankGame.TankInput();i.setCommandSeq(seq);i.setAction(action);g.handleInput("human",i);}
    @Test void shellsHaveFiniteInventoryAndSixSecondReloadRegardlessOfInputSpam(){
        var g=game();var me=g.getTanks().get("human");
        fire(g,700,100);assertEquals(7,me.getTactical().cannon().total());
        for(int n=0;n<100;n++){var i=new TankGame.TankInput();i.setShooting(true);i.setTargetX(700.0);i.setTargetY(100.0);g.handleInput("human",i);}
        g.update();assertEquals(7,me.getTactical().cannon().total());assertEquals(CannonState.Phase.LOADING,me.getTactical().cannon().phase());
        assertEquals(1200,me.getMaxHealth());assertEquals(1200,me.getHealth());assertTrue(g.getHealthPacks().isEmpty());
    }
    @Test void firstThinWallStopsShellAndProtectsFarSideFromSplash(){
        var g=game();var target=add(g,"bot-protected",280,100);
        g.getWalls().add(new TankGame.Wall(350,60,10,80));g.getWalls().add(new TankGame.Wall(230,60,1,80));
        fire(g,500,100);for(int n=0;n<20;n++)g.update();
        assertEquals(1,g.getExplosions().size());assertEquals(225,g.getExplosions().get(0).x(),.001);
        assertEquals(900,target.getHealth());assertTrue(g.getBullets().isEmpty());
    }
    @Test void nearbyWallBlastStillHurtsShooterOnNearSide(){
        var g=game();g.getWalls().add(new TankGame.Wall(150,60,10,80));fire(g,500,100);g.update();
        var blast=g.getExplosions().get(0);assertEquals(145,blast.x(),.001);
        int expected=(int)Math.round(TacticalDamage.radial(Math.hypot(blast.x()-100,blast.y()-100),120,220)*.55);
        assertEquals(1200-expected,g.getTanks().get("human").getHealth());
    }
    @Test void selfDestructionCannotAwardCreditOrRespawnWithFreeResources(){
        var g=game();var me=g.getTanks().get("human");me.setHealth(50);me.setCredits(75);me.setScore(25);
        var friend=add(g,"friend",140,100);fire(g,100,100);
        assertFalse(me.isAlive());assertEquals(75,me.getCredits());assertEquals(25,me.getScore());assertEquals(1200,friend.getHealth());
        me.setDiedAt(0);assertFalse(g.respawn("human"));
    }
    @Test void takingSupplyPreservesRemainderAndRetriesCannotApplyAgain(){
        var g=game();g.getSupplies().clear();var me=g.getTanks().get("human");
        var crate=new TankGame.SupplyCrate("ammo",100,100,TankGame.SupplyKind.SHELLS,8);g.getSupplies().add(crate);
        command(g,1,"INTERACT");assertEquals(12,me.getTactical().cannon().total());assertEquals(4,crate.getQuantity());
        me.getTactical().cannon().fire();command(g,1,"INTERACT");assertEquals(11,me.getTactical().cannon().total());assertEquals(4,crate.getQuantity());
        command(g,2,"INTERACT");assertEquals(12,me.getTactical().cannon().total());assertEquals(3,crate.getQuantity());
        for(int n=0;n<100;n++)g.update();assertEquals(3,crate.getQuantity());
    }
    @Test void reconnectAndRejoiningSameRunPreserveAmmunitionFuelAndReload(){
        var g=game();fire(g,700,100);var me=g.getTanks().get("human");me.getTactical().traveled(120);
        var before=g.getGameState("human").getTanks().get("human").getTacticalView();
        g.removePlayer("human");add(g,"human",100,100);
        var after=g.getGameState("human").getTanks().get("human").getTacticalView();
        assertEquals(before,after);assertEquals(2880,after.fuel());assertEquals(7,me.getTactical().cannon().total());
    }
    @Test void repairReservesAKitAndDamageCancelsWithoutPartialHealing(){
        var g=game();var me=g.getTanks().get("human");me.setHealth(400);command(g,1,"REPAIR");
        assertEquals(0,me.getTactical().view().repairKits());assertTrue(me.getTactical().repairing());
        command(g,1,"REPAIR");me.takeDamage(10);assertFalse(me.getTactical().repairing());
        assertEquals(1,me.getTactical().view().repairKits());assertEquals(390,me.getHealth());
    }
    @Test void viewerSnapshotsAndMinimapDoNotRevealEnemiesBehindCoverOrProjectileEndpoints() throws Exception {
        var g=game();var enemy=add(g,"bot-hidden",400,100);g.getWalls().add(new TankGame.Wall(250,60,10,80));g.update();
        var snapshot=g.getGameState("human");assertFalse(snapshot.getTanks().containsKey("bot-hidden"));assertTrue(snapshot.getEnemyMarkers().isEmpty());
        assertEquals(-1,snapshot.getEnemiesRemaining());
        g.getWalls().clear();g.update();assertTrue(g.getGameState("human").getTanks().containsKey("bot-hidden"));
        enemy.setX(1200);g.update();var remembered=g.getGameState("human").getContacts();
        assertEquals(1,remembered.size());assertFalse(remembered.get(0).current());assertTrue(remembered.get(0).x()<500);
        fire(g,700,100);String json=new ObjectMapper().writeValueAsString(g.getGameState("human"));
        assertFalse(json.contains("targetX"));assertFalse(json.contains("targetY"));assertFalse(json.contains("\"tactical\":"));
        assertTrue(json.contains("tacticalView"));
        var oldFuel=snapshot.getTanks().get("human").getTacticalView().fuel();g.getTanks().get("human").getTactical().traveled(10);
        assertEquals(oldFuel,snapshot.getTanks().get("human").getTacticalView().fuel());
    }
    @Test void vehicleBodyStopsShellBeforeDistantSelectedPoint(){
        var g=game();var enemy=add(g,"bot-target",400,100);
        var concealment=new TankGame.Wall(250,50,2,100);concealment.setBlocksMovement(false);concealment.setBlocksProjectile(false);concealment.setBlocksBlast(false);g.getWalls().add(concealment);
        fire(g,700,100);for(int n=0;n<30;n++)g.update();
        assertTrue(enemy.getHealth()<900);assertTrue(g.getExplosions().get(0).x()<400);
    }
    @Test void wavesRetainSupplyAndDoNotRestoreHealthWhenTheyAdvance(){
        var g=game();var me=g.getTanks().get("human");me.setHealth(600);g.getSupplies().get(0).setQuantity(0);
        for(int n=0;n<60;n++)g.update();assertEquals(1,g.getWave());assertEquals(0,g.getSupplies().get(0).getQuantity());
        for(int wave=1;wave<=3;wave++){
            g.getInfantry().clear();g.getTanks().values().removeIf(t->t.getPlayerId().startsWith("bot-"));
            var objective=g.getGameState("human").getMission();me.setX(objective.x());me.setY(objective.y());
            for(int n=0;n<260;n++)g.update();
        }
        assertEquals(4,g.getWave());assertEquals(TankMaps.Layout.WAREHOUSE,g.getMapLayout());assertEquals(600,me.getHealth());assertEquals(345,me.getCredits());
    }
    @Test void classicKeepsItsDirectFireAndItemBehavior(){
        var g=new TankGame("classic",1800,1200,5,15,0);g.getWalls().clear();var me=add(g,"human",100,100);g.setState(TankGame.GameState.IN_PROGRESS);
        fire(g,400,100);assertFalse(g.getBullets().get(0).isShell());assertEquals(25,g.getBullets().get(0).getDamage());
        assertNull(me.getTactical());assertFalse(g.getHealthPacks().isEmpty());assertFalse(g.upgrade("human","ENGINE"));
    }
}
