package com.brim.game.game.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlphaBetaSearchTest {
    /** Greedy gives +50 immediately but loses 500 to the reply; safe keeps +20. */
    private static class Tree implements AlphaBetaSearch.Position<String> {
        private String node = "root";
        private int side = 1;
        private final Deque<String> history = new ArrayDeque<>();
        private boolean captures;
        private boolean checked;
        private boolean interruptEvaluation;
        public List<String> legalMoves() {
            return new ArrayList<>(switch (node) {
                case "root" -> List.of("greedy", "safe");
                case "greedy" -> List.of("refutation");
                case "safe" -> List.of("defense");
                default -> List.of("wait");
            });
        }
        public void play(String move) { history.push(node); if (!move.equals("wait")) node = move; side = -side; }
        public void undo() { node = history.pop(); side = -side; }
        public int evaluate() {
            if (interruptEvaluation) Thread.currentThread().interrupt();
            return side * switch (node) {
                case "greedy" -> 50;
                case "safe" -> 10;
                case "refutation" -> -500;
                case "defense" -> 20;
                default -> 0;
            };
        }
        public boolean inCheck() { return checked && node.equals("greedy"); }
        public boolean stalemateIsLoss() { return false; }
        public boolean tactical(String move) { return captures && move.equals("refutation"); }
        public int movePriority(String move) { return move.equals("greedy") ? 100 : 0; }
        public String key() { return node + side; }
    }
    @Test void deeperSearchSeesOpponentsBestReply() {
        Tree tree = new Tree();
        assertEquals("greedy", new AlphaBetaSearch<>(tree).findBestMove(1, 1000));
        assertEquals("safe", new AlphaBetaSearch<>(tree).findBestMove(2, 1000));
        assertEquals("root", tree.node);
        assertTrue(tree.history.isEmpty());
    }
    @Test void quiescenceSeesRecaptureBeyondNominalDepth() {
        Tree tree = new Tree(); tree.captures = true;
        assertEquals("safe", new AlphaBetaSearch<>(tree).findBestMove(1, 1000));
        assertTrue(tree.history.isEmpty());
    }
    @Test void checkEvasionsAreSearchedEvenIfTheyAreNotCaptures() {
        Tree tree = new Tree(); tree.checked = true;
        assertEquals("safe", new AlphaBetaSearch<>(tree).findBestMove(1, 1000));
    }
    @Test void deadlineReturnsLegalFallback() {
        Tree tree = new Tree();
        assertTrue(tree.legalMoves().contains(new AlphaBetaSearch<>(tree).findBestMove(6, 0)));
        assertEquals("root", tree.node);
        assertTrue(tree.history.isEmpty());
    }
    @Test void interruptedSearchAlwaysUndoesMoves() {
        Tree tree = new Tree(); tree.interruptEvaluation = true;
        try {
            assertNotNull(new AlphaBetaSearch<>(tree).findBestMove(6, 1000));
            assertEquals("root", tree.node);
            assertEquals(1, tree.side);
            assertTrue(tree.history.isEmpty());
        } finally { Thread.interrupted(); }
    }
}
