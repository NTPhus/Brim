package com.brim.game.game.chinesechess;

import com.brim.game.service.TurnBasedGameService;
import com.brim.game.game.ai.AlphaBetaSearch;
import lombok.Data;

import java.util.*;

@Data
public class ChineseChessGame implements TurnBasedGameService.TurnBasedGame {
    private final String roomId;
    private final String difficulty;
    private String player1;
    private String player2;
    private String currentPlayer;
    private final ChineseChessBoard board = new ChineseChessBoard();
    private GameStatus status = GameStatus.IN_PROGRESS;
    private String winner;
    private final List<String> moveHistory = new ArrayList<>();
    private ChineseChessMove lastMove;

    public ChineseChessGame(String roomId) { this(roomId, "MEDIUM"); }

    public ChineseChessGame(String roomId, String difficulty) {
        this.roomId = roomId;
        this.difficulty = difficulty == null ? "MEDIUM" : difficulty.toUpperCase();
    }

    @Override
    public synchronized boolean makeMove(String playerId, Object move) {
        if (isGameOver() || !Objects.equals(playerId, currentPlayer)) return false;
        if (!(move instanceof ChineseChessMove chessMove)) return false;

        PieceColor color = currentPlayer.equals(player1) ? PieceColor.RED : PieceColor.BLACK;
        ChineseChessPiece piece = board.getPieces().get(chessMove.fromX + "," + chessMove.fromY);
        if (!board.isValidMove(chessMove, color) || piece == null ||
                generateXiangqiMoves(piece, color).stream().noneMatch(candidate -> sameMove(candidate, chessMove))) {
            return false;
        }

        board.executeMove(chessMove);
        lastMove = chessMove;
        moveHistory.add(chessMove.toString());
        switchPlayer();
        checkGameEnd();
        return true;
    }

    @Override
    public synchronized Object getState() {
        PieceColor currentColor = Objects.equals(currentPlayer, player1) ? PieceColor.RED : PieceColor.BLACK;
        List<ChineseChessMove> legalMoves = isGameOver() ? List.of() : legalMoves(currentColor);
        return new ChineseChessGameState(board.snapshot(), currentPlayer, status, new ArrayList<>(moveHistory), player1, player2, winner, legalMoves, lastMove, inCheck(currentColor));
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
    }

    @Override
    public Object generateBotMove() {
        ChineseChessGame analysis;
        PieceColor color;
        synchronized (this) {
            if (isGameOver() || currentPlayer == null) return null;
            color = currentPlayer.equals(player1) ? PieceColor.RED : PieceColor.BLACK;
            analysis = new ChineseChessGame(roomId, difficulty);
            analysis.board.restore(board.snapshot());
        }
        return new AlphaBetaSearch<>(analysis.new SearchPosition(color)).findBestMove(difficulty);
    }

    private PieceColor opposite(PieceColor color) {
        return color == PieceColor.RED ? PieceColor.BLACK : PieceColor.RED;
    }

    private List<ChineseChessMove> legalMoves(PieceColor color) {
        List<ChineseChessMove> moves = new ArrayList<>();
        for (ChineseChessPiece piece : board.getPieces().values()) {
            if (piece.color == color) moves.addAll(generateXiangqiMoves(piece, color));
        }
        return moves;
    }

    private List<ChineseChessMove> generateXiangqiMoves(ChineseChessPiece piece, PieceColor color) {
        List<ChineseChessMove> moves = generatePseudoMoves(piece, color);
        moves.removeIf(move -> {
            ChineseChessPiece target = board.getPieces().get(move.toX + "," + move.toY);
            if (target != null && target.type == PieceType.GENERAL) return true;
            var snapshot = board.snapshot();
            try {
                board.executeMove(move);
                return inCheck(color);
            } finally { board.restore(snapshot); }
        });
        return moves;
    }

    private boolean inCheck(PieceColor color) {
        ChineseChessPiece king = board.getPieces().values().stream()
                .filter(p -> p.color == color && p.type == PieceType.GENERAL).findFirst().orElse(null);
        if (king == null) return true;
        for (ChineseChessPiece enemy : board.getPieces().values()) {
            if (enemy.color == color) continue;
            // Flying generals may not face each other on an open file.
            if (enemy.type == PieceType.GENERAL && enemy.x == king.x) {
                boolean blocked = false;
                for (int y = Math.min(enemy.y, king.y) + 1; y < Math.max(enemy.y, king.y); y++) {
                    if (board.getPieces().containsKey(king.x + "," + y)) { blocked = true; break; }
                }
                if (!blocked) return true;
            }
            if (generatePseudoMoves(enemy, enemy.color).stream().anyMatch(m -> m.toX == king.x && m.toY == king.y)) return true;
        }
        return false;
    }

