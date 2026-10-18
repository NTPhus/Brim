package com.brim.game.service;

import com.brim.game.game.warlords.WarlordsProfile;
import com.brim.game.game.warlords.WarlordsProfile.Claim;
import java.util.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** Per-player updates are serialized in this single-backend platform. No client reward endpoint. */
@Service
public class WarlordsProfileService implements WarlordsProfile.Store {
    private final RedisTemplate<String,Object> redis;
    private final Object[] locks=new Object[64];
    public WarlordsProfileService(RedisTemplate<String,Object> redis) {
        this.redis=redis;Arrays.setAll(locks,i->new Object());
    }
    private Object lock(String id) {return locks[Math.floorMod(id.hashCode(),locks.length)];}
    private String key(String id) {return "profile:warlords:"+id;}
    private Map<String,Object> read(String id) {
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
                new ArrayList<>(cleared),number(data,"wins",0,1_000_000),number(data,"losses",0,1_000_000));
    }
    @Override public WarlordsProfile load(String id) {synchronized(lock(id)){return profile(read(id));}}
    @Override public WarlordsProfile upgrade(String id,String type) {
        synchronized(lock(id)) {
            Map<String,Object> data=read(id);WarlordsProfile current=profile(data);
            String field=switch(type==null?"":type){case "WEAPON"->"weapon";case "ARMOR"->"armor";case "SKILL"->"skill";default->throw new IllegalArgumentException("Loại nâng cấp không hợp lệ.");};
            int rank=number(data,field,0,5),cost=WarlordsProfile.upgradeCost(type,rank);
            if(rank>=5)throw new IllegalArgumentException("Nâng cấp đã đạt cấp tối đa.");
            if(current.gold()<cost)throw new IllegalArgumentException("Không đủ vàng để nâng cấp.");
            data.put(field,rank+1);data.put("gold",current.gold()-cost);
            redis.opsForValue().set(key(id),data);
            return profile(data);
        }
    }
    @Override public WarlordsProfile claim(String id,Claim claim) {
        synchronized(lock(id)) {
            Map<String,Object> data=read(id);WarlordsProfile current=profile(data);
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
            redis.opsForValue().set(key(id),data);
            return profile(data);
        }
    }
}
