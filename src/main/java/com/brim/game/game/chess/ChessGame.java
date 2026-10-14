package com.brim.game.game.chess;

import com.brim.game.service.TurnBasedGameService;
import lombok.Data;

import java.util.*;

@Data
public class ChessGame implements TurnBasedGameService.TurnBasedGame {
    private final String roomId;
    private final String difficulty;
    private String player1;
    private String player2;
    private String currentPlayer;
    private final ChessBoard board = new ChessBoard();
    private GameStatus status = GameStatus.IN_PROGRESS;
    private String winner;
    private final List<String> moveHistory = new ArrayList<>();

    public ChessGame(String roomId) { this(roomId, "MEDIUM"); }

    public ChessGame(String roomId, String difficulty) {
        this.roomId = roomId;
        this.difficulty = difficulty == null ? "MEDIUM" : difficulty.toUpperCase();
    }

    @Override
    public boolean makeMove(String playerId, Object move) {
        if (!playerId.equals(currentPlayer)) return false;
        if (!(move instanceof ChessMove chessMove)) return false;

        PieceColor color = currentPlayer.equals(player1) ? PieceColor.WHITE : PieceColor.BLACK;
        ChessPiece piece = board.getPieces().get(chessMove.fromX + "," + chessMove.fromY);
        if (!board.isValidMove(chessMove, color) || piece == null ||
                generateLegalMovesForPiece(piece, color).stream().noneMatch(candidate -> sameMove(candidate, chessMove))) {
            return false;
        }

        board.executeMove(chessMove);
        moveHistory.add(chessMove.toString());
        switchPlayer();
        checkGameEnd();
        return true;
    }

    @Override
    public Object getState() {
        PieceColor currentColor = Objects.equals(currentPlayer, player1) ? PieceColor.WHITE : PieceColor.BLACK;
        List<ChessMove> legalMoves = board.getPieces().values().stream()
                .filter(piece -> piece.getColor() == currentColor)
                .flatMap(piece -> generateLegalMovesForPiece(piece, currentColor).stream())
                .toList();
        return new ChessGameState(board.getPieces(), currentPlayer, status, moveHistory, player1, player2, winner, legalMoves);
    }

    @Override
    public String getCurrentPlayerId() {
        return currentPlayer;
    }

    @Override
    public boolean isGameOver() {
        return status != GameStatus.IN_PROGRESS;
    }

    @Override
    public String getWinner() {
        return winner;
    }

    @Override
    public void setPlayer1(String playerId) {
        this.player1 = playerId;
        this.currentPlayer = playerId;
    }

    @Override
    public void setPlayer2(String playerId) {
        this.player2 = playerId;
    }

    @Override
    public void handleEvent(com.brim.game.model.dto.MessageType type, Object payload) {
        // Handle draw offer, resignation, etc.
    }

    @Override
    public Object generateBotMove() {
        PieceColor botColor = currentPlayer.equals(player1) ? PieceColor.WHITE : PieceColor.BLACK;
        List<ChessPiece> myPieces = board.getPieces().values().stream()
                .filter(p -> p.getColor() == botColor)
                .toList();
        if (myPieces.isEmpty()) return null;

        record ScoredMove(ChessMove move, int score) {}
        List<ScoredMove> legalMoves = new ArrayList<>();

        for (ChessPiece p : myPieces) {
            List<ChessMove> movesForPiece = generateLegalMovesForPiece(p, botColor);
            for (ChessMove m : movesForPiece) {
                int score = evaluateMove(m, p, botColor);
                legalMoves.add(new ScoredMove(m, score));
            }
        }

        if (legalMoves.isEmpty()) return null;

        if ("EASY".equals(difficulty)) {
            return legalMoves.get(new Random().nextInt(legalMoves.size())).move();
        }

        // Sort by score descending (best move first)
        legalMoves.sort((a, b) -> Integer.compare(b.score, a.score));

        if ("HARD".equals(difficulty)) {
            return legalMoves.stream()
                    .max(Comparator.comparingInt(scored -> hardMoveScore(scored.move(), botColor)))
                    .orElseThrow().move();
        }

        // Medium: choose among near-best moves for some variety.
        Random rand = new Random();
        int bestScore = legalMoves.get(0).score;
        List<ChessMove> topMoves = legalMoves.stream()
                .filter(sm -> sm.score >= bestScore - 5)
                .map(ScoredMove::move)
                .toList();

        return topMoves.get(rand.nextInt(topMoves.size()));
    }

