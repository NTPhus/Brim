package com.brim.game.game.othello;

import com.brim.game.game.board.GridDuel;
import java.util.*;

public final class OthelloGame extends GridDuel {
    public OthelloGame(String roomId, String difficulty) {
        super(8,difficulty);
        board[3][3]=board[4][4]=2; board[3][4]=board[4][3]=1;
    }
    @Override protected GridDuel fresh() { return new OthelloGame("",difficulty); }
    private List<Cell> flips(Move move,int value) {
        List<Cell> result=new ArrayList<>();
        if (board[move.row()][move.column()]!=0) return result;
        for (int dr=-1;dr<=1;dr++) for (int dc=-1;dc<=1;dc++) {
            if (dr==0&&dc==0) continue;
            List<Cell> ray=new ArrayList<>();
            int r=move.row()+dr,c=move.column()+dc;
            while (inside(r,c)&&board[r][c]==3-value) { ray.add(new Cell(r,c)); r+=dr;c+=dc; }
            if (inside(r,c)&&board[r][c]==value) result.addAll(ray);
        }
        return result;
    }
    @Override protected List<Move> moves(int value) {
        List<Move> legal=new ArrayList<>();
        for (int r=0;r<size;r++) for (int c=0;c<size;c++) {
            Move m=new Move(r,c); if (!flips(m,value).isEmpty()) legal.add(m);
        }
        return legal;
    }
    @Override protected void apply(Move move) {
        for (Cell cell:flips(move,side)) board[cell.row()][cell.column()]=side;
        board[move.row()][move.column()]=side;
        side=3-side;
        if (moves(side).isEmpty()) {
            passedPlayer=player(side); side=3-side;
            if (moves(side).isEmpty()) {
                int difference=count(1)-count(2);
                status=difference==0?"DRAW":"FINISHED";
                winner=difference==0?null:player(difference>0?1:2);
            }
        }
    }
    @Override protected List<Move> candidates() {
        List<Move> legal=moves(side);
        legal.sort(Comparator.<Move>comparingInt(m -> corner(m.row(),m.column())?100:0).reversed());
        return legal;
    }
    private boolean corner(int r,int c) { return (r==0||r==7)&&(c==0||c==7); }
    @Override protected int evaluate(int value) {
        int score=0;
        for (int r=0;r<size;r++) for (int c=0;c<size;c++) if (board[r][c]!=0) {
            int weight=corner(r,c)?120:(r==0||c==0||r==7||c==7)?12:1;
            score+=board[r][c]==value?weight:-weight;
        }
        return score+8*(moves(value).size()-moves(3-value).size());
    }
}
