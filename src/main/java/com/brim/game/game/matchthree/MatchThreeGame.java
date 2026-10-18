package com.brim.game.game.matchthree;

import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;

/** Seeded, server-authoritative puzzle. Animation events never drive the rules. */
public final class MatchThreeGame implements TurnBasedGame {
    public record Cell(int row,int column) {}
    public record Level(int number,long seed,int moves,int target,int collectType,int collectTarget,List<Cell> blocks,List<Cell> covers) {}
    public record Move(String action,Integer row,Integer column,Integer toRow,Integer toColumn,Integer level) {}
    public record Event(String type,List<Cell> cells,int[][] board,int points) {}
    public record State(int[][] board,int[][] covers,int level,int unlocked,int movesLeft,int score,int target,
                        int collectType,int collected,int collectTarget,String levelStatus,String status,
                        String currentPlayer,String winner,List<Event> events) {}
    private static final List<Level> LEVELS=loadLevels();
    private String player,status="IN_PROGRESS",levelStatus="PLAYING";
    private int[][] board,covers;
    private int level=1,unlocked=1,movesLeft,score,collected;
    private Random rng;
    private List<Event> events=List.of();
    public MatchThreeGame(String roomId,String difficulty) { start(1); }
    private static List<Level> loadLevels() {
        try (var input=MatchThreeGame.class.getResourceAsStream("/games/match-three-levels.json")) {
            if (input==null) throw new IllegalStateException("Missing match-3 levels");
            return new ObjectMapper().readValue(input,new TypeReference<List<Level>>() {});
        } catch (java.io.IOException error) { throw new IllegalStateException("Invalid match-3 levels",error); }
    }
    @Override public synchronized void configure(Map<String,Object> settings) {
        Object saved=settings.get("match3Unlocked");
        unlocked=saved instanceof Number n?Math.max(1,Math.min(10,n.intValue())):1;
        Object selected=settings.get("match3Level");
        start(selected instanceof Number n?Math.max(1,Math.min(unlocked,n.intValue())):1);
    }
    @Override public synchronized Map<String,Object> checkpoint() { return Map.of("match3Unlocked",unlocked,"match3Level",level); }
    private void start(int number) {
        level=number; Level spec=LEVELS.get(number-1);
        rng=new Random(spec.seed()); score=0;collected=0;movesLeft=spec.moves();
        status="IN_PROGRESS";levelStatus="PLAYING";events=List.of();
        covers=new int[8][8];
        for (Cell cell:spec.covers()) covers[cell.row()][cell.column()]=1;
        regenerate();
    }
    private void regenerate() {
        Level spec=LEVELS.get(level-1);
        for (int attempt=0;attempt<200;attempt++) {
            board=new int[8][8];
            for (Cell cell:spec.blocks()) board[cell.row()][cell.column()]=-1;
            for (int r=0;r<8;r++) for (int c=0;c<8;c++) if (board[r][c]!=-1) {
                int value;
                do { value=1+rng.nextInt(6); }
                while ((c>=2&&board[r][c-1]==value&&board[r][c-2]==value)||(r>=2&&board[r-1][c]==value&&board[r-2][c]==value));
                board[r][c]=value;
            }
            if (hasLegalSwap(board)) return;
        }
        throw new IllegalStateException("Cannot generate playable board");
    }
    @Override public Class<?> moveType() { return Move.class; }
    @Override public synchronized void setPlayer1(String id) { player=id; }
    @Override public void setPlayer2(String id) {}
    @Override public synchronized String getCurrentPlayerId() { return player; }
    @Override public synchronized boolean isGameOver() { return status.equals("FINISHED"); }
    @Override public synchronized String getWinner() { return isGameOver()?player:null; }
    @Override public void handleEvent(MessageType type,Object payload) {}
    @Override public Object generateBotMove() { return null; }
    @Override public synchronized State getState() {
        Level spec=LEVELS.get(level-1);
        return new State(copy(board),copy(covers),level,unlocked,movesLeft,score,spec.target(),spec.collectType(),collected,
                spec.collectTarget(),levelStatus,status,player,getWinner(),copyEvents(events));
    }
    private static List<Event> copyEvents(List<Event> source) {
        return source.stream().map(e->new Event(e.type(),e.cells(),copy(e.board()),e.points())).toList();
    }
    @Override public synchronized boolean makeMove(String id,Object input) {
        if (id==null||!id.equals(player)||isGameOver()||!(input instanceof Move move)||move.action()==null) return false;
        if (move.action().equals("RETRY")) { start(level);return true; }
        if (move.action().equals("LEVEL")&&move.level()!=null&&move.level()>=1&&move.level()<=unlocked) { start(move.level());return true; }
        if (move.action().equals("NEXT")&&levelStatus.equals("WON")&&level<10) { start(level+1);return true; }
        if (!move.action().equals("SWAP")||!levelStatus.equals("PLAYING")||move.row()==null||move.column()==null||move.toRow()==null||move.toColumn()==null) return false;
        int r=move.row(),c=move.column(),y=move.toRow(),x=move.toColumn();
        if (!inside(r,c)||!inside(y,x)||Math.abs(r-y)+Math.abs(c-x)!=1||board[r][c]<=0||board[y][x]<=0) return false;
        swap(board,r,c,y,x);
        Set<Cell> matched=findMatches(board);
        if (matched.isEmpty()) { swap(board,r,c,y,x);return false; }
        movesLeft--;
        List<Event> output=new ArrayList<>();
        output.add(new Event("SWAP",List.of(new Cell(r,c),new Cell(y,x)),copy(board),0));
        Level spec=LEVELS.get(level-1);
        int cascade=0;
        while (!matched.isEmpty()&&cascade<50) {
            cascade++;
            int points=matched.size()*10*cascade;
            for (Cell cell:matched) {
                if (board[cell.row()][cell.column()]==spec.collectType()) collected++;
                board[cell.row()][cell.column()]=0;
                covers[cell.row()][cell.column()]=0;
            }
            score+=points;
            output.add(new Event("CLEAR",List.copyOf(matched),copy(board),points));
            collapse();
            output.add(new Event("FALL",List.of(),copy(board),0));
            matched=findMatches(board);
        }
        if (!matched.isEmpty()||!hasLegalSwap(board)) {
            regenerate(); output.add(new Event("RESHUFFLE",List.of(),copy(board),0));
        }
        boolean coverLeft=Arrays.stream(covers).flatMapToInt(Arrays::stream).anyMatch(v->v>0);
        if (score>=spec.target()&&collected>=spec.collectTarget()&&!coverLeft) {
            levelStatus="WON";unlocked=Math.max(unlocked,Math.min(10,level+1));
            if (level==10) status="FINISHED";
        } else if (movesLeft==0) levelStatus="LOST";
        events=List.copyOf(output);
        return true;
    }
    private void collapse() {
        // Blocks divide a column into independent gravity segments.
        for (int c=0;c<8;c++) {
            int end=7;
            while (end>=0) {
                if (board[end][c]==-1) { end--;continue; }
                int top=end;
                while (top>0&&board[top-1][c]!=-1) top--;
                int write=end;
                for (int read=end;read>=top;read--) if (board[read][c]>0) board[write--][c]=board[read][c];
                while (write>=top) board[write--][c]=1+rng.nextInt(6);
                end=top-1;
            }
        }
    }
    public static Set<Cell> findMatches(int[][] cells) {
        Set<Cell> result=new LinkedHashSet<>();
        for (int r=0;r<cells.length;r++) for (int c=0;c<cells[r].length;c++) {
            int value=cells[r][c]; if (value<=0) continue;
            if (c+2<cells[r].length&&cells[r][c+1]==value&&cells[r][c+2]==value)
                for (int x=c;x<cells[r].length&&cells[r][x]==value;x++) result.add(new Cell(r,x));
            if (r+2<cells.length&&cells[r+1][c]==value&&cells[r+2][c]==value)
                for (int y=r;y<cells.length&&cells[y][c]==value;y++) result.add(new Cell(y,c));
        }
        return result;
    }
    public static boolean hasLegalSwap(int[][] cells) {
        for (int r=0;r<cells.length;r++) for (int c=0;c<cells[r].length;c++) for (int[] d:new int[][]{{1,0},{0,1}}) {
            int y=r+d[0],x=c+d[1];
            if (y>=cells.length||x>=cells[r].length||cells[r][c]<=0||cells[y][x]<=0||cells[r][c]==cells[y][x]) continue;
            swap(cells,r,c,y,x);
            boolean match;
            try { match=!findMatches(cells).isEmpty(); } finally { swap(cells,r,c,y,x); }
            if (match) return true;
        }
        return false;
    }
    private static void swap(int[][] cells,int r,int c,int y,int x) { int value=cells[r][c];cells[r][c]=cells[y][x];cells[y][x]=value; }
    private static boolean inside(int r,int c) { return r>=0&&c>=0&&r<8&&c<8; }
    private static int[][] copy(int[][] cells) { return Arrays.stream(cells).map(int[]::clone).toArray(int[][]::new); }
}
