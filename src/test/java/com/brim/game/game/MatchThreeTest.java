package com.brim.game.game;

import com.brim.game.game.matchthree.MatchThreeGame;
import com.brim.game.game.matchthree.MatchThreeGame.Move;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchThreeTest {
    private MatchThreeGame game() {var game=new MatchThreeGame("r","EASY");game.setPlayer1("a");return game;}
    private Move swapFor(int[][] board) {
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)for(int[] d:new int[][]{{1,0},{0,1}}) {
            int y=r+d[0],x=c+d[1];if(y>=8||x>=8||board[r][c]<=0||board[y][x]<=0)continue;
            int value=board[r][c];board[r][c]=board[y][x];board[y][x]=value;
            boolean match=!MatchThreeGame.findMatches(board).isEmpty();
            board[y][x]=board[r][c];board[r][c]=value;
            if(match)return new Move("SWAP",r,c,y,x,null);
        }
        throw new AssertionError("No valid swap");
    }
    @Test void allTenSeededBoardsAreStablePlayableAndDetached() {
        for(int level=1;level<=10;level++) {
            var game=game();game.configure(Map.of("match3Unlocked",10,"match3Level",level));
            var state=game.getState();
            assertEquals(level,state.level());assertEquals(8,state.board().length);
            assertTrue(MatchThreeGame.findMatches(state.board()).isEmpty());
            assertTrue(MatchThreeGame.hasLegalSwap(state.board()));
            assertArrayEquals(state.board(),game.getState().board());
            state.board()[1][1]=99;assertNotEquals(99,game.getState().board()[1][1]);
        }
    }
    @Test void invalidSwapsAreRevertedWithoutSpendingMoveAndLevelSelectionIsBounded() {
        var game=game();var before=game.getState();
        assertFalse(game.makeMove("x",swapFor(before.board())));
        assertFalse(game.makeMove("a",new Move("SWAP",-1,0,0,0,null)));
        assertFalse(game.makeMove("a",new Move("SWAP",0,0,2,2,null)));
        assertFalse(game.makeMove("a",new Move("SWAP",null,0,0,0,null)));
        assertFalse(game.makeMove("a",new Move("LEVEL",null,null,null,null,2)));
        boolean checked=false;
        for(int r=0;r<8&&!checked;r++)for(int c=0;c<7&&!checked;c++) {
            int[][] copy=game.getState().board();int v=copy[r][c];copy[r][c]=copy[r][c+1];copy[r][c+1]=v;
            if(MatchThreeGame.findMatches(copy).isEmpty()) {
                assertFalse(game.makeMove("a",new Move("SWAP",r,c,r,c+1,null)));checked=true;
            }
        }
        assertTrue(checked);assertEquals(before.movesLeft(),game.getState().movesLeft());
        assertArrayEquals(before.board(),game.getState().board());
    }
    @Test void matchFinderDeduplicatesIntersectionAndHandlesBordersAndSimultaneousRuns() {
        int[][] board=new int[8][8];for(int[] row:board)Arrays.fill(row,-1);
        for(int c=1;c<=3;c++)board[2][c]=1;
        for(int r=1;r<=3;r++)board[r][2]=1;
        board[7][5]=board[7][6]=board[7][7]=2;
        var matches=MatchThreeGame.findMatches(board);
        assertEquals(8,matches.size());assertTrue(matches.contains(new MatchThreeGame.Cell(7,7)));
    }
    @Test void seededCommandsReplayExactlyAndCascadesScoreOnlyOnServer() {
        var first=game();var second=game();
        for(int i=0;i<8;i++) {
            Move move=swapFor(first.getState().board());
            assertTrue(first.makeMove("a",move));assertTrue(second.makeMove("a",move));
            var one=first.getState();var two=second.getState();
            assertArrayEquals(one.board(),two.board());assertEquals(one.score(),two.score());assertEquals(one.collected(),two.collected());
            assertTrue(MatchThreeGame.findMatches(one.board()).isEmpty());assertTrue(MatchThreeGame.hasLegalSwap(one.board()));
            assertTrue(one.events().stream().anyMatch(e->e.type().equals("CLEAR")));
            if(one.levelStatus().equals("WON"))break;
        }
    }
    @Test void wonLevelUnlocksNextLostLevelRetriesAndCheckpointRestoresSelection() {
        var game=game();ReflectionTestUtils.setField(game,"score",10000);
        assertTrue(game.makeMove("a",swapFor(game.getState().board())));
        assertEquals("WON",game.getState().levelStatus());assertEquals(2,game.getState().unlocked());
        assertTrue(game.makeMove("a",new Move("NEXT",null,null,null,null,null)));
        assertEquals(2,game.getState().level());
        var restored=game();restored.configure(game.checkpoint());assertEquals(2,restored.getState().level());
        ReflectionTestUtils.setField(game,"movesLeft",1);
        assertTrue(game.makeMove("a",swapFor(game.getState().board())));
        assertEquals("LOST",game.getState().levelStatus());
        assertFalse(game.makeMove("a",swapFor(game.getState().board())));
        assertTrue(game.makeMove("a",new Move("RETRY",null,null,null,null,null)));
        assertEquals("PLAYING",game.getState().levelStatus());assertEquals(20,game.getState().movesLeft());
    }
    @Test void deadBoardHasNoMoveAndBlockedCellsCannotBeSwapped() {
        int[][] dead=new int[8][8];for(int r=0;r<8;r++)for(int c=0;c<8;c++)dead[r][c]=(r+c)%6+1;
        assertTrue(MatchThreeGame.findMatches(dead).isEmpty());assertFalse(MatchThreeGame.hasLegalSwap(dead));
        var game=game();game.configure(Map.of("match3Unlocked",10,"match3Level",4));
        assertFalse(game.makeMove("a",new Move("SWAP",0,0,0,1,null)));
        assertEquals(-1,game.getState().board()[0][0]);
    }

    @Test void fixedBoardCascadesAndClearsCoversWithoutCountingCellsTwice() {
        var game=game();
        int[][] board=new int[8][8];for(int r=0;r<8;r++)for(int c=0;c<8;c++)board[r][c]=(r*2+c)%6+1;
        board[7][0]=1;board[7][1]=2;board[7][2]=1;board[6][1]=1;
        assertTrue(MatchThreeGame.findMatches(board).isEmpty());
        int[][] covers=new int[8][8];covers[7][0]=covers[7][1]=covers[7][2]=1;
        ReflectionTestUtils.setField(game,"board",board);ReflectionTestUtils.setField(game,"covers",covers);
        ReflectionTestUtils.setField(game,"rng",new Random(42) {
            int calls;
            @Override public int nextInt(int bound) {return calls++<3?5:super.nextInt(bound);}
        });
        assertTrue(game.makeMove("a",new Move("SWAP",6,1,7,1,null)));
        var state=game.getState();
        assertTrue(state.events().stream().filter(e->e.type().equals("CLEAR")).count()>=2);
        assertEquals(state.score(),state.events().stream().mapToInt(MatchThreeGame.Event::points).sum());
        assertEquals(0,Arrays.stream(state.covers()).flatMapToInt(Arrays::stream).sum());
        assertTrue(MatchThreeGame.findMatches(state.board()).isEmpty());
    }

    @Test void blocksSplitGravitySegmentsAndFinalLevelFinishesRun() {
        var game=game();
        int[][] board=new int[8][8];for(int r=0;r<8;r++)for(int c=0;c<8;c++)board[r][c]=(r*2+c)%6+1;
        board[3][0]=-1;board[7][0]=0;
        ReflectionTestUtils.setField(game,"board",board);
        ReflectionTestUtils.invokeMethod(game,"collapse");
        assertEquals(-1,game.getState().board()[3][0]);
        for(int r=0;r<3;r++)assertEquals((r*2)%6+1,game.getState().board()[r][0]);
        game.configure(Map.of("match3Unlocked",10,"match3Level",10));
        ReflectionTestUtils.setField(game,"score",10000);ReflectionTestUtils.setField(game,"collected",100);
        ReflectionTestUtils.setField(game,"covers",new int[8][8]);
        assertTrue(game.makeMove("a",swapFor(game.getState().board())));
        assertTrue(game.isGameOver());assertEquals("a",game.getWinner());
        assertFalse(game.makeMove("a",new Move("RETRY",null,null,null,null,null)));
    }
}
