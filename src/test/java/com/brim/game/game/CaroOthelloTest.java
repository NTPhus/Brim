package com.brim.game.game;

import com.brim.game.game.board.GridDuel;
import com.brim.game.game.board.GridDuel.Move;
import com.brim.game.game.caro.CaroGame;
import com.brim.game.game.othello.OthelloGame;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CaroOthelloTest {
    private <T extends GridDuel> T players(T game) {game.setPlayer1("a");game.setPlayer2("b");return game;}
    @Test void caroRejectsWrongTurnOccupiedOutsideAndNull() {
        var game=players(new CaroGame("r","EASY"));
        assertFalse(game.makeMove("b",new Move(7,7)));
        assertFalse(game.makeMove("a",new Move(null,0)));
        assertFalse(game.makeMove("a",new Move(-1,0)));
        assertFalse(game.makeMove("a",new Move(15,0)));
        assertTrue(game.makeMove("a",new Move(7,7)));
        assertFalse(game.makeMove("b",new Move(7,7)));
        assertEquals("b",game.getCurrentPlayerId());
        var state=game.getState();state.board()[7][7]=2;
        assertEquals(1,game.getState().board()[7][7]);
    }
    @Test void caroWinsFourDirectionsAtEdgesAndBlocksFurtherMoves() {
        for (int[] direction:new int[][]{{1,0},{0,1},{1,1},{1,-1}}) {
            var game=players(new CaroGame("r","EASY"));
            for (int i=0;i<5;i++) {
                int r=direction[0]*i,c=direction[1]<0?14-i:direction[1]*i;
                assertTrue(game.makeMove("a",new Move(r,c)));
                if (i<4) assertTrue(game.makeMove("b",new Move(14,i*2)));
            }
            assertEquals("a",game.getWinner());assertEquals(5,game.getState().winningCells().size());
            assertFalse(game.makeMove("b",new Move(8,8)));
        }
    }
    @Test void freestyleOverlineWinsEvenBlockedBothEndsAndBotFindsWin() {
        var game=players(new CaroGame("r","EASY"));
        int[][] board=new int[15][15];
        for (int c:new int[]{1,2,3,5,6}) board[7][c]=1;
        board[7][0]=board[7][7]=2;
        ReflectionTestUtils.setField(game,"board",board);
        assertEquals(new Move(7,4),game.generateBotMove());
        assertEquals(0,game.getState().board()[7][4]);
        assertTrue(game.makeMove("a",new Move(7,4)));
        assertEquals(6,game.getState().winningCells().size());
    }
    @Test void othelloLegalOpeningFlipsAndRejectsNonCapturingMove() {
        var game=players(new OthelloGame("r","EASY"));
        assertEquals(Set.of(new Move(2,3),new Move(3,2),new Move(4,5),new Move(5,4)),new HashSet<>(game.getState().legalMoves()));
        assertFalse(game.makeMove("a",new Move(0,0)));
        assertTrue(game.makeMove("a",new Move(2,3)));
        assertEquals(1,game.getState().board()[3][3]);
        assertEquals(4,game.getState().score1());assertEquals(1,game.getState().score2());
        assertTrue(game.getState().legalMoves().contains(game.generateBotMove()));
    }
    @Test void othelloAutomaticallyPassesAndEndsWhenBothHaveNoMove() {
        var game=players(new OthelloGame("r","EASY"));
        int[][] board=new int[8][8];for (int[] row:board) Arrays.fill(row,1);
        board[0][1]=board[7][6]=2;board[0][2]=board[7][7]=0;
        ReflectionTestUtils.setField(game,"board",board);
        assertTrue(game.makeMove("a",new Move(0,2)));
        assertEquals("b",game.getState().passedPlayer());assertEquals("a",game.getCurrentPlayerId());
        assertTrue(game.makeMove("a",new Move(7,7)));
        assertTrue(game.isGameOver());assertEquals("a",game.getWinner());assertEquals(64,game.getState().score1());
    }
    @Test void othelloEqualFinalCountsDraw() {
        var game=players(new OthelloGame("r","EASY"));
        int[][] board=new int[8][8];for (int[] row:board) Arrays.fill(row,2);
        board[0][0]=0;for(int i=2;i<32;i++)board[i/8][i%8]=1;
        ReflectionTestUtils.setField(game,"board",board);
        assertTrue(game.makeMove("a",new Move(0,0)));
        assertEquals("DRAW",game.getState().status());assertNull(game.getWinner());
        assertEquals(32,game.getState().score1());assertEquals(32,game.getState().score2());
    }
}
