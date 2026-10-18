package com.brim.game.game;

import com.brim.game.game.battleship.BattleshipGame;
import com.brim.game.game.battleship.BattleshipGame.Move;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class BattleshipTest {
    private BattleshipGame game() {var g=new BattleshipGame("r","MEDIUM");g.setPlayer1("a");g.setPlayer2("b");return g;}
    private void place(BattleshipGame game,String id) {
        for(int i=0;i<5;i++)assertTrue(game.makeMove(id,new Move("PLACE",i*2,0,true)));
    }
    @Test void placementRejectsOverlapEdgesNullAndOutsiders() {
        var game=game();
        assertFalse(game.makeMove("x",new Move("AUTO_PLACE",null,null,null)));
        assertFalse(game.makeMove("a",new Move("FIRE",0,0,null)));
        assertFalse(game.makeMove("a",new Move("PLACE",0,8,true)));
        assertFalse(game.makeMove("a",new Move("PLACE",null,0,true)));
        assertTrue(game.makeMove("a",new Move("PLACE",0,0,true)));
        assertFalse(game.makeMove("a",new Move("PLACE",0,1,true)));
        assertEquals(1,game.getState("a").placedShips());
        assertTrue(game.makeMove("b",new Move("PLACE",0,0,false)));
        assertEquals("PLACEMENT",game.getState("a").phase());
    }
    @Test void snapshotsNeverRevealEnemyShipsIncludingGenericAndUnknownViewers() throws Exception {
        var game=game();place(game,"a");place(game,"b");
        var mine=game.getState("a");
        assertEquals(17,Arrays.stream(mine.ownBoard()).flatMapToInt(Arrays::stream).filter(v->v==1).count());
        assertTrue(Arrays.stream(mine.targetBoard()).flatMapToInt(Arrays::stream).allMatch(v->v==0));
        String publicJson=new ObjectMapper().writeValueAsString(game.getState());
        assertFalse(publicJson.contains("ownBoard"));assertFalse(publicJson.contains("ships"));
        assertThrows(IllegalArgumentException.class,()->game.getState("outsider"));
        mine.ownBoard()[0][0]=0;assertEquals(1,game.getState("a").ownBoard()[0][0]);
    }
    @Test void battleEnforcesTurnsAndDuplicateShotsThenEndsAfterSeventeenHits() {
        var game=game();place(game,"a");place(game,"b");
        assertFalse(game.makeMove("b",new Move("FIRE",0,0,null)));
        int misses=0;
        for(int ship=0;ship<5;ship++)for(int c=0;c<BattleshipGame.FLEET.get(ship);c++) {
            assertTrue(game.makeMove("a",new Move("FIRE",ship*2,c,null)));
            if(!game.isGameOver()) {
                assertTrue(game.makeMove("b",new Move("FIRE",misses<10?9:7,misses%10,null)));misses++;
                assertFalse(game.makeMove("a",new Move("FIRE",ship*2,c,null)));
            }
        }
        assertEquals("a",game.getWinner());assertEquals(0,game.getState("a").remainingOpponent());
        assertFalse(game.makeMove("b",new Move("FIRE",8,9,null)));
    }
    @Test void botPlacementsAreCompleteAndShootingIsLegal() {
        var game=game();assertTrue(game.makeMove("a",game.generateBotMove()));
        assertTrue(game.makeMove("b",game.generateBotMove()));
        assertEquals(17,game.getState("b").remainingOwn());
        assertTrue(game.makeMove("a",game.generateBotMove()));
    }
}
