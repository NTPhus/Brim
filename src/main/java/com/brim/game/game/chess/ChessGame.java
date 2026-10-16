package com.brim.game.game.chess;

import com.brim.game.service.TurnBasedGameService;
import com.brim.game.game.ai.AlphaBetaSearch;
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
    private ChessMove lastMove;

    public ChessGame(String roomId) { this(roomId, "MEDIUM"); }

    public ChessGame(String roomId, String difficulty) {
        this.roomId = roomId;
        this.difficulty = difficulty == null ? "MEDIUM" : difficulty.toUpperCase();
    }

    @Override public Class<?> moveType() { return ChessMove.class; }

    @Override
    public synchronized boolean makeMove(String playerId, Object move) {
        if (isGameOver() || !Objects.equals(playerId, currentPlayer)) return false;
        if (!(move instanceof ChessMove chessMove)) return false;

        PieceColor color = currentPlayer.equals(player1) ? PieceColor.WHITE : PieceColor.BLACK;
        ChessPiece piece = board.getPieces().get(chessMove.fromX + "," + chessMove.fromY);
        if (!board.isValidMove(chessMove, color) || piece == null ||
                generateLegalMovesForPiece(piece, color).stream().noneMatch(candidate -> sameMove(candidate, chessMove))) {
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
        PieceColor currentColor = Objects.equals(currentPlayer, player1) ? PieceColor.WHITE : PieceColor.BLACK;
        List<ChessMove> legalMoves = isGameOver() ? List.of() : legalMoves(currentColor);
        return new ChessGameState(board.snapshot(), currentPlayer, status, new ArrayList<>(moveHistory), player1, player2, winner, legalMoves, lastMove, inCheck(currentColor));
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
        ChessGame analysis;
        PieceColor color;
        synchronized (this) {
            if (isGameOver() || currentPlayer == null) return null;
            color = currentPlayer.equals(player1) ? PieceColor.WHITE : PieceColor.BLACK;
            analysis = new ChessGame(roomId, difficulty);
            analysis.board.load(board.save());
        }
        return new AlphaBetaSearch<>(analysis.new SearchPosition(color)).findBestMove(difficulty);
    }

    private PieceColor opposite(PieceColor color) {
        return color == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
    }

    private List<ChessMove> legalMoves(PieceColor color) {
        List<ChessMove> moves = new ArrayList<>();
        for (ChessPiece piece : board.getPieces().values()) {
            if (piece.color == color) moves.addAll(generateLegalMovesForPiece(piece, color));
        }
        return moves;
    }

    private List<ChessMove> generateLegalMovesForPiece(ChessPiece piece, PieceColor color) {
        int originalX = piece.x, originalY = piece.y;
        List<ChessMove> moves = generatePseudoMoves(piece, color);
        if (piece.type == PieceType.KING && !piece.hasMoved && piece.x == 4
                && piece.y == (color == PieceColor.WHITE ? 0 : 7) && !inCheck(color)) {
            for (int rookX : new int[]{0, 7}) {
                ChessPiece rook = board.getPieces().get(rookX + "," + piece.y);
                if (rook == null || rook.type != PieceType.ROOK || rook.color != color || rook.hasMoved) continue;
                int direction = rookX == 0 ? -1 : 1;
                boolean clear = true;
                for (int x = originalX + direction; x != rookX; x += direction) {
                    if (board.getPieces().containsKey(x + "," + piece.y)) { clear = false; break; }
                }
                if (!clear) continue;
                var snapshot = board.save();
                boolean safe;
                try {
                    board.executeMove(new ChessMove(originalX, originalY, originalX + direction, originalY));
                    safe = !inCheck(color);
                } finally { board.load(snapshot); }
                if (safe) moves.add(new ChessMove(4, rook.y, 4 + direction * 2, rook.y));
            }
        }
        moves.removeIf(move -> {
            ChessPiece target = board.getPieces().get(move.toX + "," + move.toY);
            if (target != null && target.type == PieceType.KING) return true;
            var snapshot = board.save();
            try {
                board.executeMove(move);
                return inCheck(color);
            } finally { board.load(snapshot); }
        });
        return moves;
    }

    private boolean inCheck(PieceColor color) {
        ChessPiece king = board.getPieces().values().stream()
                .filter(p -> p.color == color && p.type == PieceType.KING).findFirst().orElse(null);
        if (king == null) return true;
        for (ChessPiece enemy : board.getPieces().values()) {
            if (enemy.color == color) continue;
            // Pawn attacks differ from their forward movement.
            if (enemy.type == PieceType.PAWN) {
                if (Math.abs(enemy.x - king.x) == 1 && king.y - enemy.y == (enemy.color == PieceColor.WHITE ? 1 : -1)) return true;
                continue;
            }
            if (generatePseudoMoves(enemy, enemy.color).stream().anyMatch(m -> m.toX == king.x && m.toY == king.y)) return true;
        }
        return false;
    }

    private final class SearchPosition implements AlphaBetaSearch.Position<ChessMove> {
        private PieceColor side;
        private final Deque<ChessBoard.BoardSnapshot> history = new ArrayDeque<>();
        SearchPosition(PieceColor color) { side = color; }
        public List<ChessMove> legalMoves() { return ChessGame.this.legalMoves(side); }
        public void play(ChessMove move) { history.push(board.save()); board.executeMove(move); side = opposite(side); }
        public void undo() { board.load(history.pop()); side = opposite(side); }
        public boolean inCheck() { return ChessGame.this.inCheck(side); }
        public boolean stalemateIsLoss() { return false; }
        public boolean tactical(ChessMove move) { return board.getPieces().containsKey(move.toX + "," + move.toY) || move.promotion != null || isEnPassant(move); }
        public int movePriority(ChessMove move) {
            ChessPiece mover = board.getPieces().get(move.fromX + "," + move.fromY);
            ChessPiece target = board.getPieces().get(move.toX + "," + move.toY);
            return (target == null ? 0 : 1000 + getPieceValue(target.type) * 10 - getPieceValue(mover.type))
                    + evaluateMove(move, mover, side) + (move.promotion == null ? 0 : getPieceValue(move.promotion) * 10);
        }
        public int evaluate() { return evaluatePosition(side); }
        public String key() {
            StringBuilder key = new StringBuilder(side.name());
            board.getPieces().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ChessPiece p = entry.getValue();
                key.append('|').append(entry.getKey()).append(':').append(p.color).append(':').append(p.type).append(p.hasMoved);
            });
            key.append("/ep:").append(board.enPassant);
            return key.toString();
        }
    }

    private List<ChessMove> generatePseudoMoves(ChessPiece p, PieceColor color) {
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
        if (p.type == PieceType.PAWN) {
            int direction = color == PieceColor.WHITE ? 1 : -1;
            for (int dx : new int[]{-1, 1}) {
                ChessMove capture = new ChessMove(x, y, x + dx, y + direction);
                if (isEnPassant(capture)) moves.add(capture);
            }
            List<ChessMove> promotions = new ArrayList<>();
            for (ChessMove move : moves) {
                if (move.toY == 0 || move.toY == 7) {
                    for (PieceType type : new PieceType[]{PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT}) {
                        ChessMove promotion = new ChessMove(move.fromX, move.fromY, move.toX, move.toY);
                        promotion.promotion = type;
                        promotions.add(promotion);
                    }
                } else promotions.add(move);
            }
            return promotions;
        }
        return moves;
    }

    private boolean isEnPassant(ChessMove move) {
        ChessPiece pawn = board.getPieces().get(move.fromX + "," + move.fromY);
        ChessPiece victim = board.getPieces().get(move.toX + "," + move.fromY);
        return pawn != null && pawn.type == PieceType.PAWN && victim != null
                && victim.type == PieceType.PAWN && victim.color != pawn.color
                && Math.abs(move.toX - move.fromX) == 1
                && move.toY - move.fromY == (pawn.color == PieceColor.WHITE ? 1 : -1)
                && (move.toX + "," + move.toY).equals(board.enPassant);
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

    private int evaluatePosition(PieceColor side) {
        int score = 0;
        for (ChessPiece p : board.getPieces().values()) {
            int value = getPieceValue(p.type) * 10;
            int progress = p.color == PieceColor.WHITE ? p.y : 7 - p.y;
            int center = 14 - Math.abs(2 * p.x - 7) - Math.abs(2 * p.y - 7);
            value += switch (p.type) {
                case PAWN -> progress * 7 + center * 2;
                case KNIGHT -> center * 6;
                case BISHOP -> center * 3 + progress * 2;
                case ROOK -> progress * 2;
                case QUEEN -> center;
                case KING -> -center * 2;
            };
            if (p.type != PieceType.PAWN && p.type != PieceType.KING) value += generatePseudoMoves(p, p.color).size() * 2;
            if (p.type == PieceType.KING) {
                int forward = p.color == PieceColor.WHITE ? 1 : -1;
                for (int dx = -1; dx <= 1; dx++) {
                    ChessPiece shield = board.getPieces().get((p.x + dx) + "," + (p.y + forward));
                    if (shield != null && shield.color == p.color && shield.type == PieceType.PAWN) value += 12;
                }
            }
            score += p.color == side ? value : -value;
        }
        return score;
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
                && first.toX == second.toX && first.toY == second.toY
                && (first.promotion == null ? second.promotion == null
                    : first.promotion == (second.promotion == null ? PieceType.QUEEN : second.promotion));
    }

    private void switchPlayer() {
        currentPlayer = currentPlayer.equals(player1) ? player2 : player1;
    }

    private void checkGameEnd() {
        PieceColor side = currentPlayer.equals(player1) ? PieceColor.WHITE : PieceColor.BLACK;
        if (legalMoves(side).isEmpty()) {
            if (inCheck(side)) {
                status = GameStatus.FINISHED;
                winner = side == PieceColor.WHITE ? player2 : player1;
            } else {
                status = GameStatus.DRAW;
                winner = null;
            }
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
        private String enPassant;
        public record BoardSnapshot(Map<String, ChessPiece> pieces, String enPassant) {}

        public BoardSnapshot save() { return new BoardSnapshot(snapshot(), enPassant); }
        public void load(BoardSnapshot state) {
            restore(state.pieces());
            enPassant = state.enPassant();
        }

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
            snapshot.forEach((key, piece) -> pieces.put(key,
                    new ChessPiece(piece.type, piece.color, piece.x, piece.y, piece.hasMoved)));
            enPassant = null;
        }

        public boolean isValidMove(ChessMove move, PieceColor color) {
            ChessPiece piece = pieces.get(move.fromX + "," + move.fromY);
            if (piece == null || piece.getColor() != color) return false;

            // Ownership guard; the game also validates movement and king safety.
            return true;
        }

        public void executeMove(ChessMove move) {
            ChessPiece piece = pieces.remove(move.fromX + "," + move.fromY);
            if (piece == null) return;
            if (piece.type == PieceType.PAWN && move.fromX != move.toX
                    && (move.toX + "," + move.toY).equals(enPassant)) {
                pieces.remove(move.toX + "," + move.fromY);
            }
            enPassant = piece.type == PieceType.PAWN && Math.abs(move.toY - move.fromY) == 2
                    ? move.fromX + "," + ((move.fromY + move.toY) / 2) : null;
            if (piece.type == PieceType.KING && Math.abs(move.toX - move.fromX) == 2) {
                ChessPiece rook = pieces.remove((move.toX > move.fromX ? 7 : 0) + "," + move.fromY);
                if (rook != null) {
                    rook.x = move.toX > move.fromX ? 5 : 3;
                    rook.hasMoved = true;
                    pieces.put(rook.x + "," + rook.y, rook);
                }
            }
            piece.x = move.toX;
            piece.y = move.toY;
            piece.hasMoved = true;
            if (piece.type == PieceType.PAWN && (piece.y == 0 || piece.y == 7)) {
                piece.type = move.promotion == null ? PieceType.QUEEN : move.promotion;
            }
            pieces.put(move.toX + "," + move.toY, piece);
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
        private final ChessMove lastMove;
        private final boolean check;

        public boolean isCheckmate() {
            return status == GameStatus.FINISHED && check;
        }

        public ChessGameState(Map<String, ChessPiece> pieces, String currentPlayer, GameStatus status, List<String> moveHistory, String whitePlayer, String blackPlayer, String winner, List<ChessMove> legalMoves, ChessMove lastMove, boolean check) {
            this.pieces = pieces;
            this.currentPlayer = currentPlayer;
            this.status = status;
            this.moveHistory = moveHistory;
            this.whitePlayer = whitePlayer;
            this.blackPlayer = blackPlayer;
            this.winner = winner;
            this.legalMoves = legalMoves;
            this.lastMove = lastMove;
            this.check = check;
        }
    }
}