    private int hardMoveScore(ChessMove move, PieceColor color) {
        Map<String, ChessPiece> snapshot = board.snapshot();
        board.executeMove(move);
        int material = board.getPieces().values().stream()
                .mapToInt(piece -> (piece.color == color ? 1 : -1) * getPieceValue(piece.type))
                .sum();
        PieceColor opponent = color == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
        int opponentBestCapture = board.getPieces().values().stream()
                .filter(piece -> piece.color == opponent)
                .flatMap(piece -> generateLegalMovesForPiece(piece, opponent).stream())
                .mapToInt(reply -> {
                    ChessPiece target = board.getPieces().get(reply.toX + "," + reply.toY);
                    return target == null ? 0 : getPieceValue(target.type);
                }).max().orElse(0);
        board.restore(snapshot);
        return material * 10 - opponentBestCapture * 9;
    }

    private List<ChessMove> generateLegalMovesForPiece(ChessPiece p, PieceColor color) {
        List<ChessMove> moves = new ArrayList<>();
        int x = p.getX(), y = p.getY();
        Map<String, ChessPiece> pieces = board.getPieces();

        switch (p.getType()) {
            case PAWN -> {
                int dir = (color == PieceColor.WHITE) ? 1 : -1;
                int startY = (color == PieceColor.WHITE) ? 1 : 6;

                // Đi thẳng 1 ô
                if (isInside(x, y + dir) && !pieces.containsKey(x + "," + (y + dir))) {
                    moves.add(new ChessMove(x, y, x, y + dir));
                    // Đi thẳng 2 ô từ vị trí xuất phát
                    if (y == startY && !pieces.containsKey(x + "," + (y + 2 * dir))) {
                        moves.add(new ChessMove(x, y, x, y + 2 * dir));
                    }
                }
                // Ăn chéo
                for (int dx : new int[]{-1, 1}) {
                    int nx = x + dx, ny = y + dir;
                    if (isInside(nx, ny)) {
                        ChessPiece target = pieces.get(nx + "," + ny);
                        if (target != null && target.getColor() != color) {
                            moves.add(new ChessMove(x, y, nx, ny));
                        }
                    }
                }
            }
            case KNIGHT -> {
                int[][] knightOffsets = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
                for (int[] off : knightOffsets) {
                    int nx = x + off[0], ny = y + off[1];
                    if (isInside(nx, ny)) {
                        ChessPiece target = pieces.get(nx + "," + ny);
                        if (target == null || target.getColor() != color) {
                            moves.add(new ChessMove(x, y, nx, ny));
                        }
                    }
                }
            }
            case BISHOP -> addSlidingMoves(moves, p, color, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
            case ROOK -> addSlidingMoves(moves, p, color, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
            case QUEEN -> addSlidingMoves(moves, p, color, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}});
            case KING -> {
                int[][] kingOffsets = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
                for (int[] off : kingOffsets) {
                    int nx = x + off[0], ny = y + off[1];
                    if (isInside(nx, ny)) {
                        ChessPiece target = pieces.get(nx + "," + ny);
                        if (target == null || target.getColor() != color) {
                            moves.add(new ChessMove(x, y, nx, ny));
                        }
                    }
                }
            }
        }
        return moves;
    }

