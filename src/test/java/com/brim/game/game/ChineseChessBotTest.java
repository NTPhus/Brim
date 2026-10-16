package com.brim.game.game;

import com.brim.game.game.chinesechess.ChineseChessGame;
import com.brim.game.game.chinesechess.ChineseChessGame.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChineseChessBotTest {
    private ChineseChessGame game(String difficulty) {
        ChineseChessGame g = new ChineseChessGame("test", difficulty);
        g.setPlayer1("red"); g.setPlayer2("black"); return g;
    }
    private ChineseChessGameState state(ChineseChessGame g) { return (ChineseChessGameState) g.getState(); }
    private ChineseChessPiece piece(PieceType type, PieceColor color, int x, int y) {
        return new ChineseChessPiece(type, color, x, y);
    }
    private void setup(ChineseChessGame g, ChineseChessPiece... pieces) {
        Map<String, ChineseChessPiece> map = new HashMap<>();
        for (ChineseChessPiece p : pieces) map.put(p.getX() + "," + p.getY(), p);
        g.getBoard().restore(map);
    }
    private boolean has(ChineseChessGame g, int fx, int fy, int tx, int ty) {
        return state(g).getLegalMoves().stream().anyMatch(m -> m.getFromX() == fx && m.getFromY() == fy && m.getToX() == tx && m.getToY() == ty);
    }
    @Test void openingHasFortyFourMovesAndReadingStatePreservesPosition() {
        ChineseChessGame g = game("MEDIUM");
        Map<String, ChineseChessPiece> before = g.getBoard().snapshot();
        assertEquals(44, state(g).getLegalMoves().size());
        assertEquals(before, g.getBoard().snapshot());
        ChineseChessGameState old = state(g);
        assertTrue(g.makeMove("red", new ChineseChessMove(0, 6, 0, 5)));
        assertEquals(6, old.getPieces().get("0,6").getY());
        assertTrue(old.getMoveHistory().isEmpty());
    }
    @Test void blockerCannotExposeFlyingGenerals() {
        ChineseChessGame g = game("HARD");
        setup(g, piece(PieceType.GENERAL, PieceColor.RED, 4, 9), piece(PieceType.GENERAL, PieceColor.BLACK, 4, 0),
                piece(PieceType.CHARIOT, PieceColor.RED, 4, 5));
        assertFalse(has(g, 4, 5, 3, 5));
        assertTrue(has(g, 4, 5, 4, 4));
        assertFalse(g.makeMove("red", new ChineseChessMove(4, 5, 3, 5)));
        assertFalse(has(g, 4, 5, 4, 0));
    }
    @Test void cannonCheckRequiresEvasionOrASecondScreen() {
        ChineseChessGame g = game("HARD");
        setup(g, piece(PieceType.GENERAL, PieceColor.RED, 4, 9), piece(PieceType.GENERAL, PieceColor.BLACK, 3, 0),
                piece(PieceType.CANNON, PieceColor.BLACK, 4, 2), piece(PieceType.SOLDIER, PieceColor.RED, 4, 6),
                piece(PieceType.CHARIOT, PieceColor.RED, 0, 8));
        assertTrue(state(g).isCheck());
        assertFalse(state(g).isCheckmate());
        assertFalse(has(g, 0, 8, 0, 7));
        assertTrue(has(g, 0, 8, 4, 8)); // Adding a second screen breaks the cannon attack.
        assertTrue(has(g, 4, 9, 5, 9));
        assertFalse(g.makeMove("red", new ChineseChessMove(0, 8, 0, 7)), "Cannot ignore cannon check");
        assertTrue(g.makeMove("red", new ChineseChessMove(0, 8, 4, 8)));
        assertFalse(state(g).isCheck());
        assertFalse(state(g).isCheckmate());
        assertFalse(g.isGameOver());
    }
    @Test void blockedHorseDoesNotGiveCheck() {
        ChineseChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.GENERAL, PieceColor.RED, 4, 9), piece(PieceType.GENERAL, PieceColor.BLACK, 3, 0),
                piece(PieceType.HORSE, PieceColor.BLACK, 3, 7), piece(PieceType.SOLDIER, PieceColor.RED, 3, 8));
        assertFalse(state(g).isCheck());
    }
    @Test void noLegalMovesIsALossEvenWithoutCheck() {
        ChineseChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.GENERAL, PieceColor.RED, 4, 9), piece(PieceType.GENERAL, PieceColor.BLACK, 4, 0),
                piece(PieceType.SOLDIER, PieceColor.RED, 4, 5),
                piece(PieceType.CHARIOT, PieceColor.RED, 3, 2), piece(PieceType.CHARIOT, PieceColor.RED, 5, 3));
        assertTrue(g.makeMove("red", new ChineseChessMove(5, 3, 5, 1)));
        assertFalse(state(g).isCheck());
        assertEquals(GameStatus.FINISHED, g.getStatus());
        assertFalse(state(g).isCheckmate(), "No legal moves without check is a loss, not checkmate");
        assertEquals("red", g.getWinner());
        assertTrue(state(g).getLegalMoves().isEmpty());
    }
    @Test void botFindsMateWithoutCapturingGeneral() {
        ChineseChessGame g = game("HARD");
        setup(g, piece(PieceType.GENERAL, PieceColor.RED, 4, 9), piece(PieceType.GENERAL, PieceColor.BLACK, 4, 0),
                piece(PieceType.SOLDIER, PieceColor.RED, 4, 5),
                piece(PieceType.CHARIOT, PieceColor.RED, 0, 2), piece(PieceType.CHARIOT, PieceColor.RED, 8, 1));
        Map<String, ChineseChessPiece> before = g.getBoard().snapshot();
        Object move = g.generateBotMove();
        assertEquals(before, g.getBoard().snapshot());
        assertTrue(g.makeMove("red", move));
        assertTrue(g.isGameOver(), "Expected forced win, got " + move);
        assertEquals("red", g.getWinner());
        assertFalse(g.getBoard().isGeneralCaptured(PieceColor.BLACK));
        assertFalse(g.makeMove("black", new ChineseChessMove(4, 0, 3, 0)));
    }
    @Test void checkmateKeepsGeneralOnBoardAndRejectsFurtherMoves() {
        ChineseChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.GENERAL, PieceColor.RED, 4, 9), piece(PieceType.GENERAL, PieceColor.BLACK, 3, 0),
                piece(PieceType.CHARIOT, PieceColor.RED, 4, 1), piece(PieceType.CHARIOT, PieceColor.RED, 0, 2));
        assertFalse(state(g).isCheck());
        assertTrue(g.makeMove("red", new ChineseChessMove(0, 2, 3, 2)));
        ChineseChessGameState result = state(g);
        assertTrue(result.isCheck());
        assertTrue(result.isCheckmate());
        assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result).path("checkmate").asBoolean());
        assertEquals(GameStatus.FINISHED, result.getStatus());
        assertEquals("red", result.getWinner());
        assertEquals("black", result.getCurrentPlayer());
        assertTrue(result.getLegalMoves().isEmpty());
        assertFalse(g.getBoard().isGeneralCaptured(PieceColor.BLACK));
        assertFalse(g.makeMove("black", new ChineseChessMove(3, 0, 4, 0)));
        assertNull(g.generateBotMove());
    }
    @Test void mediumBotAvoidsLosingChariotForSoldier() {
        ChineseChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.GENERAL, PieceColor.RED, 4, 9), piece(PieceType.GENERAL, PieceColor.BLACK, 4, 0),
                piece(PieceType.SOLDIER, PieceColor.RED, 4, 5), piece(PieceType.CHARIOT, PieceColor.RED, 0, 9),
                piece(PieceType.SOLDIER, PieceColor.BLACK, 0, 5), piece(PieceType.CHARIOT, PieceColor.BLACK, 0, 0));
        ChineseChessMove move = assertInstanceOf(ChineseChessMove.class, g.generateBotMove());
        assertFalse(move.getFromX() == 0 && move.getFromY() == 9 && move.getToX() == 0 && move.getToY() == 5);
        assertTrue(g.makeMove("red", move));
    }
    @Test void allDifficultiesReturnLegalMovesAndPreserveLiveState() {
        for (String difficulty : List.of("EASY", "MEDIUM", "HARD")) {
            ChineseChessGame g = game(difficulty);
            Map<String, ChineseChessPiece> before = g.getBoard().snapshot();
            Object move = g.generateBotMove();
            assertEquals(before, g.getBoard().snapshot());
            assertTrue(g.getMoveHistory().isEmpty());
            assertEquals("red", g.getCurrentPlayerId());
            assertTrue(g.makeMove("red", move), difficulty);
        }
    }
}
