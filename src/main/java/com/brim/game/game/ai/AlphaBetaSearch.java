package com.brim.game.game.ai;

import java.util.*;

/** Time-bounded, iterative-deepening negamax shared by both board games. */
public final class AlphaBetaSearch<M> {
    public interface Position<M> {
        List<M> legalMoves();
        void play(M move);
        void undo();
        int evaluate(); // From the side-to-move's perspective.
        boolean inCheck();
        boolean stalemateIsLoss();
        boolean tactical(M move);
        int movePriority(M move);
        String key(); // Includes side to move and special-move rights.
    }

    private static final int MATE = 1_000_000;
    private static final int INF = 2_000_000;
    private static final int QUIESCENCE_DEPTH = 6;
    private final Position<M> position;
    private final Map<String, Entry<M>> table = new HashMap<>();
    private long deadline;
    private record Entry<M>(int depth, int score, int bound, M move) {}
    private static final class Timeout extends RuntimeException {
        private Timeout() { super(null, null, false, false); }
    }

    public AlphaBetaSearch(Position<M> position) { this.position = position; }

    public M findBestMove(String difficulty) {
        int depth = "HARD".equals(difficulty) ? 6 : "EASY".equals(difficulty) ? 1 : 3;
        long milliseconds = "HARD".equals(difficulty) ? 1500 : "EASY".equals(difficulty) ? 100 : 450;
        return findBestMove(depth, milliseconds);
    }

    public M findBestMove(int maxDepth, long milliseconds) {
        deadline = System.nanoTime() + milliseconds * 1_000_000L;
        table.clear();
        List<M> moves = position.legalMoves();
        if (moves.isEmpty()) return null;
        order(moves, null);
        M best = moves.get(0); // Legal fallback even if the first iteration times out.
        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                order(moves, best);
                M iterationBest = best;
                int alpha = -INF;
                for (M move : moves) {
                    checkTime();
                    position.play(move);
                    int score;
                    try { score = -search(depth - 1, -INF, -alpha, 1, QUIESCENCE_DEPTH); }
                    finally { position.undo(); }
                    if (score > alpha) {
                        alpha = score;
                        iterationBest = move;
                    }
                }
                best = iterationBest; // Never publish a partially searched iteration.
                if (alpha >= MATE - 100) break;
            } catch (Timeout timeout) {
                break;
            }
        }
        return best;
    }

    private int search(int depth, int alpha, int beta, int ply, int quietLeft) {
        checkTime();
        String key = position.key();
        int originalAlpha = alpha;
        Entry<M> cached = depth > 0 ? table.get(key) : null;
        if (cached != null && cached.depth >= depth) {
            if (cached.bound == 0) return cached.score;
            if (cached.bound == 1 && cached.score >= beta) return cached.score;
            if (cached.bound == -1 && cached.score <= alpha) return cached.score;
        }
        List<M> moves = position.legalMoves();
        boolean check = position.inCheck();
        if (moves.isEmpty()) return check || position.stalemateIsLoss() ? -MATE + ply : 0;
        if (depth <= 0) {
            // Never stand pat in check: search evasions, with a finite emergency cap.
            if (quietLeft <= 0) return position.evaluate();
            if (!check) {
                int standPat = position.evaluate();
                if (standPat >= beta) return standPat;
                alpha = Math.max(alpha, standPat);
                moves.removeIf(move -> !position.tactical(move));
            }
        }
        order(moves, cached == null ? null : cached.move);
        M best = null;
        int scoreBest = depth <= 0 && !check ? alpha : -INF;
        for (M move : moves) {
            checkTime();
            position.play(move);
            int score;
            try { score = -search(depth - 1, -beta, -alpha, ply + 1, quietLeft - (depth <= 0 ? 1 : 0)); }
            finally { position.undo(); }
            if (score > scoreBest) { scoreBest = score; best = move; }
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }
        // Mate scores depend on ply; don't cache them without normalization.
        if (depth > 0 && Math.abs(scoreBest) < MATE - 100 && table.size() < 50_000) {
            int bound = scoreBest <= originalAlpha ? -1 : scoreBest >= beta ? 1 : 0;
            table.put(key, new Entry<>(depth, scoreBest, bound, best));
        }
        return scoreBest;
    }

    private void order(List<M> moves, M preferred) {
        moves.sort(Comparator.<M>comparingInt(move -> Objects.equals(move, preferred)
                ? Integer.MAX_VALUE : position.movePriority(move)).reversed());
    }

    private void checkTime() {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new Timeout();
    }
}