    private void addSlidingMoves(List<ChessMove> moves, ChessPiece p, PieceColor color, int[][] dirs) {
        Map<String, ChessPiece> pieces = board.getPieces();
        for (int[] d : dirs) {
            int nx = p.getX() + d[0];
            int ny = p.getY() + d[1];
            while (isInside(nx, ny)) {
                ChessPiece target = pieces.get(nx + "," + ny);
                if (target == null) {
                    moves.add(new ChessMove(p.getX(), p.getY(), nx, ny));
                } else {
                    if (target.getColor() != color) {
                        moves.add(new ChessMove(p.getX(), p.getY(), nx, ny));
                    }
                    break; // Blocked
                }
                nx += d[0];
                ny += d[1];
            }
        }
    }

    private int evaluateMove(ChessMove move, ChessPiece mover, PieceColor color) {
        int score = 0;
        ChessPiece target = board.getPieces().get(move.getToX() + "," + move.getToY());

        // Điểm ăn quân
        if (target != null) {
            score += getPieceValue(target.getType()) * 10 - getPieceValue(mover.getType());
        }

        // Kiểm soát trung tâm bàn cờ (d4, d5, e4, e5)
        int toX = move.getToX(), toY = move.getToY();
        if ((toX == 3 || toX == 4) && (toY == 3 || toY == 4)) {
            score += 4;
        }

        // Khuyến khích tốt tiến lên
        if (mover.getType() == PieceType.PAWN) {
            int progress = (color == PieceColor.WHITE) ? toY : (7 - toY);
            score += progress * 2;
        }

        return score;
    }

    private int getPieceValue(PieceType type) {
        return switch (type) {
            case PAWN -> 10;
            case KNIGHT, BISHOP -> 35;
            case ROOK -> 50;
            case QUEEN -> 90;
            case KING -> 1000;
        };
    }

    private boolean isInside(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8;
    }

    private boolean sameMove(ChessMove first, ChessMove second) {
        return first.fromX == second.fromX && first.fromY == second.fromY
                && first.toX == second.toX && first.toY == second.toY;
    }

    private void switchPlayer() {
        currentPlayer = currentPlayer.equals(player1) ? player2 : player1;
    }

    private void checkGameEnd() {
        // Simplified - in real implementation check checkmate/stalemate
        if (board.isKingCaptured(PieceColor.WHITE)) {
            status = GameStatus.FINISHED;
            winner = player2;
        } else if (board.isKingCaptured(PieceColor.BLACK)) {
            status = GameStatus.FINISHED;
            winner = player1;
        }
    }

