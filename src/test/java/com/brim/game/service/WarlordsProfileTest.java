package com.brim.game.service;

import com.brim.game.game.warlords.WarlordsProfile;
import com.brim.game.game.warlords.WarlordsProfile.Claim;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WarlordsProfileTest {
    private WarlordsProfileService service;
    private ValueOperations<String,Object> values;
    private WarlordsDatabase database;
    private final Map<String,Object> saved=new ConcurrentHashMap<>();
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        RedisTemplate<String,Object> redis=mock(RedisTemplate.class);values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);when(values.get(anyString())).thenAnswer(call->saved.get(call.getArgument(0)));
        doAnswer(call->{saved.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),any());
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:profile-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        database=spy(new WarlordsDatabase(new org.springframework.jdbc.core.JdbcTemplate(source),new com.fasterxml.jackson.databind.ObjectMapper(),new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)));
        service=new WarlordsProfileService(redis,database);
    }
    @Test void upgradesDeductServerPriceAndRejectUnknownTypeOrInsufficientGold() {
        assertEquals(WarlordsProfile.initial(),service.load("a"));
        var upgraded=service.upgrade("a","WEAPON");assertEquals(1,upgraded.weapon());assertEquals(40,upgraded.gold());
        assertThrows(IllegalArgumentException.class,()->service.upgrade("a","WEAPON"));
        assertThrows(IllegalArgumentException.class,()->service.upgrade("a","FREE_GOLD"));
        assertEquals(40,service.load("a").gold());assertEquals(100,service.load("b").gold());
    }
    @Test void heroSelectionPersistsWithoutResettingProgressOrGrantingResources() {
        var upgraded=service.upgrade("a","WEAPON");
        var selected=service.selectHero("a","DINH_LIEN");
        assertEquals("DINH_LIEN",service.load("a").heroId());
        assertEquals(upgraded.gold(),selected.gold());assertEquals(upgraded.weapon(),selected.weapon());
        assertEquals(selected.heroId(),service.claim("a",new Claim("reward",1,1,false,true,35,42)).heroId());
        assertThrows(IllegalArgumentException.class,()->service.selectHero("a","FREE_STATS"));
        assertEquals("DINH_LIEN",service.load("a").heroId());
        assertEquals("DINH_BO_LINH",service.load("b").heroId());
    }
    @Test void firstClearUnlocksNextChapterAndRetryCannotDuplicateReward() {
        var claim=new Claim("battle1",1,1,false,true,35,42);
        var first=service.claim("a",claim);var second=service.claim("a",claim);
        assertEquals(first,second);assertEquals(185,first.gold());assertEquals(72,first.experience());assertEquals(2,first.unlocked());
        var replay=service.claim("a",new Claim("battle2",2,1,false,true,35,42));
        assertEquals(220,replay.gold());assertEquals(114,replay.experience());assertEquals(2,replay.unlocked());
    }
    @Test void lostReplyAfterDatabaseCommitIsSafeToRetry() {
        service.load("a");var fail=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{var result=call.callRealMethod();if(fail.getAndSet(false))throw new IllegalStateException("Reply lost");return result;})
                .when(database).update(eq("profile:warlords:a"),any(),any());
        var claim=new Claim("commit",1,1,false,true,35,42);
        assertThrows(IllegalStateException.class,()->service.claim("a",claim));
        assertEquals(185,service.claim("a",claim).gold());
        assertEquals(1,service.load("a").wins());
    }
    @Test void concurrentClaimsCannotLoseUpdatesAndDuelDoesNotUnlockCampaign() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(6);
        try {
            List<Callable<WarlordsProfile>> calls=new ArrayList<>();
            for(int i=0;i<30;i++){int id=i;calls.add(()->service.claim("a",new Claim("duel"+id,id+1,0,true,true,5,10)));}
            for(var future:workers.invokeAll(calls))future.get();
        }finally{workers.shutdownNow();}
        var profile=service.load("a");assertEquals(250,profile.gold());assertEquals(300,profile.experience());
        assertEquals(1,profile.unlocked());assertEquals(30,profile.wins());
    }
    @Test void boundedReceiptHistoryStillRejectsRetiredClaims() {
        for(int i=1;i<=260;i++)service.claim("a",new Claim("battle"+i,i,0,true,true,1,1));
        var before=service.load("a");var after=service.claim("a",new Claim("battle1",1,0,true,true,100,100));
        assertEquals(before,after);
        Map<String,Object> data=database.read("profile:warlords:a");
        assertEquals(256,((Map<?,?>)data.get("receipts")).size());
    }
    @Test void legacyRedisProfileIsImportedOnceAndThenSurvivesRedisLoss() {
        saved.put("profile:warlords:a",new HashMap<>(Map.of("gold",345,"heroId","LE_HOAN","cleared",List.of(1,2))));
        assertEquals(345,service.load("a").gold());saved.clear();
        assertEquals(345,service.load("a").gold());assertEquals("LE_HOAN",service.load("a").heroId());assertEquals(3,service.load("a").unlocked());
        verify(values,times(1)).get("profile:warlords:a");
    }
}
