package com.brim.game.service;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.Arrays;

@Service
public class VietnamProgressService {
    private final RedisTemplate<String,Object> redis;
    private final Object[] locks = new Object[32];
    public VietnamProgressService(RedisTemplate<String,Object> redis) { this.redis=redis; Arrays.setAll(locks,i->new Object()); }
    public int unlocked(String id) {
        Object value=redis.opsForValue().get("profile:vietnam:"+id);
        return value instanceof Number n ? Math.max(1,Math.min(3,n.intValue())) : 1;
    }
    public void complete(String id,int chapter) {
        synchronized(locks[Math.floorMod(id.hashCode(),locks.length)]) {
            int current=unlocked(id);
            if(chapter>=1 && chapter<=current) redis.opsForValue().set("profile:vietnam:"+id,Math.min(3,Math.max(current,chapter+1)));
        }
    }
}
