package com.brim.game.game.board;

import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import java.util.*;

/** Shared public-board snapshots and bounded bot search for Caro and Othello. */
public abstract class GridDuel implements TurnBasedGame {
    public record Move(Integer row, Integer column) {}
    public record Cell(int row, int column) {}
    public record State(int[][] board, String player1, String player2, String currentPlayer,
                        String status, String winner, List<Move> legalMoves, List<Cell> winningCells,
                        Move lastMove, String passedPlayer, int score1, int score2) {}
    protected final int size;
    protected final String difficulty;
    protected int[][] board;
    protected String player1, player2, winner, passedPlayer;
    protected int side = 1;
    protected String status = "IN_PROGRESS";
    protected Move lastMove;
    protected List<Cell> winningCells = List.of();

    protected GridDuel(int size, String difficulty) {
        this.size = size;
        this.difficulty = difficulty == null ? "MEDIUM" : difficulty;
        board = new int[size][size];
    }
    protected abstract GridDuel fresh();
    protected abstract List<Move> moves(int side);
    protected abstract void apply(Move move);
    protected abstract int evaluate(int side);
    protected List<Move> candidates() { return moves(side); }
    protected boolean inside(int r, int c) { return r >= 0 && c >= 0 && r < size && c < size; }
    protected String player(int side) { return side == 1 ? player1 : player2; }
    protected int count(int value) { return (int) Arrays.stream(board).flatMapToInt(Arrays::stream).filter(v -> v == value).count(); }
    @Override public Class<?> moveType() { return Move.class; }
    @Override public synchronized void setPlayer1(String id) { player1 = id; }
    @Override public synchronized void setPlayer2(String id) { player2 = id; }
    @Override public synchronized String getCurrentPlayerId() { return player(side); }
    @Override public synchronized boolean isGameOver() { return !status.equals("IN_PROGRESS"); }
    @Override public synchronized String getWinner() { return winner; }
    @Override public void handleEvent(MessageType type, Object payload) {}
    @Override public synchronized boolean makeMove(String id, Object input) {
        if (isGameOver() || player1 == null || player2 == null || id == null || !id.equals(player(side))
                || !(input instanceof Move move) || move.row == null || move.column == null
                || !inside(move.row, move.column) || !moves(side).contains(move)) return false;
        passedPlayer = null;
        apply(move);
        lastMove = move;
        return true;
    }
    @Override public synchronized State getState() {
        return new State(copyBoard(), player1, player2, player(side), status, winner,
                isGameOver() ? List.of() : List.copyOf(moves(side)), winningCells,
                lastMove, passedPlayer, count(1), count(2));
    }
    protected int[][] copyBoard() { return Arrays.stream(board).map(int[]::clone).toArray(int[][]::new); }
    protected GridDuel fork() {
        GridDuel copy = fresh();
        copy.board = copyBoard(); copy.player1 = player1; copy.player2 = player2;
        copy.side = side; copy.status = status; copy.winner = winner;
        return copy;
    }
    @Override public Move generateBotMove() {
        GridDuel root;
        synchronized (this) { if (isGameOver()) return null; root = fork(); }
        List<Move> choices = root.candidates();
        if (choices.isEmpty()) return null;
        Move best = choices.get(0);
        long deadline = System.nanoTime() + ("HARD".equals(difficulty) ? 1500 : "EASY".equals(difficulty) ? 100 : 450) * 1_000_000L;
        int depthLimit = "HARD".equals(difficulty) ? 4 : "EASY".equals(difficulty) ? 1 : 3;
        for (int depth = 1; depth <= depthLimit; depth++) {
            int alpha = -2_000_000;
            Move candidate = best;
            try {
                for (Move move : choices) {
                    check(deadline);
                    GridDuel child = root.fork(); child.apply(move);
                    int value = search(child, root.side, depth - 1, alpha, 2_000_000, deadline);
                    if (value > alpha) { alpha = value; candidate = move; }
                }
                best = candidate;
            } catch (SearchTimeout timeout) { break; }
        }
        return best;
    }
    private static int search(GridDuel game, int rootSide, int depth, int alpha, int beta, long deadline) {
        check(deadline);
        if (game.isGameOver()) return game.winner == null ? 0 : game.winner.equals(game.player(rootSide)) ? 1_000_000 + depth : -1_000_000 - depth;
        if (depth == 0) return game.evaluate(rootSide);
        boolean maximize = game.side == rootSide;
        int value = maximize ? -2_000_000 : 2_000_000;
        for (Move move : game.candidates()) {
            GridDuel child = game.fork(); child.apply(move);
            int score = search(child, rootSide, depth - 1, alpha, beta, deadline);
            value = maximize ? Math.max(value, score) : Math.min(value, score);
            if (maximize) alpha = Math.max(alpha, value); else beta = Math.min(beta, value);
            if (alpha >= beta) break;
        }
        return value;
    }
    private static void check(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new SearchTimeout();
    }
    private static final class SearchTimeout extends RuntimeException {
        private SearchTimeout() { super(null, null, false, false); }
    }
}
