package com.brim.game.service;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VietnamProgressTest {
    @Test @SuppressWarnings("unchecked") void completionIsMonotonicBoundedAndSeparatePerPlayer() {
        RedisTemplate<String,Object> redis=mock(RedisTemplate.class);
        ValueOperations<String,Object> values=mock(ValueOperations.class);
        Map<String,Object> stored=new HashMap<>();when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call->stored.get(call.getArgument(0)));
        doAnswer(call->{stored.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),any());
        var profiles=new VietnamProgressService(redis);
        assertEquals(1,profiles.unlocked("a"));profiles.complete("a",3);assertEquals(1,profiles.unlocked("a"));
        profiles.complete("a",1);assertEquals(2,profiles.unlocked("a"));assertEquals(1,profiles.unlocked("b"));
        profiles.complete("a",2);profiles.complete("a",1);assertEquals(3,profiles.unlocked("a"));
        profiles.complete("a",999);assertEquals(3,profiles.unlocked("a"));
    }
}
