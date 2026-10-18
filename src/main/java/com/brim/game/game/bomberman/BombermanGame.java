package com.brim.game.game.bomberman;

import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import java.util.*;

/** 100ms server ticks; clients submit intentions, never positions or blast results. */
public final class BombermanGame implements TurnBasedGame {
    public record Input(String action,String direction) {}
    public record Avatar(String id,String name,int row,int column,boolean alive,int range,int capacity) {}
    public record Bomb(int row,int column,String owner,int range,int explodesAt) {}
    public record Cell(int row,int column) {}
    public record State(int[][] board,List<Avatar> players,List<Bomb> bombs,List<Cell> flames,Map<String,String> items,
                        String status,String winner,int ticksLeft,long revision) {}
    private static final int HEIGHT=11,WIDTH=13,LIMIT=1800;
    private static final int[][] SPAWNS={{1,1},{9,11},{1,11},{9,1}};
    private static final Map<String,int[]> DIRECTIONS=Map.of("UP",new int[]{-1,0},"DOWN",new int[]{1,0},"LEFT",new int[]{0,-1},"RIGHT",new int[]{0,1},"STOP",new int[]{0,0});
    private static final class Actor {
        String id,name,direction="STOP";int r,c,range=2,capacity=1,nextMove,inputExpires;boolean alive=true,plant;
    }
    private final int[][] board=new int[HEIGHT][WIDTH];
    private final LinkedHashMap<String,Actor> actors=new LinkedHashMap<>();
    private final Map<Cell,Bomb> bombs=new LinkedHashMap<>();
    private final Map<Cell,Integer> flames=new HashMap<>();
    private final Map<Cell,String> items=new HashMap<>();
    private final Random random;
    private int tick;
    private String status="IN_PROGRESS",winner;
    public BombermanGame(String roomId,String difficulty) {
        random=new Random(roomId.hashCode());
        for (int r=0;r<HEIGHT;r++) for (int c=0;c<WIDTH;c++) {
            if (r==0||c==0||r==HEIGHT-1||c==WIDTH-1||(r%2==0&&c%2==0)) board[r][c]=1;
            else if (random.nextDouble()<.65) board[r][c]=2;
        }
        for (int[] spawn:SPAWNS) {
            board[spawn[0]][spawn[1]]=0;
            for (int[] d:DIRECTIONS.values()) if (inside(spawn[0]+d[0],spawn[1]+d[1])&&board[spawn[0]+d[0]][spawn[1]+d[1]]!=1)
                board[spawn[0]+d[0]][spawn[1]+d[1]]=0;
        }
    }
    @Override public synchronized void setPlayers(List<com.brim.game.model.dto.Player> players) {
        actors.clear();
        for (var player:players) {
            if (actors.size()==4) break;
            int[] spawn=SPAWNS[actors.size()];
            Actor a=new Actor();a.id=player.getId();a.name=player.getUsername();a.r=spawn[0];a.c=spawn[1];actors.put(a.id,a);
        }
    }
    @Override public void setPlayer1(String id) {}
    @Override public void setPlayer2(String id) {}
    @Override public Class<?> moveType() { return Input.class; }
    @Override public boolean realtime() { return true; }
    @Override public String getCurrentPlayerId() { return null; }
    @Override public synchronized boolean isGameOver() { return !status.equals("IN_PROGRESS"); }
    @Override public synchronized String getWinner() { return winner; }
    @Override public void handleEvent(MessageType type,Object input) {}
    @Override public Object generateBotMove() { return null; }
    @Override public synchronized boolean makeMove(String id,Object input) {
        Actor actor=actors.get(id);
        if (actor==null||!actor.alive||isGameOver()||!(input instanceof Input command)||command.action()==null) return false;
        if (command.action().equals("BOMB")) { actor.plant=true;return true; }
        if (command.action().equals("MOVE")&&DIRECTIONS.containsKey(command.direction()==null?"":command.direction())) {
            actor.direction=command.direction();actor.inputExpires=tick+5;return true;
        }
        return false;
    }
    @Override public synchronized void retainPlayers(Set<String> members) {
        actors.values().stream().filter(a->!members.contains(a.id)).forEach(a->{a.alive=false;a.direction="STOP";});
    }
    @Override public synchronized void tick() {
        if (isGameOver()) return;
        tick++;
        flames.entrySet().removeIf(entry->entry.getValue()<=tick);
        for (Actor a:actors.values()) if (a.alive) {
            if (a.id.startsWith("bot-")) bot(a);
            else if (tick>=a.inputExpires) a.direction="STOP";
            // A player standing in an existing flame cannot escape by moving this tick.
            if (flames.containsKey(new Cell(a.r,a.c))) { a.alive=false;continue; }
            if (a.plant) { plant(a);a.plant=false; }
            if (tick>=a.nextMove) {
                int[] d=DIRECTIONS.get(a.direction);
                int r=a.r+d[0],c=a.c+d[1];
                if (walkable(r,c)) { a.r=r;a.c=c;a.nextMove=tick+2; }
            }
            Cell cell=new Cell(a.r,a.c);
            String item=items.remove(cell);
            if ("RANGE".equals(item)) a.range=Math.min(5,a.range+1);
            if ("CAPACITY".equals(item)) a.capacity=Math.min(4,a.capacity+1);
        }
        ArrayDeque<Cell> due=new ArrayDeque<>();
        bombs.forEach((cell,bomb)->{if (bomb.explodesAt()<=tick) due.add(cell);});
        while (!due.isEmpty()) explode(due.remove(),due);
        for (Actor a:actors.values()) if (flames.containsKey(new Cell(a.r,a.c))) a.alive=false;
        List<Actor> alive=actors.values().stream().filter(a->a.alive).toList();
        if (actors.size()>=2&&alive.size()<=1) {
            winner=alive.size()==1?alive.get(0).id:null;status=winner==null?"DRAW":"FINISHED";
        } else if (tick>=LIMIT) status="DRAW";
    }
    private void plant(Actor a) {
        Cell cell=new Cell(a.r,a.c);
        if (!bombs.containsKey(cell)&&bombs.values().stream().filter(b->b.owner().equals(a.id)).count()<a.capacity)
            bombs.put(cell,new Bomb(a.r,a.c,a.id,a.range,tick+20));
    }
    private void explode(Cell cell,ArrayDeque<Cell> due) {
        Bomb bomb=bombs.remove(cell);
        if (bomb==null) return;
        ignite(cell,due);
        for (int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for (int distance=1;distance<=bomb.range();distance++) {
            int r=cell.row()+d[0]*distance,c=cell.column()+d[1]*distance;
            if (!inside(r,c)||board[r][c]==1) break;
            Cell target=new Cell(r,c);
            if (board[r][c]==2) {
                board[r][c]=0;
                // Items are revealed after this explosion; a later explosion can destroy them.
                flames.put(target,tick+5);
                if (random.nextDouble()<.35) items.put(target,random.nextBoolean()?"RANGE":"CAPACITY");
                break;
            }
            boolean chain=bombs.containsKey(target);
            ignite(target,due);
            if (chain) break;
        }
    }
    private void ignite(Cell cell,ArrayDeque<Cell> due) {
        flames.put(cell,tick+5);items.remove(cell);
        if (bombs.containsKey(cell)) due.add(cell);
    }
    private boolean inside(int r,int c) { return r>=0&&c>=0&&r<HEIGHT&&c<WIDTH; }
    private boolean walkable(int r,int c) { return inside(r,c)&&board[r][c]==0&&!bombs.containsKey(new Cell(r,c)); }
    private boolean danger(int r,int c) {
        if (flames.containsKey(new Cell(r,c))) return true;
        for (Bomb bomb:bombs.values()) {
            int distance=Math.abs(r-bomb.row())+Math.abs(c-bomb.column());
            if (distance>bomb.range()||(r!=bomb.row()&&c!=bomb.column())) continue;
            int dr=Integer.signum(r-bomb.row()),dc=Integer.signum(c-bomb.column());boolean blocked=false;
            for (int i=1;i<distance;i++) if (board[bomb.row()+dr*i][bomb.column()+dc*i]!=0) {blocked=true;break;}
            if (!blocked) return true;
        }
        return false;
    }
    private void bot(Actor a) {
        if (tick<a.nextMove) return;
        List<String> options=new ArrayList<>(List.of("UP","DOWN","LEFT","RIGHT","STOP"));
        Collections.shuffle(options,random);
        String best="STOP";int bestScore=Integer.MIN_VALUE;
        for (String direction:options) {
            int[] d=DIRECTIONS.get(direction);int r=a.r+d[0],c=a.c+d[1];
            if (!direction.equals("STOP")&&!walkable(r,c)) continue;
            int score=danger(r,c)?-1000:0;
            if (!direction.equals("STOP")) score+=5;
            for (Bomb bomb:bombs.values()) score+=Math.abs(r-bomb.row())+Math.abs(c-bomb.column());
            if (score>bestScore) {bestScore=score;best=direction;}
        }
        a.direction=best;
        if (!danger(a.r,a.c)&&tick%10==0) {
            boolean near=false;
            for (int[] d:DIRECTIONS.values()) if (inside(a.r+d[0],a.c+d[1])&&board[a.r+d[0]][a.c+d[1]]==2) near=true;
            if (near) a.plant=true;
        }
    }
    @Override public synchronized State getState() {
        Map<String,String> itemState=new HashMap<>();items.forEach((cell,type)->itemState.put(cell.row()+","+cell.column(),type));
        return new State(Arrays.stream(board).map(int[]::clone).toArray(int[][]::new),actors.values().stream()
                .map(a->new Avatar(a.id,a.name,a.r,a.c,a.alive,a.range,a.capacity)).toList(),List.copyOf(bombs.values()),
                List.copyOf(flames.keySet()),Map.copyOf(itemState),status,winner,Math.max(0,LIMIT-tick),tick);
    }
}
