package com.brim.game.game.caro;

import com.brim.game.game.board.GridDuel;
import java.util.*;

/** Freestyle Gomoku: five or more wins, including a line blocked at both ends. */
public final class CaroGame extends GridDuel {
    private static final int[][] DIRECTIONS = {{1,0},{0,1},{1,1},{1,-1}};
    public CaroGame(String roomId, String difficulty) { super(15, difficulty); }
    @Override protected GridDuel fresh() { return new CaroGame("", difficulty); }
    @Override protected List<Move> moves(int side) {
        List<Move> legal = new ArrayList<>();
        for (int r=0;r<size;r++) for (int c=0;c<size;c++) if (board[r][c]==0) legal.add(new Move(r,c));
        return legal;
    }
    @Override protected void apply(Move move) {
        int r=move.row(), c=move.column();
        board[r][c]=side;
        for (int[] d:DIRECTIONS) {
            List<Cell> line = new ArrayList<>(); line.add(new Cell(r,c));
            for (int sign:new int[]{-1,1}) {
                int y=r+sign*d[0], x=c+sign*d[1];
                while (inside(y,x) && board[y][x]==side) { line.add(new Cell(y,x)); y+=sign*d[0]; x+=sign*d[1]; }
            }
            if (line.size()>=5) { status="FINISHED"; winner=player(side); winningCells=List.copyOf(line); return; }
        }
        if (moves(side).isEmpty()) status="DRAW"; else side=3-side;
    }
    @Override protected List<Move> candidates() {
        if (count(1)+count(2)==0) return List.of(new Move(7,7));
        // Rank every empty square so immediate wins and blocks are never pruned.
        List<Move> legal=moves(side);
        Map<Move,Integer> rank=new HashMap<>();
        for (Move move:legal) rank.put(move, potential(move,side)*2+potential(move,3-side));
        legal.sort(Comparator.<Move>comparingInt(rank::get).reversed());
        return new ArrayList<>(legal.subList(0,Math.min(12,legal.size())));
    }
    private int potential(Move move,int value) {
        int best=0;
        for (int[] d:DIRECTIONS) {
            int n=1, open=0;
            for (int sign:new int[]{-1,1}) {
                int r=move.row()+sign*d[0],c=move.column()+sign*d[1];
                while (inside(r,c)&&board[r][c]==value) { n++; r+=sign*d[0]; c+=sign*d[1]; }
                if (inside(r,c)&&board[r][c]==0) open++;
            }
            best+=n>=5?100000:n==4?5000+open*1000:n==3?200+open*100:n==2?10+open*5:open;
        }
        return best;
    }
    @Override protected int evaluate(int value) {
        int own=0,other=0;
        for (Move m:moves(side)) { own=Math.max(own,potential(m,value)); other=Math.max(other,potential(m,3-value)); }
        return own-other;
    }
}
