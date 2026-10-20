package com.brim.game.game.warlords;

import com.brim.game.game.matchthree.MatchThreeGame.Cell;
import java.util.*;

/** Eight by eight combat board. Units retain their resource in the last decimal digit. */
public final class CombatBoard {
    public static final int SIZE=8, NORMAL=0, ROW=1, COLUMN=2, AREA=3, CROSS=4, AREA_RADIUS=1;
    public record Swap(int row,int column,int toRow,int toColumn) {}
    public record Special(Cell cell,int tile) {public int kind(){return special(tile);}}
    public record Wave(int[] counts,List<Cell> cells,int[][] cleared,int[][] fallen,int combo,
                       List<Special> created,List<Special> activated,int[][] before) {}
    public record Resolution(List<Wave> waves,boolean reshuffled,int[][] swapped) {}
    public record Projection(int[][] board,List<int[]> waves) {}
    public record Saved(int[][] cells,long randomState) {}
    private record Run(List<Cell> cells,boolean horizontal) {}
    private record Clear(int[] counts,List<Cell> cells,List<Special> created,List<Special> activated,int[][] before) {}
    private final BoardRandom rng;
    private int[][] cells;

    public CombatBoard(long seed) {rng=new BoardRandom(seed);regenerate();}
    private CombatBoard(Saved saved) {
        if(saved==null||saved.cells()==null||saved.cells().length!=SIZE||Arrays.stream(saved.cells())
                .anyMatch(row->row==null||row.length!=SIZE||Arrays.stream(row).anyMatch(value->!validTile(value))))
            throw new IllegalArgumentException("Bàn chiến dịch đã lưu không hợp lệ.");
        rng=new BoardRandom(0);rng.state=saved.randomState()&((1L<<48)-1);cells=copy(saved.cells());
    }
    public Saved save(){return new Saved(snapshot(),rng.state);}
    public static CombatBoard restore(Saved saved){return new CombatBoard(saved);}
    public static int resource(int tile){return tile%10;}
    public static int special(int tile){return tile/10;}
    public static int tile(int resource,int kind) {
        if(resource<1||resource>6||kind<NORMAL||kind>CROSS)throw new IllegalArgumentException("Invalid combat tile");
        return resource+kind*10;
    }
    private static boolean validTile(int tile){return tile>0&&resource(tile)>=1&&resource(tile)<=6&&special(tile)<=CROSS;}