    public enum PieceColor { WHITE, BLACK }
    public enum PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }
    public enum GameStatus { IN_PROGRESS, FINISHED, DRAW }

    @Data
    public static class ChessMove {
        private int fromX, fromY;
        private int toX, toY;
        private PieceType promotion;

        public ChessMove() {}

        public ChessMove(int fromX, int fromY, int toX, int toY) {
            this.fromX = fromX; this.fromY = fromY; this.toX = toX; this.toY = toY;
        }
    }

    @Data
    public static class ChessPiece {
        private PieceType type;
        private PieceColor color;
        private int x, y;
        private boolean hasMoved;

        public ChessPiece(PieceType type, PieceColor color, int x, int y, boolean hasMoved) {
            this.type = type;
            this.color = color;
            this.x = x;
            this.y = y;
            this.hasMoved = hasMoved;
        }
    }

    public static class ChessBoard {
        private final Map<String, ChessPiece> pieces = new HashMap<>();

        public ChessBoard() {
            setupInitialPosition();
        }

        private void setupInitialPosition() {
            // White pieces
            placePiece(new ChessPiece(PieceType.ROOK, PieceColor.WHITE, 0, 0, false));
            placePiece(new ChessPiece(PieceType.KNIGHT, PieceColor.WHITE, 1, 0, false));
            placePiece(new ChessPiece(PieceType.BISHOP, PieceColor.WHITE, 2, 0, false));
            placePiece(new ChessPiece(PieceType.QUEEN, PieceColor.WHITE, 3, 0, false));
            placePiece(new ChessPiece(PieceType.KING, PieceColor.WHITE, 4, 0, false));
            placePiece(new ChessPiece(PieceType.BISHOP, PieceColor.WHITE, 5, 0, false));
            placePiece(new ChessPiece(PieceType.KNIGHT, PieceColor.WHITE, 6, 0, false));
            placePiece(new ChessPiece(PieceType.ROOK, PieceColor.WHITE, 7, 0, false));
            for (int i = 0; i < 8; i++) {
                placePiece(new ChessPiece(PieceType.PAWN, PieceColor.WHITE, i, 1, false));
            }

            // Black pieces
            placePiece(new ChessPiece(PieceType.ROOK, PieceColor.BLACK, 0, 7, false));
            placePiece(new ChessPiece(PieceType.KNIGHT, PieceColor.BLACK, 1, 7, false));
            placePiece(new ChessPiece(PieceType.BISHOP, PieceColor.BLACK, 2, 7, false));
            placePiece(new ChessPiece(PieceType.QUEEN, PieceColor.BLACK, 3, 7, false));
            placePiece(new ChessPiece(PieceType.KING, PieceColor.BLACK, 4, 7, false));
            placePiece(new ChessPiece(PieceType.BISHOP, PieceColor.BLACK, 5, 7, false));
            placePiece(new ChessPiece(PieceType.KNIGHT, PieceColor.BLACK, 6, 7, false));
            placePiece(new ChessPiece(PieceType.ROOK, PieceColor.BLACK, 7, 7, false));
            for (int i = 0; i < 8; i++) {
                placePiece(new ChessPiece(PieceType.PAWN, PieceColor.BLACK, i, 6, false));
            }
        }

        private void placePiece(ChessPiece piece) {
            pieces.put(piece.x + "," + piece.y, piece);
        }

        public Map<String, ChessPiece> getPieces() {
            return new HashMap<>(pieces);
        }

        public Map<String, ChessPiece> snapshot() {
            Map<String, ChessPiece> copy = new HashMap<>();
            pieces.forEach((key, piece) -> copy.put(key,
                    new ChessPiece(piece.type, piece.color, piece.x, piece.y, piece.hasMoved)));
            return copy;
        }

        public void restore(Map<String, ChessPiece> snapshot) {
            pieces.clear();
            pieces.putAll(snapshot);
        }

        public boolean isValidMove(ChessMove move, PieceColor color) {
            ChessPiece piece = pieces.get(move.fromX + "," + move.fromY);
            if (piece == null || piece.getColor() != color) return false;

            // Simplified validation - real implementation needs full chess rules
            return true;
        }

        public void executeMove(ChessMove move) {
            String fromKey = move.fromX + "," + move.fromY;
            String toKey = move.toX + "," + move.toY;

            ChessPiece piece = pieces.remove(fromKey);
            if (piece != null) {
                piece.setX(move.toX);
                piece.setY(move.toY);
                piece.setHasMoved(true);
                pieces.put(toKey, piece);
            }
        }

        public boolean isKingCaptured(PieceColor color) {
            return pieces.values().stream()
                    .noneMatch(p -> p.getType() == PieceType.KING && p.getColor() == color);
        }
    }

    @Data
    public static class ChessGameState {
        private final Map<String, ChessPiece> pieces;
        private final String currentPlayer;
        private final GameStatus status;
        private final List<String> moveHistory;
        private final String whitePlayer;
        private final String blackPlayer;
        private final String winner;
        private final List<ChessMove> legalMoves;

        public ChessGameState(Map<String, ChessPiece> pieces, String currentPlayer, GameStatus status, List<String> moveHistory, String whitePlayer, String blackPlayer, String winner, List<ChessMove> legalMoves) {
            this.pieces = pieces;
            this.currentPlayer = currentPlayer;
            this.status = status;
            this.moveHistory = moveHistory;
            this.whitePlayer = whitePlayer;
            this.blackPlayer = blackPlayer;
            this.winner = winner;
            this.legalMoves = legalMoves;
        }
    }
}
