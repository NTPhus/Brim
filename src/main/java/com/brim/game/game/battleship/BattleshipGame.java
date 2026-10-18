package com.brim.game.game.battleship;

import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import java.util.*;

public final class BattleshipGame implements TurnBasedGame {
    public static final List<Integer> FLEET=List.of(5,4,3,3,2);
    private final String[] players=new String[2];
    private final int[][][] ships=new int[2][10][10];
    private final boolean[][][] shots=new boolean[2][10][10];
    private final int[] placed=new int[2];
    private int turn;
    private String phase="PLACEMENT", winner;
    public BattleshipGame(String roomId,String difficulty) {}
    public record Move(String action,Integer row,Integer column,Boolean horizontal) {}
    public record State(String phase,String currentPlayer,String winner,int[][] ownBoard,int[][] targetBoard,
                        int placedShips,int opponentPlacedShips,List<Integer> fleet,Integer nextShipLength,int remainingOwn,int remainingOpponent) {}
    @Override public Class<?> moveType() { return Move.class; }
    @Override public synchronized void setPlayer1(String id) { players[0]=id; }
    @Override public synchronized void setPlayer2(String id) { players[1]=id; }
    @Override public boolean privateState() { return true; }
    @Override public synchronized Object getState() { return Map.of("phase",phase,"privateState",true); }
    @Override public synchronized State getState(String viewer) {
        int p=index(viewer);
        if (p<0) throw new IllegalArgumentException("Not a player");
        int[][] own=new int[10][10], target=new int[10][10];
        for (int r=0;r<10;r++) for (int c=0;c<10;c++) {
            // 0 unknown/water, 1 own ship, 2 miss, 3 hit. Unhit enemy ships never leave the server.
            own[r][c]=shots[1-p][r][c]?(ships[p][r][c]>0?3:2):(ships[p][r][c]>0?1:0);
            target[r][c]=shots[p][r][c]?(ships[1-p][r][c]>0?3:2):0;
        }
        return new State(phase,getCurrentPlayerId(),winner,own,target,placed[p],placed[1-p],FLEET,
                placed[p]<5?FLEET.get(placed[p]):null,remaining(p),remaining(1-p));
    }
    @Override public synchronized String getCurrentPlayerId() {
        return phase.equals("PLACEMENT")?players[placed[0]<5?0:1]:players[turn];
    }
    @Override public synchronized boolean isGameOver() { return phase.equals("FINISHED"); }
    @Override public synchronized String getWinner() { return winner; }
    @Override public void handleEvent(MessageType type,Object payload) {}
    private int index(String id) {
        if (id==null) return -1;
        return id.equals(players[0])?0:id.equals(players[1])?1:-1;
    }
    @Override public synchronized boolean makeMove(String id,Object input) {
        int p=index(id);
        if (p<0||players[0]==null||players[1]==null||isGameOver()||!(input instanceof Move m)||m.action()==null) return false;
        if (phase.equals("PLACEMENT")) {
            if (placed[p]>=5) return false;
            if (m.action().equals("AUTO_PLACE")) autoPlace(p);
            else if (m.action().equals("PLACE")&&m.row()!=null&&m.column()!=null&&m.horizontal()!=null) {
                if (!place(p,m.row(),m.column(),m.horizontal())) return false;
            } else return false;
            if (placed[0]==5&&placed[1]==5) { phase="BATTLE";turn=0; }
            return true;
        }
        if (!m.action().equals("FIRE")||p!=turn||m.row()==null||m.column()==null
                ||!inside(m.row(),m.column())||shots[p][m.row()][m.column()]) return false;
        shots[p][m.row()][m.column()]=true;
        if (remaining(1-p)==0) { phase="FINISHED";winner=id; } else turn=1-p;
        return true;
    }
    private boolean place(int p,int r,int c,boolean horizontal) {
        int length=FLEET.get(placed[p]);
        for (int i=0;i<length;i++) {
            int y=r+(horizontal?0:i),x=c+(horizontal?i:0);
            if (!inside(y,x)||ships[p][y][x]!=0) return false;
        }
        for (int i=0;i<length;i++) ships[p][r+(horizontal?0:i)][c+(horizontal?i:0)]=placed[p]+1;
        placed[p]++;
        return true;
    }
    private void autoPlace(int p) {
        // Reset partial placements; a finite shuffled scan always has a fallback row layout.
        for (int[] row:ships[p]) Arrays.fill(row,0);
        placed[p]=0;
        Random rng=new Random();
        for (int ship=0;ship<5;ship++) {
            boolean done=false;
            for (int attempt=0;attempt<200&&!done;attempt++) done=place(p,rng.nextInt(10),rng.nextInt(10),rng.nextBoolean());
            if (!done) {
                for (int[] row:ships[p]) Arrays.fill(row,0);
                placed[p]=0;
                for (int i=0;i<5;i++) place(p,i*2,0,true);
                return;
            }
        }
    }
    private boolean inside(int r,int c) { return r>=0&&c>=0&&r<10&&c<10; }
    private int remaining(int p) {
        int count=0;
        for (int r=0;r<10;r++) for (int c=0;c<10;c++) if (ships[p][r][c]>0&&!shots[1-p][r][c]) count++;
        return count;
    }
    @Override public synchronized Move generateBotMove() {
        if (isGameOver()) return null;
        int p=index(getCurrentPlayerId());
        if (p<0) return null;
        if (phase.equals("PLACEMENT")) return new Move("AUTO_PLACE",null,null,null);
        // Target adjacent to known hits, without consulting any unobserved enemy cell.
        for (int r=0;r<10;r++) for (int c=0;c<10;c++) if (shots[p][r][c]&&ships[1-p][r][c]>0) {
            for (int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int y=r+d[0],x=c+d[1];
                if (inside(y,x)&&!shots[p][y][x]) return new Move("FIRE",y,x,null);
            }
        }
        List<Move> legal=new ArrayList<>();
        for (int r=0;r<10;r++) for (int c=0;c<10;c++) if (!shots[p][r][c]) legal.add(new Move("FIRE",r,c,null));
        return legal.isEmpty()?null:legal.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(legal.size()));
    }
}
