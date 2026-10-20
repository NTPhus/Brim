package com.brim.game.game.warlords;

import com.brim.game.game.matchthree.MatchThreeGame;
import com.brim.game.game.matchthree.MatchThreeGame.Cell;
import java.util.*;

/** Shared match detection comes from BRIM's puzzle engine; this board has six combat resources. */
public final class CombatBoard {
    public record Swap(int row,int column,int toRow,int toColumn) {}
    public record Wave(int[] counts,List<Cell> cells,int[][] cleared,int[][] fallen,int combo) {}
    public record Resolution(List<Wave> waves,boolean reshuffled,int[][] swapped) {}
    public record Projection(int[][] board,List<int[]> waves) {}
    public record Saved(int[][] cells,long randomState) {}
    private final BoardRandom rng;
    private int[][] cells;
    public CombatBoard(long seed) {rng=new BoardRandom(seed);regenerate();}
    private CombatBoard(Saved saved) {
        if(saved.cells()==null||saved.cells().length!=8||Arrays.stream(saved.cells()).anyMatch(row->row==null||row.length!=8||Arrays.stream(row).anyMatch(value->value<1||value>6)))
            throw new IllegalArgumentException("Bàn chiến dịch đã lưu không hợp lệ.");
        rng=new BoardRandom(0);rng.state=saved.randomState()&((1L<<48)-1);cells=copy(saved.cells());
    }
    public Saved save(){return new Saved(snapshot(),rng.state);}
    public static CombatBoard restore(Saved saved){return new CombatBoard(saved);}
    /** Same LCG as java.util.Random, with explicit state for exact crash recovery. */
    private static final class BoardRandom extends Random {
        private long state;
        BoardRandom(long seed){super(0);state=(seed^0x5DEECE66DL)&((1L<<48)-1);}
        @Override protected int next(int bits){state=(state*0x5DEECE66DL+0xBL)&((1L<<48)-1);return (int)(state>>>(48-bits));}
    }
    public int[][] snapshot() {return copy(cells);}
    public List<Swap> legalMoves() { return legalMoves(cells); }
    public static List<Swap> legalMoves(int[][] cells) {
        List<Swap> result=new ArrayList<>();
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)for(int[] d:new int[][]{{0,1},{1,0}}) {
            int y=r+d[0],x=c+d[1];if(y>=8||x>=8||cells[r][c]<=0||cells[y][x]<=0||cells[r][c]==cells[y][x])continue;
            swap(cells,r,c,y,x);
            boolean legal;
            try {legal=!MatchThreeGame.findMatches(cells).isEmpty();}finally{swap(cells,r,c,y,x);}
            if(legal)result.add(new Swap(r,c,y,x));
        }
        return result;
    }
    /** Evaluate only visible tiles. Empty spaces stay empty: AI never samples future refills or RNG. */
    public static Projection project(int[][] visible,Swap move,boolean cascades) {
        int[][] projected=copy(visible);
        swap(projected,move.row(),move.column(),move.toRow(),move.toColumn());
        List<int[]> waves=new ArrayList<>();
        for(int wave=0;wave<30;wave++) {
            Set<Cell> matches=MatchThreeGame.findMatches(projected);
            if(matches.isEmpty())break;
            int[] count=new int[7];
            for(Cell cell:matches){count[projected[cell.row()][cell.column()]]++;projected[cell.row()][cell.column()]=0;}
            waves.add(count);
            for(int c=0;c<8;c++) {
                int bottom=7;
                for(int r=7;r>=0;r--)if(projected[r][c]>0)projected[bottom--][c]=projected[r][c];
                while(bottom>=0)projected[bottom--][c]=0;
            }
            if(!cascades)break;
        }
        return new Projection(projected,List.copyOf(waves));
    }
    public int[] preview(Swap move) {
        swap(move.row(),move.column(),move.toRow(),move.toColumn());
        try {
            int[] count=new int[7];
            for(Cell cell:MatchThreeGame.findMatches(cells))count[cells[cell.row()][cell.column()]]++;
            return count;
        } finally {swap(move.row(),move.column(),move.toRow(),move.toColumn());}
    }
    public Resolution resolve(Swap move) {
        if(!inside(move.row(),move.column())||!inside(move.toRow(),move.toColumn())
                ||Math.abs(move.row()-move.toRow())+Math.abs(move.column()-move.toColumn())!=1)return null;
        swap(move.row(),move.column(),move.toRow(),move.toColumn());
        Set<Cell> matches=MatchThreeGame.findMatches(cells);
        if(matches.isEmpty()) {swap(move.row(),move.column(),move.toRow(),move.toColumn());return null;}
        int[][] swapped=snapshot();
        List<Wave> waves=new ArrayList<>();int combo=0;
        while(!matches.isEmpty()&&combo<30) {
            int[] count=new int[7];
            for(Cell cell:matches) {count[cells[cell.row()][cell.column()]]++;cells[cell.row()][cell.column()]=0;}
            int[][] cleared=snapshot();
            fall();
            waves.add(new Wave(count,List.copyOf(matches),cleared,snapshot(),++combo));
            matches=MatchThreeGame.findMatches(cells);
        }
        boolean reshuffled=!matches.isEmpty()||!MatchThreeGame.hasLegalSwap(cells);
        if(reshuffled)regenerate();
        return new Resolution(List.copyOf(waves),reshuffled,swapped);
    }
    private void regenerate() {
        for(int attempt=0;attempt<200;attempt++) {
            cells=new int[8][8];
            for(int r=0;r<8;r++)for(int c=0;c<8;c++) {
                int v;do{v=1+rng.nextInt(6);}while((r>=2&&cells[r-1][c]==v&&cells[r-2][c]==v)||(c>=2&&cells[r][c-1]==v&&cells[r][c-2]==v));
                cells[r][c]=v;
            }
            if(MatchThreeGame.hasLegalSwap(cells))return;
        }
        throw new IllegalStateException("Cannot generate combat board");
    }
    private void fall() {
        for(int c=0;c<8;c++) {
            int target=7;
            for(int r=7;r>=0;r--)if(cells[r][c]!=0)cells[target--][c]=cells[r][c];
            while(target>=0)cells[target--][c]=1+rng.nextInt(6);
        }
    }
    private void swap(int r,int c,int y,int x) {swap(cells,r,c,y,x);}
    private static void swap(int[][] cells,int r,int c,int y,int x) {int v=cells[r][c];cells[r][c]=cells[y][x];cells[y][x]=v;}
    private static boolean inside(int r,int c){return r>=0&&c>=0&&r<8&&c<8;}
    public static int[][] copy(int[][] board){return Arrays.stream(board).map(int[]::clone).toArray(int[][]::new);}
}
