package com.brim.game.game;

import com.brim.game.game.matchthree.MatchThreeGame;
import com.brim.game.game.matchthree.MatchThreeGame.Cell;
import com.brim.game.game.warlords.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class CombatBoardSpecialTest {
    private int[][] plain() {
        int[][] cells=new int[8][8];
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)cells[r][c]=(r*2+c)%6+1;
        return cells;
    }
    private CombatBoard board(int[][] cells) {
        return CombatBoard.restore(new CombatBoard.Saved(cells,123456L));
    }
    private int[][] four() {
        int[][] cells=plain();
        cells[7][0]=cells[7][1]=cells[7][3]=cells[6][2]=1;
        cells[7][2]=cells[7][4]=2;
        assertTrue(MatchThreeGame.findMatches(cells).isEmpty());
        return cells;
    }
    private int[][] transpose(int[][] source) {
        int[][] result=new int[8][8];
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)result[r][c]=source[c][r];
        return result;
    }

    @Test void fourHorizontalAndVerticalForgeTheCorrespondingLineAtMovedDestination() {
        var horizontal=board(four()).resolve(new CombatBoard.Swap(6,2,7,2)).waves().get(0);
        assertEquals(List.of(new CombatBoard.Special(new Cell(7,2),11)),horizontal.created());
        assertEquals(3,horizontal.counts()[1]);assertEquals(11,horizontal.cleared()[7][2]);
        assertFalse(horizontal.cells().contains(new Cell(7,2)));
        var vertical=board(transpose(four())).resolve(new CombatBoard.Swap(2,6,2,7)).waves().get(0);
        assertEquals(List.of(new CombatBoard.Special(new Cell(2,7),21)),vertical.created());
        assertEquals(3,vertical.counts()[1]);
    }
    @Test void fiveStraightForgesCrossInsteadOfLine() {
        int[][] cells=four();cells[7][4]=1;cells[7][5]=2;
        var wave=board(cells).resolve(new CombatBoard.Swap(6,2,7,2)).waves().get(0);
        assertEquals(List.of(new CombatBoard.Special(new Cell(7,2),41)),wave.created());
        assertEquals(4,wave.counts()[1]);
    }
    @ParameterizedTest @CsvSource({"3","2"})
    void intersectingTAndLPatternsForgeAreaWithoutDoubleCountingTheIntersection(int centerColumn) {
        int[][] cells=plain();
        cells[4][2]=cells[4][3]=cells[4][4]=1;
        cells[2][centerColumn]=cells[3][centerColumn]=cells[5][centerColumn]=1;
        cells[4][centerColumn]=2;
        assertTrue(MatchThreeGame.findMatches(cells).isEmpty());
        var wave=board(cells).resolve(new CombatBoard.Swap(5,centerColumn,4,centerColumn)).waves().get(0);
        assertEquals(List.of(new CombatBoard.Special(new Cell(4,centerColumn),31)),wave.created());
        assertEquals(4,wave.counts()[1]);assertEquals(4,wave.cells().size());
    }
    @ParameterizedTest @CsvSource({"1,3,2,3,3,8","1,2,3,3,3,9","2,2,3,3,3,8","2,3,2,3,3,9","3,4,3,3,3,9","3,0,1,0,0,4","4,2,3,3,3,15"})
    void specialSwapIsLegalWithoutAMatchAndBlastsClipAtBoardEdges(int kind,int r,int c,int toR,int toC,int count) {
        int[][] cells=plain();cells[r][c]=CombatBoard.tile(cells[r][c],kind);
        var move=new CombatBoard.Swap(r,c,toR,toC);var board=board(cells);
        assertTrue(board.legalMoves().contains(move)||board.legalMoves().contains(new CombatBoard.Swap(toR,toC,r,c)));
        var preview=CombatBoard.project(cells,move,false);
        var wave=board.resolve(move).waves().get(0);
        assertEquals(count,wave.cells().size());assertEquals(count,Arrays.stream(wave.counts()).sum());
        assertEquals(1,wave.activated().size());assertEquals(new Cell(toR,toC),wave.activated().get(0).cell());
        assertEquals(kind,CombatBoard.special(wave.before()[toR][toC]));
        assertArrayEquals(preview.waves().get(0),wave.counts());
    }
    @Test void lineAreaAndCrossChainExactlyOnceAndEveryResourceIsCountedOnce() {
        int[][] cells=plain();
        cells[3][2]=CombatBoard.tile(cells[3][2],CombatBoard.ROW);
        cells[3][6]=CombatBoard.tile(cells[3][6],CombatBoard.COLUMN);
        cells[5][6]=CombatBoard.tile(cells[5][6],CombatBoard.AREA);
        cells[5][5]=CombatBoard.tile(cells[5][5],CombatBoard.CROSS);
        var move=new CombatBoard.Swap(3,2,3,3);
        var wave=board(cells).resolve(move).waves().get(0);
        Set<Cell> expected=new HashSet<>();
        for(int i=0;i<8;i++){expected.add(new Cell(3,i));expected.add(new Cell(i,6));expected.add(new Cell(5,i));expected.add(new Cell(i,5));}
        for(int r=4;r<=6;r++)for(int c=5;c<=7;c++)expected.add(new Cell(r,c));
        assertEquals(expected,new HashSet<>(wave.cells()));
        assertEquals(wave.cells().size(),new HashSet<>(wave.cells()).size());
        assertEquals(4,wave.activated().size());assertEquals(4,wave.activated().stream().map(CombatBoard.Special::cell).distinct().count());
        int[] counts=new int[7];
        for(Cell cell:expected)counts[CombatBoard.resource(wave.before()[cell.row()][cell.column()])]++;
        assertArrayEquals(counts,wave.counts());
        assertArrayEquals(counts,CombatBoard.project(cells,move,false).waves().get(0));
    }
    @Test void matchingByResourceTriggersAnExistingSpecialAndPreservesItsEncodedEventSnapshot() {
        int[][] cells=plain();
        cells[7][0]=1;cells[7][1]=11;cells[7][2]=2;cells[7][3]=3;cells[6][2]=1;
        var wave=board(cells).resolve(new CombatBoard.Swap(6,2,7,2)).waves().get(0);
        assertEquals(8,wave.cells().size());assertEquals(11,wave.before()[7][1]);
        assertEquals(List.of(new CombatBoard.Special(new Cell(7,1),11)),wave.activated());
    }
    @Test void identicalSpecialsCanBeSwappedAndBothTriggerWithoutDuplicateResources() {
        int[][] cells=plain();cells[3][2]=cells[3][3]=13;
        var move=new CombatBoard.Swap(3,2,3,3);var board=board(cells);
        assertTrue(board.legalMoves().contains(move));
        var wave=board.resolve(move).waves().get(0);
        assertEquals(2,wave.activated().size());assertEquals(8,wave.cells().size());assertEquals(8,Arrays.stream(wave.counts()).sum());
    }
    @Test void newlyForgedSpecialSurvivesAnOverlappingBlastUntilALaterWave() {
        int[][] cells=four();cells[7][0]=11;
        var wave=board(cells).resolve(new CombatBoard.Swap(6,2,7,2)).waves().get(0);
        assertEquals(1,wave.activated().size());assertEquals(1,wave.created().size());
        assertEquals(11,wave.cleared()[7][2]);assertFalse(wave.cells().contains(new Cell(7,2)));
        assertEquals(7,Arrays.stream(wave.counts()).sum());
    }
    @Test void invalidMovesAndPreviewsNeverChangeTheBoardOrRefillState() {
        var board=board(plain());var saved=board.save();
        var invalid=new CombatBoard.Swap(2,2,2,3);
        assertNull(board.resolve(invalid));assertNull(board.resolve(new CombatBoard.Swap(-1,0,0,0)));
        assertNull(board.resolve(new CombatBoard.Swap(0,0,7,7)));
        assertTrue(CombatBoard.project(board.snapshot(),invalid,true).waves().isEmpty());
        assertEquals(0,Arrays.stream(board.preview(invalid)).sum());
        assertArrayEquals(saved.cells(),board.snapshot());assertEquals(saved.randomState(),board.save().randomState());
    }
    @Test void ordinaryAndEncodedSavedBoardsResumeExactlyIncludingFutureRefills() {
        var ordinary=new CombatBoard(17);var old=CombatBoard.restore(ordinary.save());
        assertArrayEquals(ordinary.snapshot(),old.snapshot());
        int[][] cells=plain();cells[4][3]=36;cells[2][6]=41;
        var original=board(cells);var restored=CombatBoard.restore(original.save());
        var move=new CombatBoard.Swap(4,3,3,3);
        original.resolve(move);restored.resolve(move);
        assertArrayEquals(original.snapshot(),restored.snapshot());assertEquals(original.save().randomState(),restored.save().randomState());
        var bad=plain();bad[0][0]=51;
        assertThrows(IllegalArgumentException.class,()->board(bad));
        var empty=plain();empty[0][0]=0;
        assertThrows(IllegalArgumentException.class,()->board(empty));
    }
    @Test void visibleProjectionCreatesSpecialsLeavesHolesAndNeverUsesHiddenRefills() {
        int[][] cells=four();var one=board(cells);var two=board(cells);var move=new CombatBoard.Swap(6,2,7,2);
        for(int level=1;level<=4;level++) {
            var choice=WarlordsBot.choose(one.snapshot(),new WarlordsBot.Stats(140,140,10,0,5,0),new WarlordsBot.Stats(140,140,10,0,5,0),level,7);
            assertNotNull(choice);assertTrue(one.legalMoves().contains(choice.swap()));
        }
        var projected=CombatBoard.project(cells,move,true);
        assertTrue(Arrays.stream(projected.board()).flatMapToInt(Arrays::stream).anyMatch(v->v==0));
        assertTrue(Arrays.stream(projected.board()).flatMapToInt(Arrays::stream).anyMatch(v->CombatBoard.special(v)==CombatBoard.ROW));
        assertArrayEquals(cells,one.snapshot());assertEquals(two.save().randomState(),one.save().randomState());
        one.resolve(move);two.resolve(move);assertArrayEquals(two.snapshot(),one.snapshot());
    }
}
