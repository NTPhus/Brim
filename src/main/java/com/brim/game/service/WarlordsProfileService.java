package com.brim.game.service;

import com.brim.game.game.warlords.WarlordsProfile;
import com.brim.game.game.warlords.WarlordsProfile.Claim;
import java.util.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** Database row locks serialize upgrades and idempotent rewards; Redis is a one-time legacy import. */
@Service
public class WarlordsProfileService implements WarlordsProfile.Store {
    private final RedisTemplate<String,Object> redis;
    private final WarlordsDatabase database;
    public WarlordsProfileService(RedisTemplate<String,Object> redis,WarlordsDatabase database) {
        this.redis=redis;this.database=database;
    }
    private String key(String id) {return "profile:warlords:"+id;}
    private Map<String,Object> legacy(String id) {
        Object value=redis.opsForValue().get(key(id));
        Map<String,Object> result=new HashMap<>();
        if(value instanceof Map<?,?> map) map.forEach((k,v)->{if(k instanceof String s)result.put(s,v);});
        return result;
    }
    private static int number(Map<String,Object> data,String key,int fallback,int max) {
        return data.get(key) instanceof Number n?Math.max(0,Math.min(max,n.intValue())):fallback;
    }
    private WarlordsProfile profile(Map<String,Object> data) {
        Set<Integer> cleared=new TreeSet<>();
        if(data.get("cleared") instanceof Collection<?> list)for(Object item:list)
            if(item instanceof Number n&&n.intValue()>=1&&n.intValue()<=12)cleared.add(n.intValue());
        int unlocked=1;while(unlocked<12&&cleared.contains(unlocked))unlocked++;
        return new WarlordsProfile(number(data,"gold",100,1_000_000),number(data,"experience",0,1_000_000),
                number(data,"weapon",0,5),number(data,"armor",0,5),number(data,"skill",0,5),unlocked,
                new ArrayList<>(cleared),number(data,"wins",0,1_000_000),number(data,"losses",0,1_000_000),
                data.get("heroId") instanceof String id ? id : null);
    }
    private <T> T update(String id,java.util.function.Function<Map<String,Object>,T> change) {
        return database.update(key(id),()->legacy(id),change);
    }
    @Override public WarlordsProfile load(String id) {return update(id,this::profile);}
    @Override public WarlordsProfile selectHero(String id,String heroId) {
        String selected=com.brim.game.game.warlords.WarlordsHeroes.require(heroId).id();
        return update(id,data->{data.put("heroId",selected);return profile(data);});
    }
    @Override public WarlordsProfile upgrade(String id,String type) {
        return update(id,data->{
            WarlordsProfile current=profile(data);
            String field=switch(type==null?"":type){case "WEAPON"->"weapon";case "ARMOR"->"armor";case "SKILL"->"skill";default->throw new IllegalArgumentException("Loại nâng cấp không hợp lệ.");};
            int rank=number(data,field,0,5),cost=WarlordsProfile.upgradeCost(type,rank);
            if(rank>=5)throw new IllegalArgumentException("Nâng cấp đã đạt cấp tối đa.");
            if(current.gold()<cost)throw new IllegalArgumentException("Không đủ vàng để nâng cấp.");
            data.put(field,rank+1);data.put("gold",current.gold()-cost);
            return profile(data);
        });
    }
    @Override public WarlordsProfile claim(String id,Claim claim) {
        return update(id,data->{
            WarlordsProfile current=profile(data);
            Map<String,Long> receipts=new HashMap<>();
            if(data.get("receipts") instanceof Map<?,?> map)map.forEach((k,v)->{if(k instanceof String s&&v instanceof Number n)receipts.put(s,n.longValue());});
            long retired=data.get("retiredBefore") instanceof Number n?n.longValue():0;
            if(receipts.containsKey(claim.id())||claim.createdAt()<=retired)return current;
            int gold=Math.max(0,Math.min(400,claim.gold())),xp=Math.max(0,Math.min(500,claim.experience()));
            List<Integer> cleared=new ArrayList<>(current.cleared());
            if(!claim.duel()&&claim.won()&&claim.stage()>=1&&claim.stage()<=current.unlocked()&&!cleared.contains(claim.stage())) {
                cleared.add(claim.stage());gold+=50;xp+=30;
            }
            data.put("gold",Math.min(1_000_000,current.gold()+gold));
            data.put("experience",Math.min(1_000_000,current.experience()+xp));
            data.put("cleared",cleared);
            data.put(claim.won()?"wins":"losses",Math.min(1_000_000,(claim.won()?current.wins():current.losses())+1));
            receipts.put(claim.id(),claim.createdAt());
            while(receipts.size()>256) {
                var oldest=receipts.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow();
                retired=Math.max(retired,oldest.getValue());receipts.remove(oldest.getKey());
            }
            data.put("receipts",receipts);data.put("retiredBefore",retired);
            return profile(data);
        });
    }
}
