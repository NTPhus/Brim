package com.brim.game.game;

import com.brim.game.game.warlords.*;
import com.brim.game.game.warlords.WarlordsGame.Move;
import com.brim.game.model.dto.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WarlordsGameTest {
    private WarlordsProfile.Store store() {
        var store=mock(WarlordsProfile.Store.class);
        when(store.load(anyString())).thenReturn(WarlordsProfile.initial());
        when(store.claim(anyString(),any())).thenAnswer(call->{
            var c=(WarlordsProfile.Claim)call.getArgument(1);
            return new WarlordsProfile(100+c.gold()+50,c.experience()+30,0,0,0,2,List.of(1),1,0);
        });
        return store;
    }
    private WarlordsGame game(boolean duel,WarlordsProfile.Store store) {
        var game=new WarlordsGame("room",duel,store,12345L);
        List<Player> players=new ArrayList<>();
        for(String id:duel?List.of("a","b"):List.of("a")) {var player=new Player();player.setId(id);player.setUsername(id);players.add(player);}
        game.setPlayers(players);return game;
    }
    private Move command(WarlordsGame g,String action,String option,Integer chapter,Integer... cells) {
        var s=g.getState();
        return new Move(action,cells.length>0?cells[0]:null,cells.length>1?cells[1]:null,cells.length>2?cells[2]:null,cells.length>3?cells[3]:null,option,chapter,s.encounterId(),s.revision());
    }
    private Object unit(WarlordsGame g,int side) {return ((Object[])ReflectionTestUtils.getField(g,"units"))[side];}
    private void set(WarlordsGame g,int side,String field,int value) {ReflectionTestUtils.setField(unit(g,side),field,value);}
    private void fixedBoard(WarlordsGame game,int value) {
        var board=(CombatBoard)ReflectionTestUtils.getField(game,"board");
        int[][] cells=new int[8][8];for(int r=0;r<8;r++)for(int c=0;c<8;c++)cells[r][c]=(r*2+c)%6+1;
        cells[7][0]=cells[7][2]=cells[6][1]=value;cells[7][1]=value%6+1;cells[7][3]=(value+2)%6+1;
        assertTrue(com.brim.game.game.matchthree.MatchThreeGame.findMatches(cells).isEmpty());
        ReflectionTestUtils.setField(board,"cells",cells);
    }
    private void start(WarlordsGame game) {assertTrue(game.makeMove("a",command(game,"START",null,1)));}

    @Test void campaignStartsOnMapAndRejectsLockedChaptersAndOutsiders() {
        var game=game(false,store());
        assertEquals("MAP",game.getState().phase());assertEquals(12,game.getState().chapters().size());
        assertFalse(game.makeMove("x",command(game,"START",null,1)));
        assertFalse(game.makeMove("a",command(game,"START",null,2)));
        assertFalse(game.makeMove("a",command(game,"START",null,-1)));
        start(game);assertEquals("a",game.getCurrentPlayerId());assertEquals("BATTLE",game.getState().phase());
        assertEquals(140,game.getState().fighters().get(0).maxHp());
    }
    @Test void selectedHeroChangesCampaignStyleOnlyAndCannotChangeDuringBattle() {
        var store=store();
        var selected=new WarlordsProfile(100,0,0,0,0,1,List.of(),0,0,"DINH_LIEN");
        when(store.load("a")).thenReturn(selected);when(store.selectHero("a","DINH_LIEN")).thenReturn(selected);
        var campaign=game(false,store);
        assertTrue(campaign.makeMove("a",command(campaign,"SELECT_HERO","DINH_LIEN",null)));
        start(campaign);var hero=campaign.getState().fighters().get(0);
        assertEquals("DINH_LIEN",hero.hero().id());assertEquals(128,hero.maxHp());assertEquals(6,hero.attack());
        assertFalse(campaign.makeMove("a",command(campaign,"SELECT_HERO","NGUYEN_BAC",null)));
        assertEquals(6,campaign.getState().heroes().size());
        var duel=game(true,store);var pvp=duel.getState().fighters().get(0);
        assertEquals("DINH_LIEN",pvp.hero().id());assertEquals(140,pvp.maxHp());assertEquals(5,pvp.attack());
        assertEquals(10,pvp.energy());assertEquals(0,pvp.shield());assertEquals(0,pvp.skillRank());
    }
    @Test void botPacingDoesNotBlockWorkersAndCampaignDifficultyIncreases() {
        var game=game(false,store());start(game);
        assertTrue(game.makeMove("a",game.generateBotMove()));
        assertFalse(game.botReady());ReflectionTestUtils.setField(game,"botReadyAt",0L);assertTrue(game.botReady());
        assertEquals(1,WarlordsCampaign.chapter(1).botLevel());assertEquals(2,WarlordsCampaign.chapter(4).botLevel());
        assertEquals(3,WarlordsCampaign.chapter(7).botLevel());assertEquals(4,WarlordsCampaign.chapter(12).botLevel());
    }
    @Test void invalidSwapsAndStaleCommandsDoNotSpendTurnOrResources() {
        var game=game(true,store());var before=game.getState();
        assertFalse(game.makeMove("b",game.generateBotMove()));
        assertFalse(game.makeMove("a",command(game,"SWAP",null,null,-1,0,0,0)));
        assertFalse(game.makeMove("a",command(game,"SWAP",null,null,0,0,7,7)));
        assertFalse(game.makeMove("a",new Move("SKILL",null,null,null,null,"STRIKE",null,"old",before.revision())));
        Move legal=game.generateBotMove();assertTrue(game.makeMove("a",legal));
        long changed=game.getState().revision();assertFalse(game.makeMove("a",legal));assertEquals(changed,game.getState().revision());
        assertEquals(before.turnNumber()+1,game.getState().turnNumber());
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6})
    void threeOfEachResourceHasItsCombatEffect(int value) {
        var game=game(true,store());fixedBoard(game,value);set(game,0,"hp",100);
        assertTrue(game.makeMove("a",command(game,"SWAP",null,null,6,1,7,1)));
        var swap=game.getState().events().get(0);
        assertEquals("SWAP",swap.type());assertEquals(2,swap.cells().size());
        assertEquals(value,swap.board()[7][1]);assertEquals(value%6+1,swap.board()[6][1]);
        var effect=game.getState().events().stream().filter(e->e.type().equals("CLEAR")).findFirst().orElseThrow().effects();
        assertEquals(switch(value){case 1->15;default->0;},effect.damage());
        assertEquals(value==2?18:0,effect.energy());assertEquals(value==3?9:0,effect.healing());
        assertEquals(value==4?12:0,effect.shield());assertEquals(value==5?6:0,effect.gold());assertEquals(value==6?9:0,effect.experience());
    }
    @Test void shieldsAbsorbBeforeHpAndSkillsConsumeEnergyAndTurn() {
        var game=game(true,store());fixedBoard(game,1);set(game,1,"shield",10);
        assertTrue(game.makeMove("a",command(game,"SWAP",null,null,6,1,7,1)));
        var effect=game.getState().events().stream().filter(e->e.type().equals("CLEAR")).findFirst().orElseThrow().effects();assertEquals(10,effect.absorbed());assertEquals(5,effect.damage());
        set(game,1,"energy",100);set(game,1,"hp",80);
        assertTrue(game.makeMove("b",command(game,"SKILL","HEAL",null)));
        assertEquals(110,game.getState().fighters().get(1).hp());assertEquals(75,game.getState().fighters().get(1).energy());
        set(game,0,"energy",100);
        assertTrue(game.makeMove("a",command(game,"SKILL","GUARD",null)));
        assertEquals(40,game.getState().fighters().get(0).shield());assertEquals(80,game.getState().fighters().get(0).energy());
        assertTrue(game.makeMove("b",command(game,"SKILL","STRIKE",null)));
        assertEquals(12,game.getState().fighters().get(0).shield());assertEquals("a",game.getCurrentPlayerId());
    }
    @Test void encodedLineBlastEmitsItsOriginAndAppliesAllSixResourceEffectsExactlyOnce() {
        var game=game(true,store());set(game,0,"hp",100);
        int[][] cells=new int[8][8];for(int r=0;r<8;r++)for(int c=0;c<8;c++)cells[r][c]=(r*2+c)%6+1;
        cells[3][2]=CombatBoard.tile(cells[3][2],CombatBoard.ROW);
        ReflectionTestUtils.setField(game,"board",CombatBoard.restore(new CombatBoard.Saved(cells,123456L)));
        assertTrue(game.makeMove("a",command(game,"SWAP",null,null,3,2,3,3)));
        var events=game.getState().events();
        var activation=events.stream().filter(e->e.type().equals("SPECIAL_ACTIVATED")).findFirst().orElseThrow();
        assertEquals(13,activation.board()[3][3]);assertEquals(1,activation.cells().size());
        assertTrue(activation.text().contains("Hoành trận"));
        var clear=events.stream().filter(e->e.type().equals("CLEAR")).findFirst().orElseThrow();
        assertEquals(8,clear.cells().size());assertEquals(8,new HashSet<>(clear.cells()).size());
        var effects=clear.effects();assertEquals(10,effects.damage());assertEquals(12,effects.energy());
        assertEquals(3,effects.healing());assertEquals(4,effects.shield());assertEquals(2,effects.gold());assertEquals(3,effects.experience());
    }
    @Test void impossibleSkillPreservesStateAndSwordVictorySettlesCampaignOnce() {
        var store=store();var game=game(false,store);start(game);
        assertFalse(game.makeMove("a",command(game,"SKILL","STRIKE",null)));
        assertEquals(10,game.getState().fighters().get(0).energy());assertEquals(1,game.getState().turnNumber());
        fixedBoard(game,1);set(game,1,"hp",5);
        Move strike=command(game,"SWAP",null,null,6,1,7,1);assertTrue(game.makeMove("a",strike));
        assertEquals("RESULT",game.getState().phase());assertEquals("a",game.getWinner());assertFalse(game.isGameOver());
        assertEquals(2,game.getState().profile().unlocked());assertFalse(game.makeMove("a",strike));
        verify(store,times(1)).claim(eq("a"),any());
        assertTrue(game.makeMove("a",command(game,"MAP",null,null)));
        assertFalse(game.makeMove("a",command(game,"START",null,3)));
    }
    @Test void duelUsesEqualStatsRegardlessOfCampaignProgressAndPaysBothPlayers() {
        var store=store();when(store.load(anyString())).thenReturn(new WarlordsProfile(9999,1000,5,5,5,12,List.of(1,2),10,0));
        var game=game(true,store);
        assertEquals(game.getState().fighters().get(0).maxHp(),game.getState().fighters().get(1).maxHp());
        assertEquals(140,game.getState().fighters().get(0).hp());assertEquals(5,game.getState().fighters().get(0).attack());
        assertTrue(game.makeMove("b",command(game,"RESIGN",null,null)));
        assertTrue(game.isGameOver());assertEquals("a",game.getWinner());
        verify(store).claim(eq("a"),argThat(c->c.duel()&&c.won()&&c.gold()==25));
        verify(store).claim(eq("b"),argThat(c->c.duel()&&!c.won()&&c.gold()==5));
    }
    @Test void failedPersistenceCanBeRetriedWithoutAnotherBattle() {
        var store=store();when(store.claim(eq("a"),any())).thenThrow(new IllegalStateException("offline")).thenReturn(WarlordsProfile.initial());
        var game=game(true,store);assertTrue(game.makeMove("b",command(game,"RESIGN",null,null)));
        assertFalse(game.isGameOver());assertTrue(game.getState().rewardsPending());
        assertFalse(game.makeMove("a",command(game,"SWAP",null,null,0,0,0,1)));
        assertTrue(game.makeMove("a",command(game,"CLAIM",null,null)));
        assertTrue(game.isGameOver());assertFalse(game.getState().rewardsPending());
        verify(store,times(2)).claim(eq("a"),any());verify(store,times(1)).claim(eq("b"),any());
    }
    @Test void retreatDoesNotFarmRewardsAndSnapshotIsDetached() {
        var store=store();var game=game(false,store);start(game);
        var snapshot=game.getState();snapshot.board()[0][0]=999;assertNotEquals(999,game.getState().board()[0][0]);
        assertTrue(game.makeMove("a",command(game,"RETREAT",null,null)));assertEquals("MAP",game.getState().phase());
        verify(store,never()).claim(anyString(),any());
    }
    @Test void threeExpiredTurnsForfeitAndDisconnectResolvesDuel() {
        var game=game(true,store());
        for(int i=0;i<3;i++) {
            ReflectionTestUtils.setField(game,"turnDeadline",0L);
            assertTrue(game.advanceClock(Set.of("a","b")));
            if(i<2){set(game,1,"energy",100);assertTrue(game.makeMove("b",command(game,"SKILL","GUARD",null)));}
        }
        assertEquals("b",game.getWinner());assertTrue(game.isGameOver());
        var other=game(true,store());assertTrue(other.advanceClock(Set.of("a")));assertEquals("a",other.getWinner());
    }
    @Test void botCommandsAreLegalAndCombatBoardIsSeededAndStable() {
        var one=new CombatBoard(7);var two=new CombatBoard(7);
        assertArrayEquals(one.snapshot(),two.snapshot());
        assertTrue(com.brim.game.game.matchthree.MatchThreeGame.findMatches(one.snapshot()).isEmpty());
        var move=one.legalMoves().get(0);one.resolve(move);two.resolve(move);assertArrayEquals(one.snapshot(),two.snapshot());
        var game=game(false,store());start(game);
        assertTrue(game.makeMove("a",game.generateBotMove()));
        assertTrue(game.makeMove(game.getCurrentPlayerId(),game.generateBotMove()));
        assertEquals("a",game.getCurrentPlayerId());
    }
}
