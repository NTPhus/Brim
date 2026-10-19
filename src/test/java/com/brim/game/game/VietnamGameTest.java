package com.brim.game.game;

import com.brim.game.game.vietnam.VietnamGame;
import com.brim.game.model.dto.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VietnamGameTest {
    private VietnamGame game(int chapter) {
        var game=new VietnamGame("room","MEDIUM");game.setPlayer1("human");game.configure(Map.of("vietnamChapter",chapter));return game;
    }
    private void input(VietnamGame g,double dx,double dy,String action,boolean fire) {
        assertTrue(g.makeMove("human",new VietnamGame.Input(dx,dy,1500,500,fire,false,action)));g.tick();
    }
    private void walk(VietnamGame g,double x,double y) {
        for(int i=0;i<300;i++) {
            if(g.isGameOver())return;
            var a=g.getState().units().get(0);
            if(Math.abs(a.x()-x)<15&&Math.abs(a.y()-y)<15)return;
            double dx=Math.abs(a.x()-x)<15?0:Math.signum(x-a.x()),dy=Math.abs(a.y()-y)<15?0:Math.signum(y-a.y());
            input(g,dx,dy,"",false);
        }
        fail("Route blocked at "+g.getState().units().get(0));
    }
    @Test void infantryMissionCanBeClearedWithSquadSupportAndTrenchRoute() {
        var g=game(1);
        walk(g,120,390);walk(g,400,390);assertEquals(1,g.getState().objective());
        walk(g,680,390);
        for(int tick=0;tick<300&&!g.isGameOver();tick++) {
            var me=g.getState().units().get(0);
            var target=g.getState().units().stream().filter(a->!a.ally()&&a.hp()>0)
                    .min(Comparator.comparingDouble(a->Math.hypot(a.x()-me.x(),a.y()-me.y()))).orElse(null);
            if(target==null)break;
            assertTrue(g.makeMove("human",new VietnamGame.Input(0,0,target.x(),target.y(),true,true,g.getState().ammo()==0?"RELOAD":"")));
            g.tick();
        }
        assertTrue(g.getState().units().get(0).hp()>0,"The squad should survive a supported trench approach");
        walk(g,680,210);walk(g,780,210);input(g,0,0,"INTERACT",false);
        assertEquals(2,g.getState().objective(),g.getState().units().toString()+g.getState().notice());
        walk(g,780,130);walk(g,1430,130);walk(g,1430,160);
        assertEquals("human",g.getWinner());
    }

    @Test void unitsNeverSpawnInsideHistoricalScenery() {
        for(int chapter=1;chapter<=3;chapter++) {
            var state=game(chapter).getState();
            for(var unit:state.units()) assertTrue(state.walls().stream().noneMatch(w->w.intersects(unit.x(),unit.y(),unit.tank()?23:12)),unit.name()+" must spawn outside cover");
        }
    }

    @Test void invalidIdentityAndNonFiniteInputCannotMoveAvatar() {
        var g=game(1);double x=g.getState().units().get(0).x();
        assertFalse(g.makeMove("outsider",new VietnamGame.Input(1,0,0,0,false,false,"")));
        assertFalse(g.makeMove("human",new VietnamGame.Input(Double.NaN,0,0,0,false,false,"")));
        g.tick();assertEquals(x,g.getState().units().get(0).x());
        input(g,1e20,0,"",false);assertEquals(x+10,g.getState().units().get(0).x());
        for(int i=0;i<10;i++)g.tick();double stopped=g.getState().units().get(0).x();g.tick();assertEquals(stopped,g.getState().units().get(0).x());
    }
    @Test void lockedChaptersAreClampedOnServerAndCampaignIsItsOwnSoloRealtimeGame() {
        var g=new VietnamGame(id->1,(id,stage)->fail("No completion expected"));g.setPlayer1("human");g.configure(Map.of("vietnamChapter",3));
        assertEquals(1,g.getState().chapter().id());assertTrue(g.realtime());
        assertTrue(GameType.VIETNAM_CAMPAIGN.isSolo());assertEquals(1,GameSettings.defaultFor(GameType.VIETNAM_CAMPAIGN).getMaxPlayers());
    }
    @Test void tunnelRequiresEntranceAndKeepsUndergroundMovementOnTunnelNetwork() {
        var g=game(2);input(g,0,0,"INTERACT",false);assertEquals(0,g.getState().layer());
        walk(g,215,755);input(g,0,0,"INTERACT",false);assertEquals(1,g.getState().layer());assertEquals(1,g.getState().objective());
        double x=g.getState().units().get(0).x();
        for(int i=0;i<20;i++)input(g,-1,0,"",true);
        assertTrue(g.getState().units().get(0).x()>=180);assertEquals(12,g.getState().ammo());assertTrue(g.getState().shots().isEmpty());
        assertTrue(x>g.getState().units().get(0).x());
    }
    @Test void tunnelMissionCanBeCompletedThroughActualRoutesAndAwardsOnlyOnce() {
        List<Integer> completed=new ArrayList<>();var g=new VietnamGame(id->3,(id,stage)->completed.add(stage));g.setPlayer1("human");g.configure(Map.of("vietnamChapter",2));
        walk(g,215,755);input(g,0,0,"INTERACT",false);
        walk(g,710,760);walk(g,710,320);input(g,0,0,"INTERACT",false);assertEquals(2,g.getState().objective());
        walk(g,710,760);walk(g,1180,760);input(g,0,0,"INTERACT",false);assertEquals(3,g.getState().objective());
        walk(g,1155,755);input(g,0,0,"INTERACT",false);
        assertTrue(g.isGameOver());assertEquals("human",g.getWinner());assertEquals(List.of(2),completed);
        g.tick();assertEquals(List.of(2),completed);
        assertFalse(g.makeMove("human",new VietnamGame.Input(0,0,0,0,false,false,"INTERACT")));
    }
    @Test void tankMissionRequiresGateInteractionAndNeverRequiresFiringAtPalace() {
        var g=game(3);assertTrue(g.getState().units().get(0).tank());
        walk(g,120,520);walk(g,700,520);assertEquals(1,g.getState().objective());
        walk(g,1330,520);
        for(int i=0;i<8;i++)input(g,1,0,"",false);
        assertTrue(g.getState().units().get(0).x()<1350);
        input(g,0,0,"INTERACT",false);assertEquals(2,g.getState().objective());
        walk(g,1470,520);assertEquals("human",g.getWinner());assertEquals(5,g.getState().ammo());
    }
    @Test void ammunitionAndReloadAreAuthoritativeAndSnapshotsDoNotAliasActors() {
        var g=game(1);var before=g.getState();
        for(int i=0;i<60;i++)input(g,0,0,"",true);
        assertEquals(0,g.getState().ammo());assertEquals(12,before.ammo());
        input(g,0,0,"RELOAD",false);for(int i=0;i<16;i++)g.tick();assertEquals(12,g.getState().ammo());
        input(g,1,0,"",false);assertEquals(120,before.units().get(0).x());
        g.retainPlayers(Set.of());assertTrue(g.isGameOver());assertNull(g.getWinner());
    }
}
