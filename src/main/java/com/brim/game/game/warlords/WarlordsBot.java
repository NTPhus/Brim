package com.brim.game.game.warlords;

import java.util.*;

/** Bounded, deterministic search using visible tiles and public combat stats only. */
public final class WarlordsBot {
    private WarlordsBot() {}
    public record Stats(int hp,int maxHp,int energy,int shield,int attack,int skillRank) {}
    public record Choice(String skill,CombatBoard.Swap swap) {}
    private record Candidate(Choice choice,int[][] board,Stats self,Stats enemy,double score) {}

    public static Choice choose(int[][] board,Stats self,Stats enemy,int level,long variety) {
        List<Candidate> options=candidates(board,self,enemy,level>=3);
        options.sort(Comparator.comparingDouble(Candidate::score).reversed());
        if(options.isEmpty())return null;
        // Early opponents see legal matches, but do not always pick the strongest one.
        if(level==1) {
            List<Candidate> swaps=options.stream().filter(c->c.choice().swap()!=null).toList();
            return swaps.isEmpty()?options.get(0).choice():swaps.get(Math.floorMod(variety,Math.min(4,swaps.size()))).choice();
        }
        if(level==2)return options.get(0).choice();
        Candidate best=options.get(0);double bestScore=-Double.MAX_VALUE;
        // At most eight candidate moves and one opponent reply; search never depends on unseen refills.
        for(Candidate option:options.subList(0,Math.min(8,options.size()))) {
            double reply=option.enemy().hp()<=0?0:candidates(option.board(),option.enemy(),option.self(),true)
                    .stream().mapToDouble(Candidate::score).max().orElse(0);
            double score=option.score()-(level==3?.35:1.0)*reply;
            if(score>bestScore){bestScore=score;best=option;}
        }
        return best.choice();
    }

    private static List<Candidate> candidates(int[][] board,Stats self,Stats enemy,boolean cascades) {
        List<Candidate> result=new ArrayList<>();
        for(var move:CombatBoard.legalMoves(board)) {
            var projected=CombatBoard.project(board,move,cascades);
            Stats actor=self,foe=enemy;int combo=0,loot=0;
            for(int[] count:projected.waves()) {
                int raw=count[1]*actor.attack()*(100+25*(Math.min(4,++combo)-1))/100;
                int absorbed=Math.min(foe.shield(),raw);
                actor=new Stats(Math.min(actor.maxHp(),actor.hp()+count[3]*3),actor.maxHp(),
                        Math.min(100,actor.energy()+count[2]*6),Math.min(80,actor.shield()+count[4]*4),actor.attack(),actor.skillRank());
                foe=new Stats(Math.max(0,foe.hp()-raw+absorbed),foe.maxHp(),foe.energy(),foe.shield()-absorbed,foe.attack(),foe.skillRank());
                loot+=count[5]+count[6];if(foe.hp()==0)break;
            }
            result.add(new Candidate(new Choice(null,move),projected.board(),actor,foe,value(self,enemy,actor,foe)+loot*.15));
        }
        if(self.energy()>=30) {
            int raw=28+self.skillRank()*6,absorbed=Math.min(enemy.shield(),raw);
            Stats actor=new Stats(self.hp(),self.maxHp(),self.energy()-30,self.shield(),self.attack(),self.skillRank());
            Stats foe=new Stats(Math.max(0,enemy.hp()-raw+absorbed),enemy.maxHp(),enemy.energy(),enemy.shield()-absorbed,enemy.attack(),enemy.skillRank());
            result.add(new Candidate(new Choice("STRIKE",null),board,actor,foe,value(self,enemy,actor,foe)));
        }
        if(self.energy()>=25&&self.hp()<self.maxHp()) {
            Stats actor=new Stats(Math.min(self.maxHp(),self.hp()+30+self.skillRank()*5),self.maxHp(),self.energy()-25,self.shield(),self.attack(),self.skillRank());
            result.add(new Candidate(new Choice("HEAL",null),board,actor,enemy,value(self,enemy,actor,enemy)));
        }
        if(self.energy()>=20&&self.shield()<80) {
            Stats actor=new Stats(self.hp(),self.maxHp(),self.energy()-20,Math.min(80,self.shield()+40+self.skillRank()*4),self.attack(),self.skillRank());
            result.add(new Candidate(new Choice("GUARD",null),board,actor,enemy,value(self,enemy,actor,enemy)));
        }
        return result;
    }
    private static double value(Stats before,Stats target,Stats after,Stats foe) {
        if(foe.hp()==0)return 100_000;
        double healingWeight=before.hp()*2<before.maxHp()?2.8:1.1;
        return (target.hp()-foe.hp())*2.2+(target.shield()-foe.shield())*1.1
                +(after.hp()-before.hp())*healingWeight+(after.shield()-before.shield())*.85
                +(after.energy()-before.energy())*.55;
    }
}
