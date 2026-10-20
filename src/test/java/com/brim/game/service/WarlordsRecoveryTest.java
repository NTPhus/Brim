package com.brim.game.service;

import com.brim.game.game.warlords.*;
import com.brim.game.game.warlords.WarlordsGame.Move;
import com.brim.game.model.dto.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WarlordsRecoveryTest {
    @TempDir Path directory;
    private WarlordsDatabase database;
    private WarlordsProfileService profiles;
    private WarlordsCampaignArchive archive;
    private JdbcTemplate jdbc;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){open();}
    @SuppressWarnings("unchecked") private void open(){
        var source=new DriverManagerDataSource("jdbc:h2:file:"+directory.resolve("game").toAbsolutePath().toString().replace('\\','/')+";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE","sa","");
        jdbc=new JdbcTemplate(source);var json=new ObjectMapper();
        database=new WarlordsDatabase(jdbc,json,new DataSourceTransactionManager(source));
        var redis=mock(RedisTemplate.class,RETURNS_DEEP_STUBS);
        profiles=new WarlordsProfileService(redis,database);archive=spy(new WarlordsCampaignArchive(database,json));
    }
    private WarlordsGame game(String room){
        var game=new WarlordsGame(room,profiles,archive);var player=new Player();player.setId("a");player.setUsername("General");game.setPlayers(List.of(player));return game;
    }
    private Move command(WarlordsGame game,String action,String option,Integer chapter){
        var state=game.getState();return new Move(action,null,null,null,null,option,chapter,state.encounterId(),state.revision());
    }
    private WarlordsGame battle(){
        profiles.selectHero("a","LE_HOAN");var game=game("first");assertTrue(game.makeMove("a",command(game,"START",null,1)));return game;
    }
    private void terminalSetup(WarlordsGame game){
        Object[] units=(Object[])ReflectionTestUtils.getField(game,"units");
        ReflectionTestUtils.setField(units[0],"energy",100);ReflectionTestUtils.setField(units[1],"hp",1);game.flush();
    }
    @Test void newDatabaseAndEngineInstancesResumeExactBoardAndFutureRefills(){
        var original=battle();assertTrue(original.makeMove("a",command(original,"SKILL","GUARD",null)));
        assertTrue(original.makeMove(original.getCurrentPlayerId(),original.generateBotMove()));
        var before=original.getState();var saved=original.save();
        var accountService=new com.brim.game.security.AccountService(jdbc,database);
        accountService.register("General_01","a-long-password",new com.brim.game.security.GuestSession("a","General",Long.MAX_VALUE));
        open(); // No profile or game objects survive this simulated process restart; file database does.
        assertEquals("a",new com.brim.game.security.AccountService(jdbc,database).authenticate("general_01","a-long-password").id());
        var resumed=game("second");var after=resumed.getState();
        assertEquals(before.encounterId(),after.encounterId());assertEquals(before.revision(),after.revision());
        assertArrayEquals(before.board(),after.board());assertEquals(before.fighters(),after.fighters());assertEquals(before.currentPlayer(),after.currentPlayer());
        assertEquals("LE_HOAN",profiles.load("a").heroId());
        var next=resumed.generateBotMove();assertEquals("SWAP",next.action());
        var expected=CombatBoard.restore(saved.board());expected.resolve(new CombatBoard.Swap(next.row(),next.column(),next.toRow(),next.toColumn()));
        assertTrue(resumed.makeMove(resumed.getCurrentPlayerId(),next));assertArrayEquals(expected.snapshot(),resumed.getState().board());
        assertThrows(RuntimeException.class,()->original.makeMove(original.getCurrentPlayerId(),original.generateBotMove()),"Previous room generation cannot write");
    }
    @Test void failedCommitRollsBackBothVictoryRewardsAndCombatThenRetryPaysOnce(){
        var game=battle();terminalSetup(game);var before=game.getState();var move=command(game,"SKILL","STRIKE",null);
        doAnswer(call->database.transaction(()->{
            try{call.callRealMethod();}catch(Throwable failure){throw new IllegalStateException(failure);}
            throw new IllegalStateException("Storage failed before commit");
        })).when(archive).transact(eq("a"),eq("first"),anyLong(),any(),any());
        assertThrows(IllegalStateException.class,()->game.makeMove("a",move));
        assertEquals(before.revision(),game.getState().revision());assertEquals(before.fighters(),game.getState().fighters());
        assertEquals("BATTLE",game.getState().phase());assertEquals(100,profiles.load("a").gold());assertEquals(0,profiles.load("a").wins());
        doCallRealMethod().when(archive).transact(eq("a"),eq("first"),anyLong(),any(),any());
        assertTrue(game.makeMove("a",move));assertEquals(185,profiles.load("a").gold());assertEquals(1,profiles.load("a").wins());
        var restored=game("second");assertEquals("RESULT",restored.getState().phase());assertFalse(restored.makeMove("a",move));
        assertEquals(185,profiles.load("a").gold());
    }
    @Test void lostCommitReplyReconcilesFromDatabaseWithoutRepeatingRewards(){
        var game=battle();terminalSetup(game);var move=command(game,"SKILL","STRIKE",null);
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{var result=call.callRealMethod();if(first.getAndSet(false))throw new IllegalStateException("Commit reply lost");return result;})
                .when(archive).transact(eq("a"),eq("first"),anyLong(),any(),any());
        assertThrows(IllegalStateException.class,()->game.makeMove("a",move));
        assertEquals("RESULT",game.getState().phase());assertEquals(185,profiles.load("a").gold());
        assertFalse(game.makeMove("a",move));assertEquals(1,profiles.load("a").wins());
    }
    @Test void accountsUsePasswordHashesAndRejectWrongPasswordAndDuplicateNames(){
        var accounts=new com.brim.game.security.AccountService(jdbc,database);
        var account=accounts.register("General_01","a-long-password",null);
        assertEquals(account,accounts.authenticate("GENERAL_01","a-long-password"));
        String stored=jdbc.queryForObject("SELECT password_hash FROM brim_accounts WHERE id=?",String.class,account.id());
        assertNotEquals("a-long-password",stored);assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("a-long-password",stored));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->accounts.authenticate("General_01","wrong-password"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->accounts.register("general_01","another-password",null));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->accounts.register("invalid user","a-long-password",null));
    }
}