    private final class SearchPosition implements AlphaBetaSearch.Position<ChineseChessMove> {
        private PieceColor side;
        private final Deque<Map<String, ChineseChessPiece>> history = new ArrayDeque<>();
        SearchPosition(PieceColor color) { side = color; }
        public List<ChineseChessMove> legalMoves() { return ChineseChessGame.this.legalMoves(side); }
        public void play(ChineseChessMove move) { history.push(board.snapshot()); board.executeMove(move); side = opposite(side); }
        public void undo() { board.restore(history.pop()); side = opposite(side); }
        public boolean inCheck() { return ChineseChessGame.this.inCheck(side); }
        public boolean stalemateIsLoss() { return true; }
        public boolean tactical(ChineseChessMove move) { return board.getPieces().containsKey(move.toX + "," + move.toY); }
        public int movePriority(ChineseChessMove move) {
            ChineseChessPiece mover = board.getPieces().get(move.fromX + "," + move.fromY);
            ChineseChessPiece target = board.getPieces().get(move.toX + "," + move.toY);
            return (target == null ? 0 : 1000 + getPieceValue(target.type) * 10 - getPieceValue(mover.type))
                    + evaluateXiangqiMove(move, mover, side);
        }
        public int evaluate() { return evaluatePosition(side); }
        public String key() {
            StringBuilder key = new StringBuilder(side.name());
            board.getPieces().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ChineseChessPiece p = entry.getValue();
                key.append('|').append(entry.getKey()).append(':').append(p.color).append(':').append(p.type);
            });

