package com.brim.game.game;

import com.brim.game.game.chess.ChessGame;
import com.brim.game.game.chess.ChessGame.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChessBotTest {
    private ChessGame game(String difficulty) {
        ChessGame game = new ChessGame("test", difficulty);
        game.setPlayer1("white"); game.setPlayer2("black");
        return game;
    }
    private ChessGameState state(ChessGame g) { return (ChessGameState) g.getState(); }
    private ChessPiece piece(PieceType type, PieceColor color, int x, int y) {
        return new ChessPiece(type, color, x, y, false);
    }
    private void setup(ChessGame g, ChessPiece... pieces) {
        Map<String, ChessPiece> map = new HashMap<>();
        for (ChessPiece p : pieces) map.put(p.getX() + "," + p.getY(), p);
        g.getBoard().restore(map);
    }
    private boolean has(ChessGame g, int fx, int fy, int tx, int ty) {
        return state(g).getLegalMoves().stream().anyMatch(m -> m.getFromX() == fx && m.getFromY() == fy && m.getToX() == tx && m.getToY() == ty);
    }
    @Test void initialPositionHasTwentyMovesAndStateIsDetached() {
        ChessGame g = game("MEDIUM");
        ChessGameState before = state(g);
        assertEquals(20, before.getLegalMoves().size());
        assertTrue(g.makeMove("white", new ChessMove(4, 1, 4, 3)));
        assertEquals(1, before.getPieces().get("4,1").getY());
        assertTrue(before.getMoveHistory().isEmpty());
        assertEquals(20, state(g).getLegalMoves().size());
    }
    @Test void pinnedRookCannotExposeKing() {
        ChessGame g = game("HARD");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 4, 0), piece(PieceType.ROOK, PieceColor.WHITE, 4, 1),
                piece(PieceType.ROOK, PieceColor.BLACK, 4, 7), piece(PieceType.KING, PieceColor.BLACK, 0, 7));
        assertFalse(has(g, 4, 1, 3, 1));
        assertFalse(g.makeMove("white", new ChessMove(4, 1, 3, 1)));
        assertTrue(has(g, 4, 1, 4, 7));
    }
    @Test void foolsMateFinishesWithoutCapturingKingAndRejectsFurtherMoves() {
        ChessGame g = game("HARD");
        assertTrue(g.makeMove("white", new ChessMove(5, 1, 5, 2)));
        assertTrue(g.makeMove("black", new ChessMove(4, 6, 4, 4)));
        assertTrue(g.makeMove("white", new ChessMove(6, 1, 6, 3)));
        assertTrue(g.makeMove("black", new ChessMove(3, 7, 7, 3)));
        assertTrue(g.isGameOver());
        assertEquals("black", g.getWinner());
        assertTrue(state(g).isCheck());
        assertTrue(state(g).isCheckmate());
        assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(state(g)).path("checkmate").asBoolean());
        assertTrue(state(g).getLegalMoves().isEmpty());
        assertFalse(g.makeMove("white", new ChessMove(0, 1, 0, 2)));
        assertNull(g.generateBotMove());
        assertFalse(g.getBoard().isKingCaptured(PieceColor.WHITE));
    }
    @Test void stalemateIsADraw() {
        ChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 2, 5), piece(PieceType.QUEEN, PieceColor.WHITE, 1, 4),
                piece(PieceType.KING, PieceColor.BLACK, 0, 7));
        assertTrue(g.makeMove("white", new ChessMove(1, 4, 1, 5)));
        assertEquals(GameStatus.DRAW, g.getStatus());
        assertNull(g.getWinner());
        assertFalse(state(g).isCheck());
        assertFalse(state(g).isCheckmate());
    }
    @Test void checkMustBeAnsweredAndClearsAfterBlocking() {
        ChessGame g = game("MEDIUM");
        assertFalse(state(g).isCheck());
        assertFalse(state(g).isCheckmate());
        assertTrue(g.makeMove("white", new ChessMove(4, 1, 4, 3)));
        assertTrue(g.makeMove("black", new ChessMove(5, 6, 5, 4)));
        assertTrue(g.makeMove("white", new ChessMove(3, 0, 7, 4)));
        ChessGameState checked = state(g);
        assertTrue(checked.isCheck());
        assertFalse(checked.isCheckmate());
        assertEquals("black", checked.getCurrentPlayer());
        assertEquals(GameStatus.IN_PROGRESS, checked.getStatus());
        assertFalse(checked.getLegalMoves().isEmpty());
        assertFalse(g.makeMove("black", new ChessMove(0, 6, 0, 5)), "Cannot ignore check");
        assertTrue(g.makeMove("black", new ChessMove(6, 6, 6, 5)), "Pawn can block the queen's check");
        assertFalse(state(g).isCheck());
        assertFalse(state(g).isCheckmate());
        assertFalse(g.isGameOver());
    }
    @Test void castlesMoveRookAndKingAndRejectAttackedTransit() {
        ChessGame g = game("HARD");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 4, 0), piece(PieceType.ROOK, PieceColor.WHITE, 0, 0),
                piece(PieceType.ROOK, PieceColor.WHITE, 7, 0), piece(PieceType.KING, PieceColor.BLACK, 4, 7));
        assertTrue(has(g, 4, 0, 2, 0)); assertTrue(has(g, 4, 0, 6, 0));
        assertTrue(g.makeMove("white", new ChessMove(4, 0, 6, 0)));
        assertEquals(PieceType.ROOK, state(g).getPieces().get("5,0").getType());
        assertEquals(PieceType.KING, state(g).getPieces().get("6,0").getType());
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 4, 0), piece(PieceType.ROOK, PieceColor.WHITE, 7, 0),
                piece(PieceType.ROOK, PieceColor.BLACK, 5, 7), piece(PieceType.KING, PieceColor.BLACK, 0, 7));
        g.setCurrentPlayer("white");
        assertFalse(has(g, 4, 0, 6, 0));
    }
    @Test void enPassantRemovesPawnAndExpiresAfterOneTurn() {
        ChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 4, 0), piece(PieceType.KING, PieceColor.BLACK, 4, 7),
                piece(PieceType.PAWN, PieceColor.WHITE, 4, 4), piece(PieceType.PAWN, PieceColor.BLACK, 3, 6));
        g.setCurrentPlayer("black");
        assertTrue(g.makeMove("black", new ChessMove(3, 6, 3, 4)));
        assertTrue(has(g, 4, 4, 3, 5));
        assertTrue(g.makeMove("white", new ChessMove(4, 4, 3, 5)));
        assertFalse(state(g).getPieces().containsKey("3,4"));
        assertEquals(PieceType.PAWN, state(g).getPieces().get("3,5").getType());

        g = game("MEDIUM");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 4, 0), piece(PieceType.KING, PieceColor.BLACK, 4, 7),
                piece(PieceType.PAWN, PieceColor.WHITE, 4, 4), piece(PieceType.PAWN, PieceColor.BLACK, 3, 6));
        g.setCurrentPlayer("black");
        assertTrue(g.makeMove("black", new ChessMove(3, 6, 3, 4)));
        assertTrue(g.makeMove("white", new ChessMove(4, 0, 5, 0)));
        assertTrue(g.makeMove("black", new ChessMove(4, 7, 5, 7)));
        assertFalse(has(g, 4, 4, 3, 5));
    }
    @Test void enPassantCannotExposeKingToRook() {
        ChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 4, 4), piece(PieceType.KING, PieceColor.BLACK, 4, 7),
                piece(PieceType.PAWN, PieceColor.WHITE, 5, 4), piece(PieceType.PAWN, PieceColor.BLACK, 6, 6),
                piece(PieceType.ROOK, PieceColor.BLACK, 7, 4));
        g.setCurrentPlayer("black");
        assertTrue(g.makeMove("black", new ChessMove(6, 6, 6, 4)));
        assertFalse(has(g, 5, 4, 6, 5));
    }
    @Test void promotionSupportsDefaultQueenAndUnderpromotion() {
        for (PieceType promotion : new PieceType[]{null, PieceType.KNIGHT, PieceType.KING}) {
            ChessGame g = game("MEDIUM");
            setup(g, piece(PieceType.KING, PieceColor.WHITE, 4, 0), piece(PieceType.KING, PieceColor.BLACK, 7, 7),
                    piece(PieceType.PAWN, PieceColor.WHITE, 0, 6));
            ChessMove move = new ChessMove(0, 6, 0, 7); move.setPromotion(promotion);
            assertEquals(promotion != PieceType.KING, g.makeMove("white", move));
            if (promotion != PieceType.KING) assertEquals(promotion == null ? PieceType.QUEEN : promotion,
                    state(g).getPieces().get("0,7").getType());
        }
    }
    @Test void hardBotFindsMateAndPreservesTheLivePosition() {
        ChessGame g = game("HARD");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 5, 5), piece(PieceType.QUEEN, PieceColor.WHITE, 6, 5),
                piece(PieceType.KING, PieceColor.BLACK, 7, 7), piece(PieceType.ROOK, PieceColor.BLACK, 0, 3));
        Map<String, ChessPiece> before = g.getBoard().snapshot();
        ChessMove move = assertInstanceOf(ChessMove.class, g.generateBotMove());
        assertEquals(before, g.getBoard().snapshot());
        assertTrue(g.getMoveHistory().isEmpty());
        assertEquals("white", g.getCurrentPlayerId());
        assertTrue(g.makeMove("white", move));
        assertTrue(g.isGameOver(), "Expected mate, got " + move);
        assertEquals("white", g.getWinner());
    }
    @Test void mediumBotAvoidsPoisonedPawnThatLosesItsQueen() {
        ChessGame g = game("MEDIUM");
        setup(g, piece(PieceType.KING, PieceColor.WHITE, 6, 0), piece(PieceType.QUEEN, PieceColor.WHITE, 3, 0),
                piece(PieceType.KING, PieceColor.BLACK, 7, 7), piece(PieceType.ROOK, PieceColor.BLACK, 3, 7),
                piece(PieceType.PAWN, PieceColor.BLACK, 3, 4));
        ChessMove move = assertInstanceOf(ChessMove.class, g.generateBotMove());
        assertFalse(move.getFromX() == 3 && move.getFromY() == 0 && move.getToX() == 3 && move.getToY() == 4,
                "Bot must see the rook recapture after Qxd5");
        assertTrue(g.makeMove("white", move));
    }
    @Test void allDifficultiesProduceLegalMovesWithoutChangingTheBoard() {
        for (String difficulty : List.of("EASY", "MEDIUM", "HARD")) {
            ChessGame g = game(difficulty);
            Map<String, ChessPiece> before = g.getBoard().snapshot();
            Object move = g.generateBotMove();
            assertEquals(before, g.getBoard().snapshot());
            assertTrue(g.makeMove("white", move), difficulty);
        }
    }
}
