package com.brim.game.game.connectfour;

import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import java.util.*;

/** Server-owned 7-column, 6-row board. Row zero is the top. */
public final class ConnectFourGame implements TurnBasedGame {
    private static final int ROWS = 6, COLUMNS = 7;
    private static final int[] ORDER = {3, 2, 4, 1, 5, 0, 6};
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
    private final String roomId;
    private final String difficulty;
    private final int[][] board = new int[ROWS][COLUMNS];
    private String player1, player2, winner;
    private int turn = 1, moveCount;
    private String status = "IN_PROGRESS";
    private Cell lastMove;
    private List<Cell> winningCells = List.of();

    public ConnectFourGame(String roomId, String difficulty) {
        this.roomId = roomId;
        this.difficulty = difficulty == null ? "MEDIUM" : difficulty.toUpperCase(Locale.ROOT);
    }

    public record Move(Integer column) {}
    public record Cell(int row, int column) {}
    public record State(String roomId, int[][] board, String player1, String player2,
                        String currentPlayer, String status, String winner, int moveCount,
                        Cell lastMove, List<Cell> winningCells, List<Integer> legalColumns) {}

    @Override public Class<?> moveType() { return Move.class; }
    @Override public synchronized void setPlayer1(String id) { player1 = id; }
    @Override public synchronized void setPlayer2(String id) { player2 = id; }
    @Override public synchronized String getCurrentPlayerId() { return turn == 1 ? player1 : player2; }
    @Override public synchronized boolean isGameOver() { return !"IN_PROGRESS".equals(status); }
    @Override public synchronized String getWinner() { return winner; }
    @Override public void handleEvent(MessageType type, Object payload) {}

    @Override public synchronized boolean makeMove(String playerId, Object raw) {
        if (isGameOver() || player1 == null || player2 == null || playerId == null
                || !playerId.equals(getCurrentPlayerId()) || !(raw instanceof Move move)
                || move.column() == null || move.column() < 0 || move.column() >= COLUMNS) return false;
        int column = move.column(), row = landingRow(board, column);
        if (row < 0) return false;
        board[row][column] = turn;
        lastMove = new Cell(row, column);
        moveCount++;
        winningCells = winningLine(board, row, column);
        if (!winningCells.isEmpty()) {
            winner = playerId;
            status = "FINISHED";
        } else if (moveCount == ROWS * COLUMNS) {
            status = "DRAW";
        } else turn = 3 - turn;
        return true;
    }

    @Override public synchronized State getState() {
        return new State(roomId, copy(board), player1, player2, getCurrentPlayerId(), status,
                winner, moveCount, lastMove, winningCells,
                isGameOver() ? List.of() : legalColumns(board));
    }

    @Override public Move generateBotMove() {
        int[][] analysis;
        int side, remaining;
        synchronized (this) {
            if (isGameOver() || player1 == null || player2 == null) return null;
            analysis = copy(board);
            side = turn;
            remaining = ROWS * COLUMNS - moveCount;
        }
        int maxDepth = "HARD".equals(difficulty) ? 6 : "EASY".equals(difficulty) ? 1 : 3;
        long budgetMs = "HARD".equals(difficulty) ? 1500 : "EASY".equals(difficulty) ? 100 : 450;
        Search search = new Search(System.nanoTime() + budgetMs * 1_000_000L);
        List<Integer> legal = legalColumns(analysis);
        int best = legal.get(0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            int iterationBest = best, alpha = -2_000_000;
            try {
                for (int column : legal) {
                    search.checkTime();
                    int row = landingRow(analysis, column);
                    analysis[row][column] = side;
                    int score;
                    try {
                        score = !winningLine(analysis, row, column).isEmpty() ? 1_000_000
                                : -search.score(analysis, 3 - side, depth - 1, remaining - 1, -2_000_000, -alpha, 1);
                    } finally { analysis[row][column] = 0; }
                    if (score > alpha) { alpha = score; iterationBest = column; }
                }
                best = iterationBest;
            } catch (SearchTimeout timeout) { break; }
        }
        return new Move(best);
    }

    private static final class SearchTimeout extends RuntimeException {
        private SearchTimeout() { super(null, null, false, false); }
    }

    private static final class Search {
        private final long deadline;
        Search(long deadline) { this.deadline = deadline; }
        void checkTime() {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new SearchTimeout();
        }
        int score(int[][] cells, int side, int depth, int remaining, int alpha, int beta, int ply) {
            checkTime();
            if (remaining == 0) return 0;
            if (depth == 0) return evaluate(cells, side);
            for (int column : legalColumns(cells)) {
                int row = landingRow(cells, column);
                cells[row][column] = side;
                int value;
                try {
                    value = !winningLine(cells, row, column).isEmpty() ? 1_000_000 - ply
                            : -score(cells, 3 - side, depth - 1, remaining - 1, -beta, -alpha, ply + 1);
                } finally { cells[row][column] = 0; }
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
            return alpha;
        }
    }

    private static int evaluate(int[][] cells, int side) {
        int value = 0;
        int[] weights = {0, 1, 12, 120, 10000};
        for (int row = 0; row < ROWS; row++) for (int column = 0; column < COLUMNS; column++) {
            if (column == 3) value += cells[row][column] == side ? 5 : cells[row][column] == 0 ? 0 : -5;
            for (int[] direction : DIRECTIONS) {
                if (!inside(row + 3 * direction[0], column + 3 * direction[1])) continue;
                int own = 0, opponent = 0;
                for (int step = 0; step < 4; step++) {
                    int cell = cells[row + step * direction[0]][column + step * direction[1]];
                    if (cell == side) own++;
                    else if (cell != 0) opponent++;
                }
                if (opponent == 0) value += weights[own];
                if (own == 0) value -= weights[opponent];
            }
        }
        return value;
    }

    private static List<Cell> winningLine(int[][] cells, int row, int column) {
        int side = cells[row][column];
        for (int[] direction : DIRECTIONS) {
            List<Cell> line = new ArrayList<>();
            line.add(new Cell(row, column));
            for (int sign : new int[]{-1, 1}) {
                int r = row + sign * direction[0], c = column + sign * direction[1];
                while (inside(r, c) && cells[r][c] == side) {
                    line.add(new Cell(r, c));
                    r += sign * direction[0]; c += sign * direction[1];
                }
            }
            if (line.size() >= 4) return List.copyOf(line);
        }
        return List.of();
    }
    private static boolean inside(int row, int column) { return row >= 0 && row < ROWS && column >= 0 && column < COLUMNS; }
    private static int landingRow(int[][] cells, int column) {
        for (int row = ROWS - 1; row >= 0; row--) if (cells[row][column] == 0) return row;
        return -1;
    }
    private static List<Integer> legalColumns(int[][] cells) {
        List<Integer> legal = new ArrayList<>();
        for (int column : ORDER) if (cells[0][column] == 0) legal.add(column);
        return legal;
    }
    private static int[][] copy(int[][] cells) { return Arrays.stream(cells).map(int[]::clone).toArray(int[][]::new); }
}