    /** Same LCG as java.util.Random, with explicit state for exact crash recovery. */
    private static final class BoardRandom extends Random {
        private long state;
        BoardRandom(long seed){super(0);state=(seed^0x5DEECE66DL)&((1L<<48)-1);}
        @Override protected int next(int bits){state=(state*0x5DEECE66DL+0xBL)&((1L<<48)-1);return (int)(state>>>(48-bits));}
    }
    public int[][] snapshot(){return copy(cells);}
    public List<Swap> legalMoves(){return legalMoves(cells);}
    public static List<Swap> legalMoves(int[][] board) {
        List<Swap> result=new ArrayList<>();
        for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++)for(int[] d:new int[][]{{0,1},{1,0}}) {
            int y=r+d[0],x=c+d[1];
            if(y>=SIZE||x>=SIZE||board[r][c]<=0||board[y][x]<=0)continue;
            if(special(board[r][c])>0||special(board[y][x])>0){result.add(new Swap(r,c,y,x));continue;}
            if(board[r][c]==board[y][x])continue;
            swap(board,r,c,y,x);
            boolean legal;
            try{legal=!runs(board).isEmpty();}finally{swap(board,r,c,y,x);}
            if(legal)result.add(new Swap(r,c,y,x));
        }
        return result;
    }

    /** Uses exactly the visible rules, leaving empty spaces empty without consuming refill RNG. */
    public static Projection project(int[][] visible,Swap move,boolean cascades) {
        int[][] projected=copy(visible);
        if(!validSwap(projected,move))return new Projection(projected,List.of());
        swap(projected,move.row(),move.column(),move.toRow(),move.toColumn());
        List<int[]> waves=new ArrayList<>();
        for(int combo=1;combo<=30;combo++) {
            Clear clear=clear(projected,combo==1?activation(projected,move):List.of(),combo==1?preferred(move):List.of());
            if(clear==null)break;
            waves.add(clear.counts());fall(projected,null);
            if(!cascades)break;
        }
        if(waves.isEmpty())return new Projection(copy(visible),List.of());
        return new Projection(projected,List.copyOf(waves));
    }
    public int[] preview(Swap move) {
        var projection=project(cells,move,false);
        return projection.waves().isEmpty()?new int[7]:projection.waves().get(0).clone();
    }
    public Resolution resolve(Swap move) {
        if(!validSwap(cells,move))return null;
        swap(cells,move.row(),move.column(),move.toRow(),move.toColumn());
        int[][] swapped=snapshot();
        List<Wave> waves=new ArrayList<>();
        for(int combo=1;combo<=30;combo++) {
            Clear clear=clear(cells,combo==1?activation(cells,move):List.of(),combo==1?preferred(move):List.of());
            if(clear==null)break;
            int[][] cleared=snapshot();fall(cells,rng);
            waves.add(new Wave(clear.counts(),clear.cells(),cleared,snapshot(),combo,clear.created(),clear.activated(),clear.before()));
        }
        if(waves.isEmpty()){swap(cells,move.row(),move.column(),move.toRow(),move.toColumn());return null;}
        boolean reshuffled=!runs(cells).isEmpty()||legalMoves(cells).isEmpty();
        if(reshuffled)regenerate();
        return new Resolution(List.copyOf(waves),reshuffled,swapped);
    }
    private static boolean validSwap(int[][] board,Swap move) {
        return move!=null&&inside(move.row(),move.column())&&inside(move.toRow(),move.toColumn())
                &&Math.abs(move.row()-move.toRow())+Math.abs(move.column()-move.toColumn())==1
                &&board[move.row()][move.column()]>0&&board[move.toRow()][move.toColumn()]>0
                &&(board[move.row()][move.column()]!=board[move.toRow()][move.toColumn()]
                    ||special(board[move.row()][move.column()])>0);
    }
    private static List<Cell> preferred(Swap move) {
        return List.of(new Cell(move.toRow(),move.toColumn()),new Cell(move.row(),move.column()));
    }
    private static List<Cell> activation(int[][] board,Swap move) {
        var endpoints=preferred(move);
        // Trading any special with an adjacent unit consumes both units and triggers each special once.
        return endpoints.stream().anyMatch(p->special(board[p.row()][p.column()])>0)?endpoints:List.of();
    }

    private static List<Run> runs(int[][] board) {
        List<Run> result=new ArrayList<>();
        for(boolean horizontal:new boolean[]{true,false})for(int line=0;line<SIZE;line++) {
            int start=0;
            while(start<SIZE) {
                int value=resource(horizontal?board[line][start]:board[start][line]),end=start+1;
                while(value>0&&end<SIZE&&resource(horizontal?board[line][end]:board[end][line])==value)end++;
                if(value>0&&end-start>=3) {
                    List<Cell> run=new ArrayList<>();
                    for(int i=start;i<end;i++)run.add(horizontal?new Cell(line,i):new Cell(i,line));
                    result.add(new Run(List.copyOf(run),horizontal));
                }
                start=end;
            }
        }
        return result;
    }

    /** A single clear plan powers actual play, special activation and the visible-only bot. */
    private static Clear clear(int[][] board,List<Cell> activatedSwap,List<Cell> preferred) {
        List<Run> runs=runs(board);
        if(runs.isEmpty()&&activatedSwap.isEmpty())return null;
        int[][] before=copy(board);
        Set<Cell> matched=new LinkedHashSet<>();for(Run run:runs)matched.addAll(run.cells());
        List<Special> created=new ArrayList<>();
        Set<Cell> protectedCells=new HashSet<>(),visited=new HashSet<>();
        for(Cell first:matched) {
            if(!visited.add(first))continue;
            Set<Cell> group=new LinkedHashSet<>();Deque<Cell> queue=new ArrayDeque<>();queue.add(first);
            while(!queue.isEmpty()) {
                Cell cell=queue.remove();group.add(cell);
                for(int[] d:new int[][]{{0,1},{1,0},{0,-1},{-1,0}}) {
                    Cell next=new Cell(cell.row()+d[0],cell.column()+d[1]);
                    if(matched.contains(next)&&resource(board[next.row()][next.column()])==resource(board[first.row()][first.column()])&&visited.add(next))queue.add(next);
                }
            }
            List<Run> groupRuns=runs.stream().filter(run->group.contains(run.cells().get(0))).toList();
            int kind=NORMAL;
            if(groupRuns.stream().anyMatch(run->run.cells().size()>=5))kind=CROSS;
            else if(groupRuns.stream().filter(Run::horizontal).anyMatch(h->groupRuns.stream().filter(v->!v.horizontal()).anyMatch(v->v.cells().stream().anyMatch(h.cells()::contains))))kind=AREA;
            else {
                var four=groupRuns.stream().filter(run->run.cells().size()>=4).findFirst();
                if(four.isPresent())kind=four.get().horizontal()?ROW:COLUMN;
            }
            if(kind==NORMAL)continue;
            // The moved destination wins; cascades choose the first cell in row/column order.
            List<Cell> candidates=new ArrayList<>(preferred);
            group.stream().sorted(Comparator.comparingInt(Cell::row).thenComparingInt(Cell::column)).forEach(candidates::add);
            Cell anchor=candidates.stream().filter(group::contains).filter(p->special(board[p.row()][p.column()])==NORMAL)
                    .filter(p->!activatedSwap.contains(p)).findFirst().orElse(null);
            if(anchor!=null) {
                protectedCells.add(anchor);
                created.add(new Special(anchor,tile(resource(board[anchor.row()][anchor.column()]),kind)));
            }
        }

        Set<Cell> cleared=new LinkedHashSet<>();Deque<Cell> pending=new ArrayDeque<>();
        for(Cell cell:matched)addClear(board,cell,protectedCells,cleared,pending);
        for(Cell cell:activatedSwap)addClear(board,cell,protectedCells,cleared,pending);
        List<Special> activated=new ArrayList<>();Set<Cell> triggered=new HashSet<>();
        while(!pending.isEmpty()) {
            Cell cell=pending.remove();
            if(!triggered.add(cell))continue;
            int value=board[cell.row()][cell.column()],kind=special(value);
            activated.add(new Special(cell,value));
            if(kind==ROW||kind==CROSS)for(int c=0;c<SIZE;c++)addClear(board,new Cell(cell.row(),c),protectedCells,cleared,pending);
            if(kind==COLUMN||kind==CROSS)for(int r=0;r<SIZE;r++)addClear(board,new Cell(r,cell.column()),protectedCells,cleared,pending);
            if(kind==AREA)for(int r=cell.row()-AREA_RADIUS;r<=cell.row()+AREA_RADIUS;r++)for(int c=cell.column()-AREA_RADIUS;c<=cell.column()+AREA_RADIUS;c++)
                addClear(board,new Cell(r,c),protectedCells,cleared,pending);
        }
        int[] counts=new int[7];
        for(Cell cell:cleared){counts[resource(board[cell.row()][cell.column()])]++;board[cell.row()][cell.column()]=0;}
        // A newly forged unit remains dormant for this wave, including overlapping chained blasts.
        for(Special unit:created)board[unit.cell().row()][unit.cell().column()]=unit.tile();
        return new Clear(counts,List.copyOf(cleared),List.copyOf(created),List.copyOf(activated),before);
    }
    private static void addClear(int[][] board,Cell cell,Set<Cell> protectedCells,Set<Cell> cleared,Deque<Cell> pending) {
        if(!inside(cell.row(),cell.column())||protectedCells.contains(cell)||board[cell.row()][cell.column()]<=0||!cleared.add(cell))return;
        if(special(board[cell.row()][cell.column()])>0)pending.add(cell);
    }

    private void regenerate() {
        for(int attempt=0;attempt<200;attempt++) {
            cells=new int[SIZE][SIZE];
            for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++) {
                int value;do{value=1+rng.nextInt(6);}while((r>=2&&cells[r-1][c]==value&&cells[r-2][c]==value)||(c>=2&&cells[r][c-1]==value&&cells[r][c-2]==value));
                cells[r][c]=value;
            }
            if(!legalMoves(cells).isEmpty())return;
        }
        throw new IllegalStateException("Cannot generate combat board");
    }
    private static void fall(int[][] board,BoardRandom refill) {
        for(int c=0;c<SIZE;c++) {
            int target=SIZE-1;
            for(int r=SIZE-1;r>=0;r--)if(board[r][c]>0)board[target--][c]=board[r][c];
            while(target>=0)board[target--][c]=refill==null?0:1+refill.nextInt(6);
        }
    }
    private static void swap(int[][] board,int r,int c,int y,int x){int value=board[r][c];board[r][c]=board[y][x];board[y][x]=value;}
    private static boolean inside(int r,int c){return r>=0&&c>=0&&r<SIZE&&c<SIZE;}
    public static int[][] copy(int[][] board){return Arrays.stream(board).map(int[]::clone).toArray(int[][]::new);}
}
