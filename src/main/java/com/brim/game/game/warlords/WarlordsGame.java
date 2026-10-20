package com.brim.game.game.warlords;

import com.brim.game.game.matchthree.MatchThreeGame.Cell;
import com.brim.game.game.warlords.WarlordsProfile.Claim;
import com.brim.game.model.dto.MessageType;
import com.brim.game.model.dto.Player;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import com.brim.game.service.WarlordsCampaignArchive;
import java.util.*;

public final class WarlordsGame implements TurnBasedGame {
    public record Move(String action,Integer row,Integer column,Integer toRow,Integer toColumn,
                       String option,Integer chapter,String encounterId,Long revision) {}
    public record Skill(String id,String name,int cost,String description) {}
    public static final List<Skill> SKILLS=List.of(
        new Skill("STRIKE","Phá trận",30,"Gây 28 sát thương, tăng theo cấp binh pháp trong chiến dịch."),
        new Skill("HEAL","Hồi quân",25,"Hồi 30 HP, tăng theo cấp binh pháp trong chiến dịch."),
        new Skill("GUARD","Thiết bích",20,"Nhận 40 khiên, tối đa 80.")
    );
    public record Fighter(String id,String name,int hp,int maxHp,int energy,int shield,int attack,int skillRank,
                          int gold,int experience,boolean bot,int missedTurns,WarlordsHeroes.Hero hero) {}
    public record Effects(int damage,int absorbed,int healing,int energy,int shield,int gold,int experience,int combo) {}
    public record Event(String type,String actor,String text,List<Cell> cells,int[][] board,Effects effects) {}
    public record Reward(String playerId,int gold,int experience) {}
    public record SavedCampaign(int version,String playerId,long seed,String phase,String status,String winner,String encounterId,
                                long revision,int turn,int turnNumber,int chapter,long remainingTurnMs,CombatBoard.Saved board,
                                List<Fighter> fighters,WarlordsProfile profile,List<Reward> rewards,Map<String,Claim> pendingClaims,String notice,WarlordsCampaign.Chapter chapterSpec) {}
    public record State(String mode,String phase,String status,String winner,String currentPlayer,String encounterId,
                        long revision,long turnDeadline,int turnNumber,int[][] board,List<Fighter> fighters,
                        WarlordsCampaign.Chapter chapter,List<WarlordsCampaign.Chapter> chapters,List<Skill> skills,
                        WarlordsProfile profile,List<Event> events,List<Reward> rewards,boolean rewardsPending,
                        boolean practice,String notice,List<WarlordsHeroes.Hero> heroes) {}
    private static final class Unit {
        String id,name;int hp,maxHp,energy=10,shield,attack,skillRank,gold,experience,missedTurns;
        WarlordsHeroes.Hero hero;
        Fighter snapshot(){return new Fighter(id,name,hp,maxHp,energy,shield,attack,skillRank,gold,experience,id.startsWith("bot-"),missedTurns,hero);}
        WarlordsBot.Stats stats(){return new WarlordsBot.Stats(hp,maxHp,energy,shield,attack,skillRank);}
    }
    private final boolean duel;
    private final WarlordsProfile.Store profiles;
    private long seed;
    private WarlordsCampaignArchive archive;
    private String roomId;
    private long generation;
    private String player1,player2,name1,name2;
    private String phase="MAP",status="IN_PROGRESS",winner,encounterId=UUID.randomUUID().toString(),notice="";
    private WarlordsProfile profile;
    private WarlordsCampaign.Chapter chapterSpec;
    private CombatBoard board;
    private final Unit[] units=new Unit[2];
    private int turn,turnNumber,chapter;
    private long revision,turnDeadline,botReadyAt;
    private List<Event> events=List.of();
    private List<Reward> rewards=List.of();
    private final Map<String,Claim> pendingClaims=new LinkedHashMap<>();

