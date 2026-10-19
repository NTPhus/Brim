package com.brim.game.game.vietnam;

import com.brim.game.game.tank.TankGame.Wall;
import com.brim.game.game.tactical.*;
import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/** Server-authoritative solo action campaign, driven by the shared 100ms runtime. */
public final class VietnamGame implements TurnBasedGame {
    public record Input(double dx,double dy,double aimX,double aimY,boolean fire,boolean crouch,String action,long commandSeq) {
        public Input(double dx,double dy,double aimX,double aimY,boolean fire,boolean crouch,String action) { this(dx,dy,aimX,aimY,fire,crouch,action,0); }
    }
    public record Unit(String id,String name,double x,double y,int hp,boolean ally,boolean tank,double angle,double hullAngle,int maxHealth,HeavyVehicle.View tacticalView) {}
    public record Area(double x,double y,double width,double height,String kind,String label,int layer) {
        boolean contains(double px,double py) { return px>=x && px<=x+width && py>=y && py<=y+height; }
    }
    public record Objective(String label,double x,double y,int layer,String kind) {}
    public record Shot(double x,double y,double tx,double ty,int life,boolean blast) {}
    public record State(VietnamCampaign.Chapter chapter,List<Unit> units,List<Wall> walls,List<Area> areas,
                        List<Objective> objectives,int objective,int layer,int ammo,int reload,int ticksLeft,
                        List<Shot> shots,String status,String winner,String notice,long revision,int reserveAmmo,long lastCommandSeq,
                        List<SupplyView> supplies,List<ProjectileView> projectiles,String rulesVersion) {}
    private static final class Actor {
        String id,name; double x,y,angle,hullAngle; int hp=100,maxHealth=100,cooldown,ammo=12,reserve=24,reload; boolean ally,tank;
        HeavyVehicle vehicle;
        Unit snapshot(boolean own) { return new Unit(id,name,x,y,hp,ally,tank,angle,hullAngle,maxHealth,own&&vehicle!=null?vehicle.view():null); }
    }
    private final TacticalBalance balance=TacticalBalance.standard();
    public record SupplyView(String id,double x,double y,int layer,String kind,double quantity) {}
    public record ProjectileView(long id,double x,double y,double angle) {}
    private static final class Crate {String id,kind;double x,y;int layer;double quantity;}
    private static final class Shell {long id;Actor owner;double x,y,tx,ty,angle;}
    private final List<Crate> supplies=new ArrayList<>();
    private final List<Shell> shells=new ArrayList<>();
    private record PendingCommand(long sequence,String action) {}
    private final ArrayDeque<PendingCommand> commands=new ArrayDeque<>();
    private long acceptedCommandSeq;
    private long shellId,lastCommandSeq;
    private int reserveAmmo=24;
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
        Actor a=new Actor();a.id=id;a.name=name;a.x=x;a.y=y;a.ally=ally;a.tank=tank;a.hp=tank?(ally?balance.playerHp():balance.enemyHp()):100;a.maxHealth=a.hp;
        if(tank)a.vehicle=new HeavyVehicle(balance,id==null?1:id.hashCode());actors.add(a);return a;
    }
    private void setup() {
        actors.clear();walls.clear();areas.clear();objectives.clear();shots.clear();objective=0;layer=0;tick=0;reload=0;status="IN_PROGRESS";notice="";
        boolean tank=chapter==3;ammo=tank?1:12;reserveAmmo=24;lastCommandSeq=0;acceptedCommandSeq=0;commands.clear();shellId=0;supplies.clear();shells.clear();
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
        addCrate("depot-a",200,850,0,tank?"SHELLS":"RIFLE",tank?4:24);
        addCrate("depot-b",chapter==2?710:650,chapter==2?350:380,chapter==2?1:0,tank?"SHELLS":"RIFLE",tank?4:24);
        if(tank){addCrate("fuel",700,650,0,"FUEL",750);addCrate("repair",220,760,0,"REPAIR",1);}
        for(Actor actor:actors) if(walls.stream().anyMatch(w->w.blocksMovement()&&w.intersects(actor.x,actor.y,actor.tank?23:12)))
            throw new IllegalStateException("Actor spawns inside cover: "+actor.id);
        input=new Input(0,0,0,0,false,false,"");inputUntil=0;
    }
    private void addCrate(String id,double x,double y,int layer,String kind,int quantity){
        Crate c=new Crate();c.id=id;c.x=x;c.y=y;c.layer=layer;c.kind=kind;c.quantity=quantity;supplies.add(c);
    }
    @Override public boolean privateState(){return true;}
    @Override public Object getState(String viewerId){return Objects.equals(player,viewerId)?getState():Map.of();}
    @Override public Class<?> moveType() { return Input.class; }
    @Override public boolean realtime() { return true; }
    @Override public synchronized boolean makeMove(String id,Object value) {
        if(!Objects.equals(id,player)||isGameOver()||!(value instanceof Input i)||!Double.isFinite(i.dx)||!Double.isFinite(i.dy)||!Double.isFinite(i.aimX)||!Double.isFinite(i.aimY)) return false;
        String action="";
        if(i.action!=null&&!i.action.isEmpty()&&i.commandSeq>acceptedCommandSeq&&i.commandSeq<=9_007_199_254_740_991L&&commands.size()<32){acceptedCommandSeq=i.commandSeq;commands.addLast(new PendingCommand(i.commandSeq,i.action));}
        input=new Input(Math.max(-1,Math.min(1,i.dx)),Math.max(-1,Math.min(1,i.dy)),Math.max(0,Math.min(1600,i.aimX)),Math.max(0,Math.min(1000,i.aimY)),i.fire,i.crouch,action,i.commandSeq);
        inputUntil=tick+5; return true;
    }
    private boolean passable(double x,double y,boolean tank) {
        if(x<30||y<30||x>1570||y>970) return false;
        if(chapter==3&&objective<2&&x>=1350)return false;
        if(layer==1) return areas.stream().anyMatch(a->a.layer==1&&a.contains(x,y));
        return walls.stream().noneMatch(w->w.blocksMovement()&&w.intersects(x,y,tank?23:12));
    }
    private void move(Actor a,double dx,double dy,double speed) {
        double len=Math.hypot(dx,dy); if(len<.001)return;
        double nx=a.x+dx/len*speed,ny=a.y+dy/len*speed;
        if(passable(nx,a.y,a.tank))a.x=nx;
        if(passable(a.x,ny,a.tank))a.y=ny;
    }
    private boolean sight(Actor a,Actor b) { return TacticalGeometry.clear(walls,a.x,a.y,b.x,b.y,BattleObstacle::blocksVision); }
    @Override public synchronized void tick() {
        if(isGameOver()||actors.isEmpty())return; tick++;
        shots.replaceAll(s->new Shot(s.x,s.y,s.tx,s.ty,s.life-1,s.blast));shots.removeIf(s->s.life<=0);
        Actor me=actors.get(0);
        if(tick>inputUntil) input=new Input(0,0,me.x+100,me.y,false,false,"");
        PendingCommand command=commands.pollFirst();
        if(command!=null){lastCommandSeq=command.sequence;input=new Input(input.dx,input.dy,input.aimX,input.aimY,input.fire,input.crouch,command.action,command.sequence);}
        if(reload>0&&--reload==0){int transferred=Math.min(12-ammo,reserveAmmo);ammo+=transferred;reserveAmmo-=transferred;}
        if("RELOAD".equals(input.action)&&!me.tank&&reload==0&&ammo<12&&reserveAmmo>0)reload=30;
        if("REPAIR".equals(input.action)&&me.tank)notice=me.vehicle.startRepair(me.hp,me.maxHealth)?"Đang sửa xe; giữ yên và ngừng bắn.":"Không thể sửa: cần dừng xe, có bộ sửa và HP dưới 70%.";
        if("INTERACT".equals(input.action))interact(me);
        if(isGameOver())return;
        if(me.tank)moveHeavy(me,-input.dy,input.dx,Math.atan2(input.aimY-me.y,input.aimX-me.x),input.fire);
        else {move(me,input.dx,input.dy,input.crouch?3.5:6.5);me.angle=Math.atan2(input.aimY-me.y,input.aimX-me.x);}
        if(me.cooldown>0)me.cooldown--;
        if(input.fire&&me.cooldown==0&&layer==0) {
            if(me.tank)fire(me,input.aimX,input.aimY);
            else if(ammo>0&&reload==0){fire(me,input.aimX,input.aimY);ammo--;}
        }
        for(Actor a:actors) if(a!=me&&a.hp>0) {
            if(a.cooldown>0)a.cooldown--;
            if(!a.tank&&a.reload>0&&--a.reload==0){int transferred=Math.min(12-a.ammo,a.reserve);a.ammo+=transferred;a.reserve-=transferred;}
            if(!a.tank&&a.ammo==0&&a.reserve>0&&a.reload==0)a.reload=30;
            if(layer==1) continue;
            Actor target=actors.stream().filter(b->b.hp>0&&a.ally!=b.ally&&sight(a,b)&&Math.hypot(a.x-b.x,a.y-b.y)<=balance.sightRange()).min(Comparator.comparingDouble(b->Math.hypot(a.x-b.x,a.y-b.y))).orElse(null);
            if(a.tank) {
                double aim=target==null?a.angle:Math.atan2(target.y-a.y,target.x-a.x);
                double bearing=Math.atan2(me.y-a.y,me.x-a.x),diff=HeavyVehicle.wrap(bearing-a.hullAngle);
                moveHeavy(a,Math.hypot(a.x-me.x,a.y-me.y)>95&&Math.abs(diff)<.6?1:0,Math.abs(diff)<.05?0:Math.signum(diff),aim,false);
            } else if(a.ally && Math.hypot(a.x-me.x,a.y-me.y)>95)move(a,me.x-a.x,me.y-a.y,5.5);
            if(!a.ally&&target!=null&&Math.hypot(a.x-target.x,a.y-target.y)>220&&Math.hypot(a.x-target.x,a.y-target.y)<450)move(a,target.x-a.x,target.y-a.y,4);
            if(target!=null&&Math.hypot(a.x-target.x,a.y-target.y)<350&&sight(a,target)&&a.cooldown==0) {
                if(a.tank)fire(a,target.x,target.y);
                else if(a.ammo>0&&a.reload==0){fire(a,target.x,target.y);a.ammo--;}
            }
        }
        updateShells();
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
        Crate crate=supplies.stream().filter(c->c.layer==layer&&c.quantity>0&&Math.hypot(c.x-me.x,c.y-me.y)<65
                &&TacticalGeometry.clear(walls,me.x,me.y,c.x,c.y,BattleObstacle::blocksMovement)).findFirst().orElse(null);
        if(crate!=null){
            double taken=switch(crate.kind){
                case "SHELLS"->me.tank?me.vehicle.cannon().refill((int)crate.quantity):0;
                case "FUEL"->me.tank?me.vehicle.refuel(crate.quantity):0;
                case "REPAIR"->me.tank?me.vehicle.addRepairKits((int)crate.quantity):0;
                default->me.tank?0:Math.min((int)crate.quantity,60-reserveAmmo);
            };
            if(crate.kind.equals("RIFLE"))reserveAmmo+=(int)taken;
            crate.quantity-=taken;notice=taken>0?"Đã nhận tiếp tế; vật tư còn lại giữ trong hòm.":"Đã mang đủ hoặc không dùng được vật tư này.";return;
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
        if(shooter.tank){
            if(shooter.vehicle.repairing()||!shooter.vehicle.cannon().fire())return;
            double range=Math.min(balance.range(),Math.hypot(tx-shooter.x,ty-shooter.y));
            Shell shell=new Shell();shell.id=++shellId;shell.owner=shooter;shell.x=shooter.x;shell.y=shooter.y;
            shell.angle=shooter.vehicle.shotAngle(shooter.angle);shell.tx=shooter.x+Math.cos(shell.angle)*range;shell.ty=shooter.y+Math.sin(shell.angle)*range;
            shells.add(shell);return;
        }
        shooter.cooldown=shooter.ally?7:18;
        double distance=Math.min(shooter.tank?600:450,Math.hypot(tx-shooter.x,ty-shooter.y));
        double angle=Math.atan2(ty-shooter.y,tx-shooter.x);
        double x=shooter.x+Math.cos(angle)*distance,y=shooter.y+Math.sin(angle)*distance,hit=1;
        for(Wall w:walls)if(w.blocksProjectile())hit=Math.min(hit,w.hitFraction(shooter.x,shooter.y,x,y,3));
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
        if(a.vehicle!=null&&amount>0)a.vehicle.interruptRepair();
        if(!a.tank&&(trench||(a==actors.get(0)&&input.crouch)))amount=(amount+1)/2;
        a.hp=Math.max(0,a.hp-amount);
        if(a.hp==0&&a.vehicle!=null)a.vehicle.cannon().disable();
    }
    private void moveHeavy(Actor a,double throttle,double turn,double aim,boolean trigger){
        var motion=a.vehicle.advance(.1,throttle,turn,a.hullAngle,a.angle,aim);
        double nx=a.x+Math.cos((a.hullAngle+motion.hullAngle())*.5)*motion.distance(),ny=a.y+Math.sin((a.hullAngle+motion.hullAngle())*.5)*motion.distance();
        a.hullAngle=motion.hullAngle();a.angle=motion.turretAngle();
        if(passable(nx,ny,true)){a.vehicle.traveled(Math.hypot(nx-a.x,ny-a.y));a.x=nx;a.y=ny;}else a.vehicle.blocked();
        a.hp=a.vehicle.advanceRepair(.1,throttle!=0||turn!=0||trigger,a.hp,a.maxHealth);
    }
    private void updateShells(){
        var iterator=shells.iterator();
        while(iterator.hasNext()){
            Shell shell=iterator.next();double distance=Math.hypot(shell.tx-shell.x,shell.ty-shell.y),step=Math.min(distance,balance.projectileSpeed()*.1);
            double nx=distance<=step?shell.tx:shell.x+Math.cos(shell.angle)*step,ny=distance<=step?shell.ty:shell.y+Math.sin(shell.angle)*step;
            double hit=Double.POSITIVE_INFINITY;Actor direct=null;
            for(Wall w:walls)if(w.blocksProjectile())hit=Math.min(hit,w.hitFraction(shell.x,shell.y,nx,ny,5));
            for(Actor a:actors)if(a.tank&&a!=shell.owner&&a.hp>0){double t=TacticalGeometry.circleHit(shell.x,shell.y,nx,ny,a.x,a.y,26);if(t<hit){hit=t;direct=a;}}
            if(hit<=1||distance<=step){
                double fraction=Math.min(1,hit),x=shell.x+(nx-shell.x)*fraction,y=shell.y+(ny-shell.y)*fraction;
                for(Actor a:actors)if(a.hp>0&&(a==shell.owner||a.ally!=shell.owner.ally)&&TacticalGeometry.clear(walls,x,y,a.x,a.y,BattleObstacle::blocksBlast)){
                    double armor=a.tank?TacticalDamage.armor(a.hullAngle,a==direct?shell.x:x,a==direct?shell.y:y,a.x,a.y):1;
                    damage(a,(int)Math.round(TacticalDamage.radial(Math.hypot(a.x-x,a.y-y),balance.blastRadius(),balance.shellDamage())*armor));
                }
                shots.add(new Shot(x,y,x,y,6,true));iterator.remove();
            }else{shell.x=nx;shell.y=ny;}
        }
    }
    private boolean visible(Actor a){
        Actor me=actors.get(0);return a.ally||layer==0&&Math.hypot(a.x-me.x,a.y-me.y)<=balance.sightRange()&&sight(me,a);
    }
    @Override public synchronized State getState() {
        Actor me=actors.isEmpty()?null:actors.get(0);
        var weapon=me!=null&&me.vehicle!=null?me.vehicle.view().weapon():null;
        return new State(VietnamCampaign.chapter(chapter),actors.stream().filter(this::visible).map(a->a.snapshot(a==me)).toList(),
                walls.stream().map(w->{var copy=new Wall(w.getX(),w.getY(),w.getWidth(),w.getHeight());copy.setBlocksMovement(w.blocksMovement());copy.setBlocksVision(w.blocksVision());copy.setBlocksProjectile(w.blocksProjectile());copy.setBlocksBlast(w.blocksBlast());return copy;}).toList(),
                List.copyOf(areas),List.copyOf(objectives),objective,layer,weapon==null?ammo:weapon.state()==CannonState.Phase.READY?1:0,
                weapon==null?reload:(int)Math.ceil(weapon.remainingMs()/100.0),Math.max(0,6000-tick),
                shots.stream().filter(s->me!=null&&Math.hypot(s.x-me.x,s.y-me.y)<=balance.sightRange()
                    &&TacticalGeometry.clear(walls,me.x,me.y,s.x,s.y,BattleObstacle::blocksVision)).toList(),status,getWinner(),notice,tick,
                weapon==null?reserveAmmo:weapon.reserve(),lastCommandSeq,
                supplies.stream().map(c->new SupplyView(c.id,c.x,c.y,c.layer,c.kind,c.quantity)).toList(),
                shells.stream().filter(p->me!=null&&Math.hypot(p.x-me.x,p.y-me.y)<=balance.sightRange()
                    &&TacticalGeometry.clear(walls,me.x,me.y,p.x,p.y,BattleObstacle::blocksVision))
                    .map(p->new ProjectileView(p.id,p.x,p.y,p.angle)).toList(),balance.version());
    }
    @Override public synchronized String getWinner() {return isGameOver()&&objective==objectives.size()&& !actors.isEmpty()&&actors.get(0).hp>0?player:null;}
    @Override public synchronized boolean isGameOver() {return status.equals("FINISHED");}
    @Override public String getCurrentPlayerId() {return null;}
    @Override public void handleEvent(MessageType type,Object payload) {}
    @Override public Object generateBotMove() {return null;}
    @Override public synchronized void retainPlayers(Set<String> members) {if(player!=null&&!members.contains(player))status="FINISHED";}
}
