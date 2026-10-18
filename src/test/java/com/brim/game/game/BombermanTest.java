package com.brim.game.game;

import com.brim.game.game.bomberman.BombermanGame;
import com.brim.game.game.bomberman.BombermanGame.Input;
import com.brim.game.model.dto.Player;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BombermanTest {
    private BombermanGame game() {
        var game=new BombermanGame("room","EASY");
        var a=new Player();a.setId("a");a.setUsername("A");
        var b=new Player();b.setId("b");b.setUsername("B");game.setPlayers(List.of(a,b));return game;
    }
    private void ticks(BombermanGame game,int n) {for(int i=0;i<n;i++)game.tick();}
    private BombermanGame.Avatar avatar(BombermanGame game,String id) {return game.getState().players().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();}
    @Test void serverBlocksWallsLimitsMovementAndExpiresStaleInput() {
        var game=game();
        assertFalse(game.makeMove("x",new Input("MOVE","RIGHT")));
        assertFalse(game.makeMove("a",new Input("MOVE","TELEPORT")));
        assertFalse(game.makeMove("a",new Input(null,null)));
        assertTrue(game.makeMove("a",new Input("MOVE","UP")));ticks(game,2);assertEquals(1,avatar(game,"a").row());
        assertTrue(game.makeMove("a",new Input("MOVE","RIGHT")));game.tick();assertEquals(2,avatar(game,"a").column());
        game.tick();assertEquals(2,avatar(game,"a").column());
        int[][] open=(int[][])ReflectionTestUtils.getField(game,"board");for(int c=1;c<12;c++)open[1][c]=0;
        ticks(game,10);int stopped=avatar(game,"a").column();ticks(game,5);assertEquals(stopped,avatar(game,"a").column());
        assertTrue(stopped<10);
    }
    @Test void bombsHaveServerFuseDestroyCratesAndEliminateOwner() {
        var game=game();int[][] board=(int[][])ReflectionTestUtils.getField(game,"board");board[1][2]=2;
        assertTrue(game.makeMove("a",new Input("BOMB",null)));game.tick();
        assertEquals(1,game.getState().bombs().size());ticks(game,19);assertTrue(avatar(game,"a").alive());
        game.tick();assertTrue(game.getState().bombs().isEmpty());assertFalse(avatar(game,"a").alive());
        assertEquals(0,game.getState().board()[1][2]);assertEquals("b",game.getWinner());
        assertFalse(game.makeMove("a",new Input("BOMB",null)));
    }
    @Test void playerCanLeaveBombButCannotWalkBackThroughItAndCapacityIsLimited() {
        var game=game();
        assertTrue(game.makeMove("a",new Input("BOMB",null)));game.tick();
        game.makeMove("a",new Input("MOVE","RIGHT"));game.tick();assertEquals(2,avatar(game,"a").column());
        game.makeMove("a",new Input("BOMB",null));game.tick();assertEquals(1,game.getState().bombs().size());
        game.makeMove("a",new Input("MOVE","LEFT"));ticks(game,3);assertEquals(2,avatar(game,"a").column());
    }
    @Test void chainReactionDetonatesLaterBombAndSnapshotIsDetached() {
        var game=game();
        @SuppressWarnings("unchecked") Map<BombermanGame.Cell,BombermanGame.Bomb> bombs=(Map<BombermanGame.Cell,BombermanGame.Bomb>)ReflectionTestUtils.getField(game,"bombs");
        int[][] board=(int[][])ReflectionTestUtils.getField(game,"board");board[1][2]=0;board[1][3]=0;
        bombs.put(new BombermanGame.Cell(1,1),new BombermanGame.Bomb(1,1,"a",3,1));
        bombs.put(new BombermanGame.Cell(1,3),new BombermanGame.Bomb(1,3,"b",2,20));
        game.tick();assertTrue(game.getState().bombs().isEmpty());
        assertTrue(game.getState().flames().contains(new BombermanGame.Cell(1,4)));
        var snapshot=game.getState();snapshot.board()[0][0]=0;assertEquals(1,game.getState().board()[0][0]);
    }
    @Test void simultaneousDeathsDrawAndLeaveEliminatesAvatar() {
        var game=game();
        game.makeMove("a",new Input("BOMB",null));game.makeMove("b",new Input("BOMB",null));ticks(game,21);
        assertEquals("DRAW",game.getState().status());assertNull(game.getWinner());
        var other=game();other.retainPlayers(Set.of("a"));other.tick();assertEquals("a",other.getWinner());
    }

    @Test void pickingUpItemsChangesOnlyServerStatsAndRoundTimesOut() {
        var game=game();
        @SuppressWarnings("unchecked") Map<BombermanGame.Cell,String> items=(Map<BombermanGame.Cell,String>)ReflectionTestUtils.getField(game,"items");
        items.put(new BombermanGame.Cell(1,1),"RANGE");game.tick();assertEquals(3,avatar(game,"a").range());
        items.put(new BombermanGame.Cell(1,1),"CAPACITY");game.tick();assertEquals(2,avatar(game,"a").capacity());
        assertTrue(game.getState().items().isEmpty());
        ticks(game,1798);assertEquals("DRAW",game.getState().status());assertEquals(0,game.getState().ticksLeft());
    }
}
