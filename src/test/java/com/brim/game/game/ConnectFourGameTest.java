package com.brim.game.game;

import com.brim.game.game.connectfour.ConnectFourGame;
import com.brim.game.game.connectfour.ConnectFourGame.Move;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConnectFourGameTest {
    private ConnectFourGame game(String difficulty) {
        var game = new ConnectFourGame("room", difficulty);
        game.setPlayer1("red");
        game.setPlayer2("yellow");
        return game;
    }
    private void play(ConnectFourGame game, int... columns) {
        for (int column : columns) assertTrue(game.makeMove(game.getCurrentPlayerId(), new Move(column)));
    }

    @Test void gravityTurnsAndInvalidInputs() {
        var game = game("MEDIUM");
        assertFalse(game.makeMove("yellow", new Move(0)));
        assertFalse(game.makeMove("outsider", new Move(0)));
        assertFalse(game.makeMove(null, new Move(0)));
        assertFalse(game.makeMove("red", new Move(null)));
        assertFalse(game.makeMove("red", new Move(-1)));
        assertFalse(game.makeMove("red", new Move(7)));
        assertFalse(game.makeMove("red", null));
        assertFalse(game.makeMove("red", "0"));
        assertEquals(0, game.getState().moveCount());
        play(game, 0, 0, 0, 0, 0, 0);
        assertEquals(2, game.getState().board()[0][0]);
        assertEquals(1, game.getState().board()[5][0]);
        assertFalse(game.getState().legalColumns().contains(0));
        assertFalse(game.makeMove("red", new Move(0)));
        assertEquals(6, game.getState().moveCount());
        assertEquals("red", game.getCurrentPlayerId());
    }

    @Test void winsInAllFourDirectionsAndRejectsMovesAfterWin() {
        int[][] sequences = {
                {0, 6, 1, 6, 2, 5, 3},
                {0, 1, 0, 1, 0, 1, 0},
                {0, 1, 1, 2, 4, 2, 2, 3, 4, 3, 5, 3, 3},
                {6, 5, 5, 4, 2, 4, 4, 3, 2, 3, 1, 3, 3}
        };
        for (int[] sequence : sequences) {
            var game = game("MEDIUM");
            play(game, sequence);
            assertTrue(game.isGameOver());
            assertEquals("FINISHED", game.getState().status());
            assertEquals("red", game.getWinner());
            assertEquals(4, game.getState().winningCells().size());
            assertTrue(game.getState().legalColumns().isEmpty());
            assertFalse(game.makeMove("yellow", new Move(6)));
        }
    }

    @Test void secondPlayerCanWin() {
        var game = game("MEDIUM");
        play(game, 0, 1, 0, 1, 2, 1, 2, 1);
        assertEquals("yellow", game.getWinner());
    }

    @Test void fullBoardWithoutFourIsDraw() {
        var game = game("MEDIUM");
        play(game, 5,2,4,3,2,3,0,6,4,5,2,0,4,0,1,6,6,1,3,6,6,4,3,5,0,2,2,5,6,2,3,0,0,4,5,1,4,1,5,3,1,1);
        assertTrue(game.isGameOver());
        assertEquals("DRAW", game.getState().status());
        assertNull(game.getWinner());
        assertEquals(42, game.getState().moveCount());
        assertTrue(game.getState().winningCells().isEmpty());
        assertNull(game.generateBotMove());
    }

    @Test void snapshotsAreDetached() {
        var game = game("MEDIUM");
        var before = game.getState();
        before.board()[5][0] = 2;
        assertEquals(0, game.getState().board()[5][0]);
        play(game, 0);
        assertEquals(0, before.moveCount());
        assertEquals(1, game.getState().board()[5][0]);
    }

    @ParameterizedTest @ValueSource(strings = {"EASY", "MEDIUM", "HARD"})
    void botTakesImmediateWinWithoutMutatingGame(String difficulty) {
        var game = game(difficulty);
        play(game, 0, 6, 1, 6, 2, 5);
        assertEquals(new Move(3), game.generateBotMove());
        assertEquals(6, game.getState().moveCount());
        assertEquals(0, game.getState().board()[5][3]);
        assertFalse(game.isGameOver());
    }

    @ParameterizedTest @ValueSource(strings = {"MEDIUM", "HARD"})
    void botBlocksImmediateLoss(String difficulty) {
        var game = game(difficulty);
        play(game, 6, 0, 6, 1, 5, 2);
        assertEquals(new Move(3), game.generateBotMove());
    }

    @Test void interruptedSearchKeepsBoardAndReturnsLegalFallback() {
        var game = game("HARD");
        play(game, 3, 3, 3, 3, 3, 3);
        Thread.currentThread().interrupt();
        try {
            assertTrue(game.getState().legalColumns().contains(game.generateBotMove().column()));
            assertEquals(6, game.getState().moveCount());
        } finally { Thread.interrupted(); }
    }
}