            return key.toString();
        }
    }

    private List<ChineseChessMove> generatePseudoMoves(ChineseChessPiece p, PieceColor color) {
        List<ChineseChessMove> moves = new ArrayList<>();
        int x = p.getX(), y = p.getY();
        Map<String, ChineseChessPiece> pieces = board.getPieces();

        switch (p.getType()) {
            case CHARIOT -> { // Xe: Đi thẳng/ngang đến khi bị cản
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    while (isInside(nx, ny)) {
                        ChineseChessPiece target = pieces.get(nx + "," + ny);
                        if (target == null) {
                            moves.add(new ChineseChessMove(x, y, nx, ny));
                        } else {
                            if (target.getColor() != color) {
                                moves.add(new ChineseChessMove(x, y, nx, ny));
                            }
                            break;
                        }
                        nx += d[0];
                        ny += d[1];
                    }
                }
            }
            case HORSE -> { // Mã: Đi chữ L, có cản mã (leg blocking)
                int[][] horseSteps = {
                        {1, 2, 0, 1}, {-1, 2, 0, 1},     // đi xuống: cản ở (x, y+1)
                        {1, -2, 0, -1}, {-1, -2, 0, -1}, // đi lên: cản ở (x, y-1)
                        {2, 1, 1, 0}, {2, -1, 1, 0},     // đi phải: cản ở (x+1, y)
                        {-2, 1, -1, 0}, {-2, -1, -1, 0}  // đi trái: cản ở (x-1, y)
                };
                for (int[] s : horseSteps) {
                    int nx = x + s[0], ny = y + s[1];
                    int blockX = x + s[2], blockY = y + s[3];
                    if (isInside(nx, ny) && !pieces.containsKey(blockX + "," + blockY)) {
                        ChineseChessPiece target = pieces.get(nx + "," + ny);
                        if (target == null || target.getColor() != color) {
                            moves.add(new ChineseChessMove(x, y, nx, ny));
                        }
                    }
                }
            }
            case ELEPHANT -> { // Tượng: Đi chéo 2 ô, không qua sông, có cản tượng
                int[][] elephantSteps = {{2, 2, 1, 1}, {2, -2, 1, -1}, {-2, 2, -1, 1}, {-2, -2, -1, -1}};
                for (int[] s : elephantSteps) {
                    int nx = x + s[0], ny = y + s[1];
                    int eyeX = x + s[2], eyeY = y + s[3];
                    // Không được qua sông
                    boolean riverValid = (color == PieceColor.RED) ? (ny >= 5 && ny <= 9) : (ny >= 0 && ny <= 4);
                    if (isInside(nx, ny) && riverValid && !pieces.containsKey(eyeX + "," + eyeY)) {
                        ChineseChessPiece target = pieces.get(nx + "," + ny);
                        if (target == null || target.getColor() != color) {
                            moves.add(new ChineseChessMove(x, y, nx, ny));
                        }
                    }
                }
            }
            case CANNON -> { // Pháo: Đi như Xe, ăn phải nhảy qua 1 ngòi
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    boolean jumped = false;
                    while (isInside(nx, ny)) {
                        ChineseChessPiece target = pieces.get(nx + "," + ny);
                        if (!jumped) {
                            if (target == null) {
                                moves.add(new ChineseChessMove(x, y, nx, ny));
                            } else {
                                jumped = true; // Tìm thấy ngòi
                            }
                        } else {
                            if (target != null) {
                                if (target.getColor() != color) {
                                    moves.add(new ChineseChessMove(x, y, nx, ny));
                                }
                                break; // Chỉ được ăn sau 1 ngòi rồi dừng
                            }
                        }
                        nx += d[0];
                        ny += d[1];
                    }
                }
            }
            case ADVISOR -> { // Sĩ: Đi chéo 1 ô trong Cung (3x3 palace)
                int[][] advisorSteps = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int[] s : advisorSteps) {
                    int nx = x + s[0], ny = y + s[1];
                    if (isInPalace(nx, ny, color)) {
                        ChineseChessPiece target = pieces.get(nx + "," + ny);
                        if (target == null || target.getColor() != color) {
                            moves.add(new ChineseChessMove(x, y, nx, ny));
                        }
                    }
                }
            }
            case GENERAL -> { // Tướng: Đi thẳng/ngang 1 ô trong Cung
                int[][] generalSteps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] s : generalSteps) {
                    int nx = x + s[0], ny = y + s[1];
                    if (isInPalace(nx, ny, color)) {
                        ChineseChessPiece target = pieces.get(nx + "," + ny);
                        if (target == null || target.getColor() != color) {
                            moves.add(new ChineseChessMove(x, y, nx, ny));
                        }
                    }
                }
            }
            case SOLDIER -> { // Tốt: Tiến 1 ô; qua sông đi được cả ngang (trái/phải)
                int forward = (color == PieceColor.RED) ? -1 : 1;
                boolean acrossRiver = (color == PieceColor.RED) ? (y <= 4) : (y >= 5);

                // Nước tiến
                int ny = y + forward;
                if (isInside(x, ny)) {
                    ChineseChessPiece target = pieces.get(x + "," + ny);
                    if (target == null || target.getColor() != color) {
                        moves.add(new ChineseChessMove(x, y, x, ny));
                    }
                }

                // Nếu đã qua sông thì được rẽ trái, phải
                if (acrossRiver) {
                    for (int dx : new int[]{-1, 1}) {
                        int nx = x + dx;
                        if (isInside(nx, y)) {
                            ChineseChessPiece target = pieces.get(nx + "," + y);
                            if (target == null || target.getColor() != color) {
                                moves.add(new ChineseChessMove(x, y, nx, y));
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    private int evaluatePosition(PieceColor side) {
        int score = 0;
        for (ChineseChessPiece p : board.getPieces().values()) {
            int value = getPieceValue(p.type) * 10;
            int progress = p.color == PieceColor.RED ? 9 - p.y : p.y;
            int center = 4 - Math.abs(p.x - 4);
            value += switch (p.type) {
                case SOLDIER -> progress * 5 + (progress >= 5 ? 55 + center * 4 : 0);
                case HORSE -> center * 7 + Math.min(progress, 6) * 3;
                case CANNON -> center * 4;
                case CHARIOT -> center * 2;
                case ADVISOR, ELEPHANT -> 8;
                case GENERAL -> 0;
            };
            if (p.type == PieceType.CHARIOT || p.type == PieceType.HORSE || p.type == PieceType.CANNON) value += generatePseudoMoves(p, p.color).size() * 3;
            score += p.color == side ? value : -value;
        }
        return score;
    }

    private int evaluateXiangqiMove(ChineseChessMove move, ChineseChessPiece mover, PieceColor color) {
        int score = 0;
        ChineseChessPiece target = board.getPieces().get(move.getToX() + "," + move.getToY());

        // Điểm ăn quân đối phương
        if (target != null) {
            score += getPieceValue(target.getType()) * 10 - getPieceValue(mover.getType());
        }

        // Tốt qua sông được thưởng điểm
        if (mover.getType() == PieceType.SOLDIER) {
            boolean across = (color == PieceColor.RED) ? (move.getToY() <= 4) : (move.getToY() >= 5);
            if (across) score += 8;
        }

        // Khuyến khích Xe, Pháo, Mã phát triển kiểm soát trung lộ (cột 4)
        if (move.getToX() == 4) {
            score += 3;
        }

        return score;
    }

    private int getPieceValue(PieceType type) {
        return switch (type) {
            case GENERAL -> 1000;
            case CHARIOT -> 90;
            case CANNON -> 50;
            case HORSE -> 45;
            case ELEPHANT, ADVISOR -> 20;
            case SOLDIER -> 12;
        };
    }

    private boolean isInside(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 10;
    }

    private boolean sameMove(ChineseChessMove first, ChineseChessMove second) {
        return first.fromX == second.fromX && first.fromY == second.fromY
                && first.toX == second.toX && first.toY == second.toY;
    }

    private boolean isInPalace(int x, int y, PieceColor color) {
        if (x < 3 || x > 5) return false;
        return (color == PieceColor.RED) ? (y >= 7 && y <= 9) : (y >= 0 && y <= 2);
    }

    private void switchPlayer() {
        currentPlayer = currentPlayer.equals(player1) ? player2 : player1;
    }

    private void checkGameEnd() {
        PieceColor side = currentPlayer.equals(player1) ? PieceColor.RED : PieceColor.BLACK;
        if (legalMoves(side).isEmpty()) {
            // In Xiangqi, stalemate is a loss for the side unable to move.
            status = GameStatus.FINISHED;
            winner = side == PieceColor.RED ? player2 : player1;
        }
    }

    public enum PieceColor { RED, BLACK }
    public enum PieceType { GENERAL, ADVISOR, ELEPHANT, HORSE, CHARIOT, CANNON, SOLDIER }
    public enum GameStatus { IN_PROGRESS, FINISHED, DRAW }

    @Data
    public static class ChineseChessMove {
        private int fromX, fromY;
        private int toX, toY;

        public ChineseChessMove() {}

        public ChineseChessMove(int fromX, int fromY, int toX, int toY) {
            this.fromX = fromX; this.fromY = fromY; this.toX = toX; this.toY = toY;
        }
    }

    @Data
    public static class ChineseChessPiece {
        private PieceType type;
        private PieceColor color;
        private int x, y;

        public ChineseChessPiece(PieceType type, PieceColor color, int x, int y) {
            this.type = type;
            this.color = color;
            this.x = x;
            this.y = y;
        }
    }

    public static class ChineseChessBoard {
        private final Map<String, ChineseChessPiece> pieces = new HashMap<>();

        public ChineseChessBoard() {
            setupInitialPosition();
        }

        private void setupInitialPosition() {
            placePiece(new ChineseChessPiece(PieceType.CHARIOT, PieceColor.BLACK, 0, 0));
            placePiece(new ChineseChessPiece(PieceType.HORSE, PieceColor.BLACK, 1, 0));
            placePiece(new ChineseChessPiece(PieceType.ELEPHANT, PieceColor.BLACK, 2, 0));
            placePiece(new ChineseChessPiece(PieceType.ADVISOR, PieceColor.BLACK, 3, 0));
            placePiece(new ChineseChessPiece(PieceType.GENERAL, PieceColor.BLACK, 4, 0));
            placePiece(new ChineseChessPiece(PieceType.ADVISOR, PieceColor.BLACK, 5, 0));
            placePiece(new ChineseChessPiece(PieceType.ELEPHANT, PieceColor.BLACK, 6, 0));
            placePiece(new ChineseChessPiece(PieceType.HORSE, PieceColor.BLACK, 7, 0));
            placePiece(new ChineseChessPiece(PieceType.CHARIOT, PieceColor.BLACK, 8, 0));

            placePiece(new ChineseChessPiece(PieceType.CANNON, PieceColor.BLACK, 1, 2));
            placePiece(new ChineseChessPiece(PieceType.CANNON, PieceColor.BLACK, 7, 2));

            for (int i = 0; i < 9; i += 2) {
                placePiece(new ChineseChessPiece(PieceType.SOLDIER, PieceColor.BLACK, i, 3));
            }

            placePiece(new ChineseChessPiece(PieceType.CHARIOT, PieceColor.RED, 0, 9));
            placePiece(new ChineseChessPiece(PieceType.HORSE, PieceColor.RED, 1, 9));
            placePiece(new ChineseChessPiece(PieceType.ELEPHANT, PieceColor.RED, 2, 9));
            placePiece(new ChineseChessPiece(PieceType.ADVISOR, PieceColor.RED, 3, 9));
            placePiece(new ChineseChessPiece(PieceType.GENERAL, PieceColor.RED, 4, 9));
            placePiece(new ChineseChessPiece(PieceType.ADVISOR, PieceColor.RED, 5, 9));
            placePiece(new ChineseChessPiece(PieceType.ELEPHANT, PieceColor.RED, 6, 9));
            placePiece(new ChineseChessPiece(PieceType.HORSE, PieceColor.RED, 7, 9));
            placePiece(new ChineseChessPiece(PieceType.CHARIOT, PieceColor.RED, 8, 9));

            placePiece(new ChineseChessPiece(PieceType.CANNON, PieceColor.RED, 1, 7));
            placePiece(new ChineseChessPiece(PieceType.CANNON, PieceColor.RED, 7, 7));

            for (int i = 0; i < 9; i += 2) {
                placePiece(new ChineseChessPiece(PieceType.SOLDIER, PieceColor.RED, i, 6));
            }
        }

        private void placePiece(ChineseChessPiece piece) {
            pieces.put(piece.x + "," + piece.y, piece);
        }

        public Map<String, ChineseChessPiece> getPieces() {
            return new HashMap<>(pieces);
        }

        public Map<String, ChineseChessPiece> snapshot() {
            Map<String, ChineseChessPiece> copy = new HashMap<>();
            pieces.forEach((key, piece) -> copy.put(key,
                    new ChineseChessPiece(piece.type, piece.color, piece.x, piece.y)));
            return copy;
        }

        public void restore(Map<String, ChineseChessPiece> snapshot) {
            pieces.clear();
            pieces.putAll(snapshot);
        }

        public boolean isValidMove(ChineseChessMove move, PieceColor color) {
            ChineseChessPiece piece = pieces.get(move.fromX + "," + move.fromY);
            if (piece == null || piece.getColor() != color) return false;
            return true;
        }

        public void executeMove(ChineseChessMove move) {
            String fromKey = move.fromX + "," + move.fromY;
            String toKey = move.toX + "," + move.toY;

            ChineseChessPiece piece = pieces.remove(fromKey);
            if (piece != null) {
                piece.setX(move.toX);
                piece.setY(move.toY);
                pieces.put(toKey, piece);
            }
        }

        public boolean isGeneralCaptured(PieceColor color) {
            return pieces.values().stream()
                    .noneMatch(p -> p.getType() == PieceType.GENERAL && p.getColor() == color);
        }
    }

    @Data
    public static class ChineseChessGameState {
        private final Map<String, ChineseChessPiece> pieces;
        private final String currentPlayer;
        private final GameStatus status;
        private final List<String> moveHistory;
        private final String redPlayer;
        private final String blackPlayer;
        private final String winner;
        private final List<ChineseChessMove> legalMoves;
        private final ChineseChessMove lastMove;
        private final boolean check;

        public ChineseChessGameState(Map<String, ChineseChessPiece> pieces, String currentPlayer, GameStatus status, List<String> moveHistory, String redPlayer, String blackPlayer, String winner, List<ChineseChessMove> legalMoves, ChineseChessMove lastMove, boolean check) {
            this.pieces = pieces;
            this.currentPlayer = currentPlayer;
            this.status = status;
            this.moveHistory = moveHistory;
            this.redPlayer = redPlayer;
            this.blackPlayer = blackPlayer;
            this.winner = winner;
            this.legalMoves = legalMoves;
            this.lastMove = lastMove;
            this.check = check;
        }
    }
}
