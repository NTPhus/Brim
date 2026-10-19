package com.brim.game.game.vietnam;

import com.brim.game.game.tank.TankGame.Wall;
import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/** Server-authoritative solo action campaign, driven by the shared 100ms runtime. */
public final class VietnamGame implements TurnBasedGame {
    public record Input(double dx,double dy,double aimX,double aimY,boolean fire,boolean crouch,String action) {}
    public record Unit(String id,String name,double x,double y,int hp,boolean ally,boolean tank,double angle) {}
    public record Area(double x,double y,double width,double height,String kind,String label,int layer) {
        boolean contains(double px,double py) { return px>=x && px<=x+width && py>=y && py<=y+height; }
    }
    public record Objective(String label,double x,double y,int layer,String kind) {}
    public record Shot(double x,double y,double tx,double ty,int life,boolean blast) {}
    public record State(VietnamCampaign.Chapter chapter,List<Unit> units,List<Wall> walls,List<Area> areas,
                        List<Objective> objectives,int objective,int layer,int ammo,int reload,int ticksLeft,
                        List<Shot> shots,String status,String winner,String notice,long revision) {}
    private static final class Actor {
        String id,name; double x,y,angle; int hp=100,cooldown; boolean ally,tank;
        Unit snapshot() { return new Unit(id,name,x,y,hp,ally,tank,angle); }
    }
    private final ToIntFunction<String> unlocked;
    private final BiConsumer<String,Integer> completion;
    private final List<Actor> actors=new ArrayList<>();
    private final List<Wall> walls=new ArrayList<>();
    private final List<Area> areas=new ArrayList<>();
    private final List<Objective> objectives=new ArrayList<>();
    private final List<Shot> shots=new ArrayList<>();
    private String player,status="IN_PROGRESS",notice="";
    private int chapter=1,objective,layer,tick,inputUntil,ammo=12,reload;
    private Input input=new Input(0,0,0,0,false,false,"");
    public VietnamGame(String room,String difficulty) { this(id->3,(id,stage)->{}); }
    public VietnamGame(ToIntFunction<String> unlocked,BiConsumer<String,Integer> completion) { this.unlocked=unlocked;this.completion=completion; }
    @Override public synchronized void setPlayer1(String id) { player=id; setup(); }
    @Override public void setPlayer2(String id) {}
    @Override public synchronized void configure(Map<String,Object> settings) {
        int requested=settings.get("vietnamChapter") instanceof Number n?n.intValue():1;
        chapter=Math.max(1,Math.min(Math.min(3,unlocked.applyAsInt(player)),requested)); setup();
    }
    private Actor add(String id,String name,double x,double y,boolean ally,boolean tank) {
        Actor a=new Actor();a.id=id;a.name=name;a.x=x;a.y=y;a.ally=ally;a.tank=tank;a.hp=tank?220:100;actors.add(a);return a;
    }
    private void setup() {
        actors.clear();walls.clear();areas.clear();objectives.clear();shots.clear();objective=0;layer=0;tick=0;reload=0;status="IN_PROGRESS";notice="";
        boolean tank=chapter==3;ammo=tank?5:12;
        add(player,VietnamCampaign.chapter(chapter).character(),120,800,true,tank);
        add("ally-1",tank?"Xe 843 · Bùi Quang Thận":"Đồng đội An (hư cấu)",85,860,true,tank);
        if(chapter!=3) add("ally-2","Đồng đội Bình (hư cấu)",160,870,true,false);
        if(chapter==1) {
            areas.add(new Area(75,350,95,540,"TRENCH","Hào tiếp cận",0));
            areas.add(new Area(75,350,650,85,"TRENCH","Chiến hào",0));
            areas.add(new Area(635,180,85,250,"TRENCH","Hào tiến công",0));
            for(int n=0;n<5;n++) walls.add(new Wall(300+n*210,520,100,75));
            walls.add(new Wall(820,170,180,95));walls.add(new Wall(1100,310,180,100));
            areas.add(new Area(840,185,130,60,"BUNKER","Công sự",0));
            objectives.add(new Objective("Tới điểm liên lạc trong chiến hào",400,390,0,"REACH"));
            objectives.add(new Objective("Chiếm công sự · dọn địch xung quanh, nhấn E",780,210,0,"CAPTURE"));
            objectives.add(new Objective("Đưa tổ chiến đấu tới điểm tập kết",1430,160,0,"REACH"));
            for(int n=0;n<6;n++) add("enemy-"+n,"Lính phòng thủ",750+n%3*180,290+n/3*150,false,false);
        } else if(chapter==2) {
            for(int n=0;n<5;n++) walls.add(new Wall(360+n*200,470+(n%2)*170,90,90));
            areas.add(new Area(180,720,70,70,"ENTRY","Cửa hầm A · E",0));
            areas.add(new Area(180,720,70,70,"ENTRY","Cửa hầm A · E",1));
            areas.add(new Area(180,740,1000,55,"TUNNEL","Trục địa đạo",1));
            areas.add(new Area(680,300,55,495,"TUNNEL","Hầm nhánh",1));
            areas.add(new Area(640,260,160,130,"ROOM","Kho cứu trợ",1));
            areas.add(new Area(1100,700,160,140,"ROOM","Bệnh xá",1));
            areas.add(new Area(1120,720,70,70,"ENTRY","Cửa hầm B · E",0));
            areas.add(new Area(1120,720,70,70,"ENTRY","Cửa hầm B · E",1));
            areas.add(new Area(400,715,100,100,"ROOM","Bếp giấu khói",1));
            objectives.add(new Objective("Vào địa đạo: tới cửa hầm A, nhấn E",215,755,1,"REACH"));
            objectives.add(new Objective("Lấy túi cứu trợ trong kho · E",710,320,1,"INTERACT"));
            objectives.add(new Objective("Chuyển cứu trợ tới bệnh xá · E",1180,760,1,"INTERACT"));
            objectives.add(new Objective("Ra cửa hầm B, trở lại mặt đất · E",1155,755,0,"REACH"));
            for(int n=0;n<4;n++) add("enemy-"+n,"Đội tuần tra",700+n*170,400,false,false);
        } else {
            areas.add(new Area(80,400,1400,240,"ROAD","Đại lộ · tuyến mô phỏng",0));
            areas.add(new Area(1380,200,150,580,"PALACE","Sân Dinh Độc Lập",0));
            for(int n=0;n<4;n++) {walls.add(new Wall(350+n*260,180,140,140));walls.add(new Wall(400+n*260,720,130,140));}
            walls.add(new Wall(1360,200,20,230));walls.add(new Wall(1360,610,20,170));
            areas.add(new Area(1360,430,30,180,"GATE","Cổng chính · tiếp cận và E",0));
            objectives.add(new Objective("Hội quân trên đại lộ",700,520,0,"REACH"));
            objectives.add(new Objective("Tiếp cận cổng Dinh · E",1330,520,0,"INTERACT"));
            objectives.add(new Objective("Tiến vào sân Dinh",1470,520,0,"REACH"));
        }
        input=new Input(0,0,0,0,false,false,"");inputUntil=0;
    }
    @Override public Class<?> moveType() { return Input.class; }
    @Override public boolean realtime() { return true; }
    @Override public synchronized boolean makeMove(String id,Object value) {
        if(!Objects.equals(id,player)||isGameOver()||!(value instanceof Input i)||!Double.isFinite(i.dx)||!Double.isFinite(i.dy)||!Double.isFinite(i.aimX)||!Double.isFinite(i.aimY)) return false;
        input=new Input(Math.max(-1,Math.min(1,i.dx)),Math.max(-1,Math.min(1,i.dy)),Math.max(0,Math.min(1600,i.aimX)),Math.max(0,Math.min(1000,i.aimY)),i.fire,i.crouch,i.action);
        inputUntil=tick+5; return true;
    }
    private boolean passable(double x,double y,boolean tank) {
        if(x<30||y<30||x>1570||y>970) return false;
        if(chapter==3&&objective<2&&x>=1350)return false;
        if(layer==1) return areas.stream().anyMatch(a->a.layer==1&&a.contains(x,y));
        return walls.stream().noneMatch(w->w.intersects(x,y,tank?23:12));
    }
    private void move(Actor a,double dx,double dy,double speed) {
        double len=Math.hypot(dx,dy); if(len<.001)return;
        double nx=a.x+dx/len*speed,ny=a.y+dy/len*speed;
        if(passable(nx,a.y,a.tank))a.x=nx;
        if(passable(a.x,ny,a.tank))a.y=ny;
    }
    private boolean sight(Actor a,Actor b) { return walls.stream().noneMatch(w->w.hitFraction(a.x,a.y,b.x,b.y,2)<=1); }
    @Override public synchronized void tick() {
        if(isGameOver()||actors.isEmpty())return; tick++;
        shots.replaceAll(s->new Shot(s.x,s.y,s.tx,s.ty,s.life-1,s.blast));shots.removeIf(s->s.life<=0);
        Actor me=actors.get(0);
        if(tick>inputUntil) input=new Input(0,0,me.x+100,me.y,false,false,"");
        if(reload>0&&--reload==0)ammo=me.tank?5:12;
        if("RELOAD".equals(input.action)&&reload==0)reload=me.tank?24:16;
        if("INTERACT".equals(input.action))interact(me);
        if(isGameOver())return;
        move(me,input.dx,input.dy,me.tank?14:input.crouch?5:10);
        me.angle=Math.atan2(input.aimY-me.y,input.aimX-me.x);
        if(me.cooldown>0)me.cooldown--;
        if(input.fire&&me.cooldown==0&&ammo>0&&reload==0&&layer==0) { fire(me,input.aimX,input.aimY);ammo--; }
        for(Actor a:actors) if(a!=me&&a.hp>0) {
            if(a.cooldown>0)a.cooldown--;
            if(layer==1) continue;
            Actor target=actors.stream().filter(b->b.hp>0&&a.ally!=b.ally).min(Comparator.comparingDouble(b->Math.hypot(a.x-b.x,a.y-b.y))).orElse(null);
            if(a.ally && Math.hypot(a.x-me.x,a.y-me.y)>95)move(a,me.x-a.x,me.y-a.y,9);
            if(!a.ally&&target!=null&&Math.hypot(a.x-target.x,a.y-target.y)>220&&Math.hypot(a.x-target.x,a.y-target.y)<450)move(a,target.x-a.x,target.y-a.y,4);
            if(target!=null&&Math.hypot(a.x-target.x,a.y-target.y)<350&&sight(a,target)&&a.cooldown==0)fire(a,target.x,target.y);
        }
        if(me.hp>0&&objective<objectives.size()) {
            Objective goal=objectives.get(objective);
            if(goal.kind.equals("REACH")&&goal.layer==layer&&Math.hypot(goal.x-me.x,goal.y-me.y)<65)advance();
        }
        if(me.hp<=0||tick>=6000) {status="FINISHED";notice="Nhiệm vụ chưa hoàn thành. Thử lại từ đầu màn.";}
        input=new Input(input.dx,input.dy,input.aimX,input.aimY,input.fire,input.crouch,"");
    }
    private void interact(Actor me) {
        if(objective>=objectives.size())return;
        Objective goal=objectives.get(objective);
        if(goal.layer==layer&&Math.hypot(goal.x-me.x,goal.y-me.y)<75&&!goal.kind.equals("REACH")) {
            if(goal.kind.equals("CAPTURE")&&actors.stream().anyMatch(a->!a.ally&&a.hp>0&&Math.hypot(a.x-goal.x,a.y-goal.y)<210)) {
                notice="Còn địch gần công sự. Phối hợp cùng đồng đội trước khi chiếm.";return;
            }
            advance();return;
        }
        if(chapter==2) for(Area a:areas) if(a.layer==layer&&a.kind.equals("ENTRY")&&Math.hypot(me.x-a.x-a.width/2,me.y-a.y-a.height/2)<65) {
            layer=1-layer;me.x=a.x+a.width/2;me.y=a.y+a.height/2;
            notice=layer==1?"Đã xuống địa đạo. Không thể bắn từ dưới hầm lên mặt đất.":"Đã trở lại mặt đất.";break;
        }
    }
    private void advance() {
        objective++;notice="Đã hoàn thành mục tiêu "+objective+"/"+objectives.size();
        if(objective==objectives.size()) {status="FINISHED";completion.accept(player,chapter);notice="Hoàn thành màn. Hồ sơ lịch sử và chương tiếp theo đã sẵn sàng.";}
    }
    private void fire(Actor shooter,double tx,double ty) {
        shooter.cooldown=shooter.ally?(shooter.tank?12:4):18;
        double distance=Math.min(shooter.tank?600:450,Math.hypot(tx-shooter.x,ty-shooter.y));
        double angle=Math.atan2(ty-shooter.y,tx-shooter.x);
        double x=shooter.x+Math.cos(angle)*distance,y=shooter.y+Math.sin(angle)*distance,hit=1;
        for(Wall w:walls)hit=Math.min(hit,w.hitFraction(shooter.x,shooter.y,x,y,3));
        x=shooter.x+(x-shooter.x)*hit;y=shooter.y+(y-shooter.y)*hit;
        if(!shooter.tank) {
            Actor victim=null;double first=1;
            for(Actor a:actors) if(a.hp>0&&a.ally!=shooter.ally) {
                double vx=x-shooter.x,vy=y-shooter.y,len=vx*vx+vy*vy;
                double t=len==0?0:Math.max(0,Math.min(1,((a.x-shooter.x)*vx+(a.y-shooter.y)*vy)/len));
                if(Math.hypot(a.x-shooter.x-vx*t,a.y-shooter.y-vy*t)<18&&t<=first) {first=t;victim=a;}
            }
            if(victim!=null) {x=shooter.x+(x-shooter.x)*first;y=shooter.y+(y-shooter.y)*first;damage(victim,shooter.ally?34:12);}
        } else for(Actor a:actors) if(a.hp>0&&(a==shooter||a.ally!=shooter.ally)) {
            int amount=(int)Math.ceil(80*Math.max(0,1-Math.hypot(a.x-x,a.y-y)/110));if(amount>0)damage(a,amount);
        }
        shots.add(new Shot(shooter.x,shooter.y,x,y,3,shooter.tank));
    }
    private void damage(Actor a,int amount) {
        boolean trench=areas.stream().anyMatch(z->z.layer==0&&z.kind.equals("TRENCH")&&z.contains(a.x,a.y));
        if(trench||(a==actors.get(0)&&input.crouch))amount=(amount+1)/2;
        a.hp=Math.max(0,a.hp-amount);
    }
    @Override public synchronized State getState() {return new State(VietnamCampaign.chapter(chapter),actors.stream().map(Actor::snapshot).toList(),List.copyOf(walls),List.copyOf(areas),List.copyOf(objectives),objective,layer,ammo,reload,Math.max(0,6000-tick),List.copyOf(shots),status,getWinner(),notice,tick);}
    @Override public synchronized String getWinner() {return isGameOver()&&objective==objectives.size()&& !actors.isEmpty()&&actors.get(0).hp>0?player:null;}
    @Override public synchronized boolean isGameOver() {return status.equals("FINISHED");}
    @Override public String getCurrentPlayerId() {return null;}
    @Override public void handleEvent(MessageType type,Object payload) {}
    @Override public Object generateBotMove() {return null;}
    @Override public synchronized void retainPlayers(Set<String> members) {if(player!=null&&!members.contains(player))status="FINISHED";}
}
