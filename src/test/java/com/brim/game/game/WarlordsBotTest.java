package com.brim.game.game;

import com.brim.game.game.warlords.*;
import com.brim.game.game.warlords.WarlordsBot.Stats;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarlordsBotTest {
    @Test void tacticalBotTakesGuaranteedVictoryInsteadOfHealing() {
        var board=new CombatBoard(765);
        var choice=WarlordsBot.choose(board.snapshot(),new Stats(1,140,30,0,5,0),new Stats(20,140,0,0,5,0),4,0);
        assertEquals("STRIKE",choice.skill());assertNull(choice.swap());
    }
    @Test void searchOnlyUsesVisibleTilesAndDoesNotAdvanceActualRefillRandomness() {
        var searched=new CombatBoard(345);var untouched=new CombatBoard(345);int[][] before=searched.snapshot();
        for(int level=1;level<=4;level++) {
            var choice=WarlordsBot.choose(searched.snapshot(),new Stats(140,140,10,0,5,0),new Stats(140,140,10,0,5,0),level,7);
            assertNotNull(choice);assertTrue(searched.legalMoves().contains(choice.swap()));
            assertArrayEquals(before,searched.snapshot());
        }
        var move=searched.legalMoves().get(0);
        var preview=CombatBoard.project(before,move,true);
        assertTrue(java.util.Arrays.stream(preview.board()).flatMapToInt(java.util.Arrays::stream).anyMatch(v->v==0));
        assertArrayEquals(before,searched.snapshot());
        searched.resolve(move);untouched.resolve(move);assertArrayEquals(untouched.snapshot(),searched.snapshot());
    }
    @Test void beginnerVariesMovesWhileHigherLevelsRemainLegalAndDeterministic() {
        var board=new CombatBoard(12345);var self=new Stats(90,140,50,0,5,1);var enemy=new Stats(140,140,30,0,6,0);
        var choices=new java.util.HashSet<WarlordsBot.Choice>();
        for(int i=0;i<4;i++)choices.add(WarlordsBot.choose(board.snapshot(),self,enemy,1,i));
        assertTrue(choices.size()>1);
        for(int level=2;level<=4;level++) {
            var choice=WarlordsBot.choose(board.snapshot(),self,enemy,level,0);
            assertEquals(choice,WarlordsBot.choose(board.snapshot(),self,enemy,level,999));
            assertTrue(choice.swap()==null||board.legalMoves().contains(choice.swap()));
        }
    }
}
