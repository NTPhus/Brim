package com.brim.game.game;

import com.brim.game.game.tank.TankGame;
import com.brim.game.game.tank.TankMaps;
import com.brim.game.model.dto.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TankMissionTest {
    private TankGame game() {
        TankGame game = new TankGame("sector-test",1800,1200,5,15,0,"ARENA","ASSAULT");
        game.getWalls().clear(); game.setState(TankGame.GameState.IN_PROGRESS); game.setWave(1);
        Player player = new Player(); player.setId("human"); game.addPlayer(player);
        game.getTanks().get("human").setX(100); game.getTanks().get("human").setY(100);
        return game;
    }
    private void advance(TankGame game,int ticks) { for(int n=0;n<ticks;n++)game.update(); }
    private void enter(TankGame game) {
        var objective=game.getGameState("human").getMission();
        game.getTanks().get("human").setX(objective.x());game.getTanks().get("human").setY(objective.y());
    }
    @Test void clearingEnemiesAloneCannotAdvanceAndHoldingAwardsOnlyOneFiniteCache() {
        var game=game();advance(game,300);
        assertEquals(1,game.getWave());assertEquals(0,game.getGameState("human").getMission().heldMs());
        enter(game);int crates=game.getSupplies().size();advance(game,160);
        assertTrue(game.getGameState("human").getMission().secured());
        assertEquals(40,game.getTanks().get("human").getCredits());assertEquals(crates+3,game.getSupplies().size());
        assertEquals(6,game.getSupplies().stream().filter(c->c.getId().equals("sector-1-shells")).findFirst().orElseThrow().getQuantity());
        advance(game,40);assertEquals(40,game.getTanks().get("human").getCredits());assertEquals(crates+3,game.getSupplies().size());
        advance(game,60);assertEquals(2,game.getWave());assertFalse(game.getGameState("human").getMission().secured());
        assertEquals(115,game.getTanks().get("human").getCredits());
    }
    @Test void leavingOrEnemyPresencePausesCaptureAndCoverPreventsRemoteCapture() {
        var game=game();enter(game);advance(game,40);assertEquals(2000,game.getGameState("human").getMission().heldMs());
        var mission=game.getGameState("human").getMission();var me=game.getTanks().get("human");
        me.setX(100);me.setY(100);advance(game,20);assertEquals(2000,game.getGameState("human").getMission().heldMs());
        enter(game);game.getInfantry().add(new TankGame.Infantry("inf-contest",mission.x()+65,mission.y(),60,10000));
        advance(game,20);assertEquals(2000,game.getGameState("human").getMission().heldMs());assertEquals("CONTESTED",game.getGameState("human").getMission().status());
        game.getInfantry().clear();me.setX(mission.x()-70);
        game.getWalls().add(new TankGame.Wall((int)mission.x()-35,(int)mission.y()-40,5,80));
        advance(game,20);assertEquals(2000,game.getGameState("human").getMission().heldMs());
        game.getWalls().clear();enter(game);advance(game,120);assertTrue(game.getGameState("human").getMission().secured());
    }
    @Test void deadVehiclesCannotCaptureAndReconnectPreservesProgress() {
        var game=game();enter(game);advance(game,40);game.removePlayer("human");
        Player player=new Player();player.setId("human");game.addPlayer(player);
        assertEquals(2000,game.getGameState("human").getMission().heldMs());
        var me=game.getTanks().get("human");me.takeDamage(2000);advance(game,160);
        assertEquals(2000,game.getGameState().getMission().heldMs());assertEquals(0,me.getCredits());
    }
    @Test void objectiveTelemetryCannotRevealHiddenContestAndWarningsUseViewerVisibility() throws Exception {
        var game=game();var mission=game.getGameState("human").getMission();
        game.getInfantry().add(new TankGame.Infantry("inf-hidden",mission.x(),mission.y(),60,10000));
        game.getGroundWarnings().add(new TankGame.GroundWarning(1,mission.x(),mission.y(),72,game.getSimulationTime()+1.4));
        var snapshot=game.getGameState("human");assertEquals("APPROACH",snapshot.getMission().status());assertTrue(snapshot.getWarnings().isEmpty());
        assertEquals(-1,snapshot.getEnemiesRemaining());assertFalse(new ObjectMapper().writeValueAsString(snapshot).contains("detonatesAt"));
        enter(game);assertEquals(1,game.getGameState("human").getWarnings().size());assertEquals("CONTESTED",game.getGameState("human").getMission().status());
    }
    @Test void delayedGrenadesCanBeDodgedAndBlastCoverPreventsDamage() {
        var game=game();var me=game.getTanks().get("human");
        game.getGroundWarnings().add(new TankGame.GroundWarning(1,100,100,72,game.getSimulationTime()+1.4));
        advance(game,20);assertEquals(1200,me.getHealth());me.setX(200);advance(game,8);assertEquals(1200,me.getHealth());assertTrue(game.getGroundWarnings().isEmpty());
        game.getGroundWarnings().add(new TankGame.GroundWarning(2,170,100,72,game.getSimulationTime()+1.4));
        game.getWalls().add(new TankGame.Wall(184,60,4,80));advance(game,28);assertEquals(1200,me.getHealth());
        game.getWalls().clear();game.getGroundWarnings().add(new TankGame.GroundWarning(3,200,100,72,game.getSimulationTime()+1.4));
        advance(game,27);assertEquals(1200,me.getHealth());advance(game,1);assertEquals(1148,me.getHealth());
    }
    @Test void grenadierQueuesVisibleWarningInsteadOfInstantDamage() {
        var game=game();var unit=new TankGame.Infantry("inf-grenade",250,100,60,1);unit.setKind("GRENADIER");game.getInfantry().add(unit);
        game.update();assertEquals(1200,game.getTanks().get("human").getHealth());assertEquals(1,game.getGameState("human").getWarnings().size());
        assertEquals(1400,game.getGameState("human").getWarnings().get(0).remainingMs());
        advance(game,28);assertEquals(1148,game.getTanks().get("human").getHealth());
    }
    @Test void classicHasNoMissionOrGroundWarnings() {
        var game=new TankGame("classic",1800,1200,5,15,0);
        assertNull(game.getGameState().getMission());assertTrue(game.getGameState().getWarnings().isEmpty());
    }
    @Test void allSectorCentersFitTankBodiesOnEveryLayoutAndSupportedWorldSize() {
        for(var layout:TankMaps.Layout.values())for(int[] size:new int[][]{{800,600},{3600,2400}}) {
            var game=new TankGame("positions",size[0],size[1],5,15,0,layout.name(),"ASSAULT");
            var positions=new java.util.HashSet<String>();
            for(int wave=1;wave<=3;wave++) {
                game.setWave(wave);var sector=game.getGameState().getMission();
                assertTrue(sector.x()>40&&sector.x()<size[0]-40&&sector.y()>40&&sector.y()<size[1]-40,layout.name());
                assertTrue(game.getWalls().stream().noneMatch(w->w.intersects(sector.x(),sector.y(),35)),layout.name());
                positions.add(sector.x()+":"+sector.y());
            }
            assertEquals(3,positions.size(),layout+" should provide three distinct destinations");
        }
    }
    @Test void mapTransitionClearsOldThreatsAndRetainsTheActualVehicleInventory() {
        var game=game();game.setWave(3);enter(game);var me=game.getTanks().get("human");
        me.setHealth(600);me.getTactical().traveled(100);me.getTactical().cannon().fire();
        game.getGroundWarnings().add(new TankGame.GroundWarning(999,1500,1000,72,1000));
        advance(game,260);
        assertEquals(4,game.getWave());assertEquals("WAREHOUSE",game.getGameState("human").getMapId());
        assertTrue(game.getGroundWarnings().isEmpty());assertFalse(game.getGameState("human").getMission().secured());
        assertEquals(1,game.getGameState("human").getMission().sector());assertEquals(600,me.getHealth());
        assertEquals(2900,me.getTactical().view().fuel());assertEquals(7,me.getTactical().cannon().total());
        assertTrue(game.getSupplies().stream().allMatch(c->c.getId().startsWith("WAREHOUSE-")));
    }
}