    public WarlordsGame(String roomId,boolean duel,WarlordsProfile.Store profiles) {
        this(roomId,duel,profiles,java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }
    public WarlordsGame(String roomId,boolean duel,WarlordsProfile.Store profiles,long seed) {
        this.duel=duel;this.profiles=profiles;this.seed=seed;
    }
    public WarlordsGame(String roomId,WarlordsProfile.Store profiles,WarlordsCampaignArchive archive) {
        this(roomId,false,profiles);this.roomId=roomId;this.archive=archive;
    }
    @Override public Class<?> moveType(){return Move.class;}
    @Override public synchronized void setPlayer1(String id){player1=id;name1=id;}
    @Override public synchronized void setPlayer2(String id){player2=id;name2=id;}
    @Override public synchronized void setPlayers(List<Player> players) {
        if(players.isEmpty()||(duel&&players.size()<2))throw new IllegalArgumentException("Thiếu người chơi.");
        player1=players.get(0).getId();name1=players.get(0).getUsername();
        if(duel){player2=players.get(1).getId();name2=players.get(1).getUsername();start(0);}
        else {
            profile=profiles.load(player1);phase="MAP";
            if(archive!=null) {
                var lease=archive.acquire(player1,roomId);generation=lease.generation();
                if(lease.saved()!=null){restore(lease.saved(),true);profile=profiles.load(player1);}
                archive.transact(player1,roomId,generation,()->true,this::save);
            }
        }
    }
    @Override public synchronized String getCurrentPlayerId(){return phase.equals("BATTLE")?units[turn].id:player1;}
    @Override public synchronized boolean isGameOver(){return status.equals("FINISHED");}
    @Override public synchronized String getWinner(){return winner;}
    @Override public void handleEvent(MessageType type,Object payload){}
    private boolean member(String id){return id!=null&&(id.equals(player1)||(duel&&id.equals(player2)));}
    private boolean validToken(Move move){return move.revision()!=null&&move.revision()==revision&&encounterId.equals(move.encounterId());}
    @Override public synchronized boolean makeMove(String id,Object input) {
        if(archive==null)return applyMove(id,input);
        var before=save();
        try{return archive.transact(player1,roomId,generation,()->applyMove(id,input),this::save);}
        catch(RuntimeException failure){recover(before);throw failure;}
    }
    private boolean applyMove(String id,Object input) {
        // Campaign NPC commands are generated by the bounded bot worker, not accepted as room members.
        boolean npc=!duel&&units[1]!=null&&units[1].id.equals(id)&&phase.equals("BATTLE");
        if((!member(id)&&!npc)||isGameOver()||!(input instanceof Move move)||move.action()==null||!validToken(move))return false;
        if(move.action().equals("CLAIM")&&!pendingClaims.isEmpty()&&member(id)) {
            retryClaims();revision++;return true;
        }
        if(!pendingClaims.isEmpty())return false;
        if(!duel&&id.equals(player1)) {
            if(move.action().equals("SELECT_HERO")&&phase.equals("MAP")) {
                profile=profiles.selectHero(player1,move.option());events=List.of();notice="Đã chọn tướng. Xuất quân để bắt đầu trận mới.";revision++;return true;
            }
            if(move.action().equals("MAP")&&!phase.equals("BATTLE")) {
                profile=profiles.load(player1);phase="MAP";events=List.of();notice="";revision++;return true;
            }
            if(move.action().equals("UPGRADE")&&phase.equals("MAP")) {
                profile=profiles.upgrade(player1,move.option());notice="Nâng cấp thành công. Chỉ số mới áp dụng từ trận tiếp theo.";revision++;return true;
            }
            if(move.action().equals("START")&&!phase.equals("BATTLE")&&move.chapter()!=null) {
                profile=profiles.load(player1);
                if(move.chapter()<1||move.chapter()>profile.unlocked())return false;
                start(move.chapter());revision++;return true;
            }
            if(move.action().equals("RETREAT")&&phase.equals("BATTLE")) {
                profile=profiles.load(player1);phase="MAP";winner=null;events=List.of();notice="Đã rút quân. Trận bỏ dở không nhận vàng hoặc kinh nghiệm.";revision++;return true;
            }
        }
        if(!phase.equals("BATTLE"))return false;
        if(move.action().equals("RESIGN")&&duel&&member(id)) {
            finish(id.equals(player1)?1:0,"Đối thủ đã đầu hàng.");revision++;return true;
        }
        if(!id.equals(getCurrentPlayerId()))return false;
        List<Event> output=new ArrayList<>();
        if(move.action().equals("SKILL")) {
            if(!useSkill(move.option(),output))return false;
        } else if(move.action().equals("SWAP")&&move.row()!=null&&move.column()!=null&&move.toRow()!=null&&move.toColumn()!=null) {
            var resolution=board.resolve(new CombatBoard.Swap(move.row(),move.column(),move.toRow(),move.toColumn()));
            if(resolution==null)return false;
            Unit actor=units[turn],enemy=units[1-turn];
            output.add(new Event("SWAP",actor.id,"",List.of(new Cell(move.row(),move.column()),
                    new Cell(move.toRow(),move.toColumn())),resolution.swapped(),null));
            for(var wave:resolution.waves()) {
                Effects effect=applyWave(wave.counts(),wave.combo());
                output.add(new Event("CLEAR",actor.id,describe(effect),wave.cells(),wave.cleared(),effect));
                output.add(new Event("FALL",actor.id,"",List.of(),wave.fallen(),null));
                if(enemy.hp==0)break;
            }
            if(resolution.reshuffled())output.add(new Event("RESHUFFLE",actor.id,"Bàn hết nước, đã đổi thế trận.",List.of(),board.snapshot(),null));
        } else return false;
        units[turn].missedTurns=0;
        events=List.copyOf(output);notice="";revision++;
        finishTurn();
        return true;
    }
    private void start(int number) {
        chapterSpec=number==0?null:WarlordsCampaign.chapter(number);
        chapter=number;encounterId=UUID.randomUUID().toString();winner=null;status="IN_PROGRESS";phase="BATTLE";
        turn=0;turnNumber=1;events=List.of();rewards=List.of();pendingClaims.clear();notice="";
        board=new CombatBoard(seed+revision*104729L+number*7919L);
        units[0]=unit(player1,name1,duel?140:profile.maxHp(),duel?5:profile.attack(),duel?0:profile.skill());
        equip(units[0],duel?heroFor(player1):WarlordsHeroes.saved(profile.heroId()));
        if(duel){units[1]=unit(player2,name2,140,5,0);equip(units[1],heroFor(player2));}
        else {
            var spec=chapterSpec;
            units[1]=unit("bot-warlord-"+number,spec.warlord(),spec.enemyHp(),spec.enemyAttack(),(number-1)/4);
        }
        turnDeadline=System.currentTimeMillis()+45_000;
        botReadyAt=System.currentTimeMillis()+900;
    }
    private WarlordsHeroes.Hero heroFor(String id) {
        if(id.startsWith("bot-"))return WarlordsHeroes.saved("DINH_LIEN");
        WarlordsProfile stored=profiles.load(id);
        return WarlordsHeroes.saved(stored==null?null:stored.heroId());
    }
    private void equip(Unit unit,WarlordsHeroes.Hero hero) {
        unit.hero=hero;
        if(!duel) {
            unit.name=hero.name();unit.hp=unit.maxHp+=hero.hpBonus();unit.attack+=hero.attackBonus();
            unit.skillRank+=hero.skillBonus();unit.energy=hero.startingEnergy();unit.shield=hero.startingShield();
        }
    }
    private Unit unit(String id,String name,int hp,int attack,int skillRank) {
        Unit unit=new Unit();unit.id=id;unit.name=name==null?id:name;unit.hp=unit.maxHp=hp;unit.attack=attack;unit.skillRank=skillRank;return unit;
    }
    private Effects applyWave(int[] count,int combo) {
        Unit actor=units[turn],enemy=units[1-turn];
        int healing=Math.min(actor.maxHp-actor.hp,count[3]*3);actor.hp+=healing;
        int energy=Math.min(100-actor.energy,count[2]*6);actor.energy+=energy;
        int shield=Math.min(80-actor.shield,count[4]*4);actor.shield+=shield;
        int gold=Math.min(100-actor.gold,count[5]*2),xp=Math.min(180-actor.experience,count[6]*3);
        actor.gold+=gold;actor.experience+=xp;
        int raw=count[1]*actor.attack*(100+25*(Math.min(4,combo)-1))/100;
        int absorbed=Math.min(enemy.shield,raw);enemy.shield-=absorbed;
        int damage=Math.min(enemy.hp,raw-absorbed);enemy.hp-=damage;
        return new Effects(damage,absorbed,healing,energy,shield,gold,xp,combo);
    }
    private boolean useSkill(String option,List<Event> output) {
        Skill skill=SKILLS.stream().filter(s->s.id().equals(option)).findFirst().orElse(null);
        Unit actor=units[turn],enemy=units[1-turn];
        if(skill==null||actor.energy<skill.cost())return false;
        if(skill.id().equals("HEAL")&&actor.hp==actor.maxHp)return false;
        if(skill.id().equals("GUARD")&&actor.shield==80)return false;
        actor.energy-=skill.cost();int damage=0,absorbed=0,healing=0,shield=0;
        if(skill.id().equals("STRIKE")) {
            int raw=28+actor.skillRank*6;absorbed=Math.min(enemy.shield,raw);enemy.shield-=absorbed;
            damage=Math.min(enemy.hp,raw-absorbed);enemy.hp-=damage;
        } else if(skill.id().equals("HEAL")) {healing=Math.min(actor.maxHp-actor.hp,30+actor.skillRank*5);actor.hp+=healing;}
        else {shield=Math.min(80-actor.shield,40+actor.skillRank*4);actor.shield+=shield;}
        Effects effect=new Effects(damage,absorbed,healing,-skill.cost(),shield,0,0,0);
        output.add(new Event("SKILL",actor.id,skill.name()+": "+describe(effect),List.of(),board.snapshot(),effect));
        return true;
    }
    private String describe(Effects effect) {
        List<String> parts=new ArrayList<>();
        if(effect.combo()>1)parts.add("Liên hoàn ×"+effect.combo());
        if(effect.damage()>0)parts.add("gây "+effect.damage()+" sát thương");
        if(effect.absorbed()>0)parts.add("phá "+effect.absorbed()+" khiên");
        if(effect.healing()>0)parts.add("hồi "+effect.healing()+" HP");
        if(effect.energy()>0)parts.add("+"+effect.energy()+" năng lượng");
        if(effect.shield()>0)parts.add("+"+effect.shield()+" khiên");
        if(effect.gold()>0)parts.add("+"+effect.gold()+" vàng chiến lợi phẩm");
        if(effect.experience()>0)parts.add("+"+effect.experience()+" kinh nghiệm");
        return parts.isEmpty()?"Thế trận đã thay đổi.":String.join(" · ",parts);
    }
    private void finishTurn() {
        if(units[1-turn].hp<=0){finish(turn,"Đối phương đã hết HP.");return;}
        if(turnNumber>=40) {
            int fatigue=4+(turnNumber-40)/4;units[turn].hp=Math.max(0,units[turn].hp-fatigue);
            notice="Giao tranh kéo dài: bên vừa đi mất "+fatigue+" HP do hao quân (xuyên khiên).";
            if(units[turn].hp==0){finish(1-turn,"Đội quân đã kiệt sức.");return;}
        }
        turn=1-turn;turnNumber++;turnDeadline=System.currentTimeMillis()+45_000;
        botReadyAt=System.currentTimeMillis()+900;
    }
    private void finish(int winningSide,String reason) {
        winner=units[winningSide].id;phase="RESULT";turnDeadline=0;notice=reason;
        List<Reward> grants=new ArrayList<>();
        boolean practice=duel&&(player1.startsWith("bot-")||player2.startsWith("bot-"));
        for(int i=0;i<2;i++) {
            Unit actor=units[i];if(actor.id.startsWith("bot-")||practice)continue;
            boolean won=i==winningSide;
            int gold=duel?(won?25:5)+Math.min(40,actor.gold)/(won?1:2):won?chapterSpec.gold()+actor.gold:actor.gold/2;
            int xp=duel?(won?30:10)+Math.min(60,actor.experience)/(won?1:2):won?chapterSpec.experience()+actor.experience:10+actor.experience/2;
            grants.add(new Reward(actor.id,gold,xp));
            pendingClaims.put(actor.id,new Claim(encounterId,System.currentTimeMillis(),chapter,duel,won,gold,xp));
        }
        rewards=List.copyOf(grants);retryClaims();
    }
    private void retryClaims() {
        Iterator<Map.Entry<String,Claim>> iterator=pendingClaims.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            try {
                var updated=profiles.claim(entry.getKey(),entry.getValue());
                if(!duel&&entry.getKey().equals(player1))profile=updated;
                iterator.remove();
            } catch(RuntimeException unavailable) {
                notice="Chưa lưu được phần thưởng. Giữ phòng và chọn Lưu lại phần thưởng khi kết nối server ổn định.";
            }
        }
        if(duel&&pendingClaims.isEmpty())status="FINISHED";
    }
    @Override public synchronized boolean advanceClock(Set<String> members) {
        if(archive==null||!phase.equals("BATTLE")||System.currentTimeMillis()<turnDeadline)return applyClock(members);
        var before=save();
        try{return archive.transact(player1,roomId,generation,()->applyClock(members),this::save);}
        catch(RuntimeException failure){recover(before);throw failure;}
    }
    private boolean applyClock(Set<String> members) {
        if(!phase.equals("BATTLE")||isGameOver())return false;
        if(duel&&(!members.contains(player1)||!members.contains(player2))) {
            finish(members.contains(player1)?0:1,"Đối thủ đã rời phòng.");revision++;return true;
        }
        if(System.currentTimeMillis()<turnDeadline)return false;
        Unit actor=units[turn];actor.missedTurns++;actor.hp=Math.max(0,actor.hp-12);
        events=List.of();notice="Hết 45 giây: mất lượt và 12 HP. Ba lần liên tiếp sẽ thua.";
        revision++;
        if(actor.hp==0||actor.missedTurns>=3)finish(1-turn,"Đối thủ không hoàn thành lượt.");else finishTurn();
        return true;
    }
    @Override public synchronized boolean botReady() {return System.currentTimeMillis()>=botReadyAt;}
    @Override public synchronized Move generateBotMove() {
        if(!phase.equals("BATTLE")||isGameOver())return null;
        Unit actor=units[turn],enemy=units[1-turn];
        int level=duel?3:chapterSpec.botLevel();
        var choice=WarlordsBot.choose(board.snapshot(),actor.stats(),enemy.stats(),level,seed+turnNumber);
        return choice==null?null:command(choice.swap()==null?"SKILL":"SWAP",choice.skill(),choice.swap());
    }
    private Move command(String action,String option,CombatBoard.Swap swap) {
        return new Move(action,swap==null?null:swap.row(),swap==null?null:swap.column(),swap==null?null:swap.toRow(),swap==null?null:swap.toColumn(),option,null,encounterId,revision);
    }
    public synchronized SavedCampaign save() {
        return new SavedCampaign(1,player1,seed,phase,status,winner,encounterId,revision,turn,turnNumber,chapter,
                Math.max(0,turnDeadline-System.currentTimeMillis()),board==null?null:board.save(),
                Arrays.stream(units).filter(Objects::nonNull).map(Unit::snapshot).toList(),profile,List.copyOf(rewards),Map.copyOf(pendingClaims),notice,chapterSpec);
    }
    public synchronized void flush(){if(archive!=null)archive.transact(player1,roomId,generation,()->true,this::save);}
    private void recover(SavedCampaign before) {
        // A lost commit reply is ambiguous: read durable truth before accepting another command.
        try{var committed=archive.latest(player1,roomId,generation);restore(committed==null?before:committed,false);}
        catch(RuntimeException unavailable){restore(before,false);}
    }
    private void restore(SavedCampaign saved,boolean resume) {
        if(saved.version()!=1||!Objects.equals(player1,saved.playerId())||!Set.of("MAP","BATTLE","RESULT").contains(saved.phase())
                ||saved.turn()<0||saved.turn()>1||saved.chapter()<0||saved.chapter()>WarlordsCampaign.CHAPTERS.size()
                ||(!saved.phase().equals("MAP")&&(saved.board()==null||saved.fighters().size()!=2||saved.chapter()==0)))
            throw new IllegalArgumentException("Bản lưu chiến dịch không tương thích. Dữ liệu vẫn được giữ nguyên.");
        seed=saved.seed();phase=saved.phase();status=saved.status();winner=saved.winner();encounterId=saved.encounterId();
        revision=saved.revision();turn=saved.turn();turnNumber=saved.turnNumber();chapter=saved.chapter();
        chapterSpec=saved.chapterSpec();
        board=saved.board()==null?null:CombatBoard.restore(saved.board());
        Arrays.fill(units,null);
        for(int i=0;i<saved.fighters().size();i++) {
            Fighter f=saved.fighters().get(i);Unit u=unit(f.id(),f.name(),f.maxHp(),f.attack(),f.skillRank());
            u.hp=f.hp();u.energy=f.energy();u.shield=f.shield();u.gold=f.gold();u.experience=f.experience();u.missedTurns=f.missedTurns();u.hero=f.hero();units[i]=u;
        }
        profile=saved.profile();rewards=List.copyOf(saved.rewards());pendingClaims.clear();pendingClaims.putAll(saved.pendingClaims());
        events=List.of();notice=resume?"Đã khôi phục chiến dịch tại lượt gần nhất được lưu.":saved.notice();
        turnDeadline=phase.equals("BATTLE")?System.currentTimeMillis()+(resume?Math.max(5000,Math.min(45000,saved.remainingTurnMs())):saved.remainingTurnMs()):0;
        botReadyAt=System.currentTimeMillis()+900;
    }
    @Override public synchronized State getState() {
        List<Event> copied=events.stream().map(e->new Event(e.type(),e.actor(),e.text(),e.cells(),CombatBoard.copy(e.board()),e.effects())).toList();
        List<Fighter> fighters=Arrays.stream(units).filter(Objects::nonNull).map(Unit::snapshot).toList();
        return new State(duel?"DUEL":"CAMPAIGN",phase,status,winner,getCurrentPlayerId(),encounterId,revision,turnDeadline,turnNumber,
                board==null?new int[0][0]:board.snapshot(),fighters,chapterSpec,
                duel?List.of():WarlordsCampaign.CHAPTERS,SKILLS,duel?null:profile,copied,rewards,!pendingClaims.isEmpty(),
                duel&&(player1.startsWith("bot-")||player2.startsWith("bot-")),notice,WarlordsHeroes.ALL);
    }
}
