package com.brim.game.websocket;

import com.brim.game.game.warlords.WarlordsGame;
import com.brim.game.kafka.GameEventProducer;
import com.brim.game.service.TurnBasedGameService;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.*;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real HTTP sessions and two actual STOMP clients; Redis/Kafka are isolated test doubles. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "debug=false","spring.kafka.listener.auto-startup=false","spring.data.redis.repositories.enabled=false",
    "logging.level.root=WARN","logging.level.org.springframework=WARN","logging.level.com.brim.game=WARN",
    "logging.level.org.springframework.web.socket=ERROR","logging.level.org.springframework.kafka=ERROR",
    "spring.jpa.show-sql=false","spring.datasource.url=jdbc:h2:mem:warlords-socket;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class WarlordsSocketIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired TurnBasedGameService games;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.brim.game.security.RequestLimits limits;
    @MockBean(answer=org.mockito.Answers.RETURNS_DEEP_STUBS) RedisTemplate<String,Object> redis;
    @MockBean KafkaAdmin kafkaAdmin;
    @MockBean GameEventProducer events;
    private final Map<String,Object> stored=new ConcurrentHashMap<>();
    private final Set<Object> listed=ConcurrentHashMap.newKeySet();
    private final List<WebSocketStompClient> clients=new ArrayList<>();
    @BeforeEach @SuppressWarnings("unchecked") void mockStorage() {
        http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        jdbc.update("DELETE FROM warlords_documents");jdbc.update("DELETE FROM brim_accounts");
        ((Map<?,?>)org.springframework.test.util.ReflectionTestUtils.getField(limits,"buckets")).clear();
        ValueOperations<String,Object> values=mock(ValueOperations.class);SetOperations<String,Object> sets=mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(values);when(redis.opsForSet()).thenReturn(sets);
        when(values.get(anyString())).thenAnswer(call->stored.get(call.getArgument(0)));
        doAnswer(call->{stored.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),any());
        when(redis.delete(anyString())).thenAnswer(call->stored.remove(call.getArgument(0))!=null);
        when(sets.members("rooms:list")).thenAnswer(call->new HashSet<>(listed));
        when(sets.add(eq("rooms:list"),any())).thenAnswer(call->listed.add(call.getArgument(1))?1L:0L);
        when(sets.remove(eq("rooms:list"),any())).thenAnswer(call->listed.remove(call.getArgument(1))?1L:0L);
    }
    @AfterEach void stopClients(){listed.forEach(id->games.removeGame(String.valueOf(id)));clients.forEach(WebSocketStompClient::stop);}
    private String base(){return "http://localhost:"+port+"/api";}
    private final class User {
        String cookie,token,header,id,name;
        StompSession socket;
        final BlockingQueue<JsonNode> states=new LinkedBlockingQueue<>(),roomStates=new LinkedBlockingQueue<>(),errors=new LinkedBlockingQueue<>();
        User(String name) {
            this.name=name;csrf();
            var response=request(HttpMethod.POST,"/auth/guest",Map.of("username",name));
            assertEquals(HttpStatus.OK,response.getStatusCode());id=response.getBody().path("id").asText();csrf();
        }
        ResponseEntity<JsonNode> request(HttpMethod method,String path,Object body) {
            HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
            if(cookie!=null)headers.set(HttpHeaders.COOKIE,cookie);
            if(token!=null)headers.set(header,token);
            var response=http.exchange(base()+path,method,new HttpEntity<>(body,headers),JsonNode.class);
            var cookies=response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if(cookies!=null)for(String value:cookies)if(value.startsWith("JSESSIONID="))cookie=value.split(";",2)[0];
            return response;
        }
        void csrf(){var response=request(HttpMethod.GET,"/auth/csrf",null);assertEquals(HttpStatus.OK,response.getStatusCode());token=response.getBody().path("token").asText();header=response.getBody().path("headerName").asText();}
        void connect(String roomId) throws Exception {
            var container=jakarta.websocket.ContainerProvider.getWebSocketContainer();
            // Campaign snapshots include all chapter descriptions and the hero roster.
            container.setDefaultMaxTextMessageBufferSize(256*1024);
            var transports=List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient(container)));
            var client=new WebSocketStompClient(new SockJsClient(transports));clients.add(client);
            client.setMessageConverter(new MappingJackson2MessageConverter());client.setDefaultHeartbeat(new long[]{0,0});
            var handshake=new WebSocketHttpHeaders();handshake.set(HttpHeaders.COOKIE,cookie);
            var connect=new StompHeaders();connect.set(header,token);
            socket=client.connectAsync(base()+"/ws/game",handshake,connect,new StompSessionHandlerAdapter(){}).get(10,TimeUnit.SECONDS);
            socket.subscribe("/topic/game/"+roomId+"/state",handler(states));
            socket.subscribe("/topic/room/"+roomId+"/state",handler(roomStates));
            socket.subscribe("/user/queue/errors",handler(errors));
            send(roomId,"join",Map.of("id",id,"username",name,"ready",true));
            assertNotNull(roomStates.poll(5,TimeUnit.SECONDS),"Room join acknowledgment");
        }
        void send(String room,String action,Object body) {
            socket.send("/app/turn/"+room+"/"+action,Map.of("type",action.equals("move")?"MAKE_MOVE":action.equals("join")?"JOIN_ROOM":"PLAYER_READY",
                "roomId",room,"playerId",id,"payload",body));
        }
        JsonNode state() throws Exception {var result=states.poll(5,TimeUnit.SECONDS);assertNotNull(result,"Expected authoritative game snapshot");return result;}
        JsonNode after(long revision) throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<end) {var result=states.poll(200,TimeUnit.MILLISECONDS);if(result!=null&&result.path("revision").asLong()>revision)return result;}
            throw new AssertionError("No snapshot after revision "+revision);
        }
    }
    private StompFrameHandler handler(BlockingQueue<JsonNode> queue) {
        return new StompFrameHandler(){
            @Override public Type getPayloadType(StompHeaders headers){return JsonNode.class;}
            @Override public void handleFrame(StompHeaders headers,Object payload){queue.add((JsonNode)payload);}
        };
    }
    @Test void twoAuthenticatedPlayersShareBoardThenSettleAndRestoreResult() throws Exception {
        assertEquals(HttpStatus.UNAUTHORIZED,http.getForEntity(base()+"/warlords",String.class).getStatusCode());
        var a=new User("General A");var b=new User("General B");
        assertEquals(6,a.request(HttpMethod.GET,"/warlords",null).getBody().path("heroes").size());
        assertEquals(HttpStatus.BAD_REQUEST,a.request(HttpMethod.POST,"/warlords/hero",Map.of("heroId","INVALID")).getStatusCode());
        assertEquals("LE_HOAN",a.request(HttpMethod.POST,"/warlords/hero",Map.of("heroId","LE_HOAN")).getBody().path("heroId").asText());
        assertEquals("NGUYEN_BAC",b.request(HttpMethod.POST,"/warlords/hero",Map.of("heroId","NGUYEN_BAC")).getBody().path("heroId").asText());
        var created=a.request(HttpMethod.POST,"/rooms",Map.of("name","Sơn Hà test","gameType","WARLORDS_DUEL","maxPlayers",2));
        assertEquals(HttpStatus.OK,created.getStatusCode());String room=created.getBody().path("id").asText();
        assertEquals(HttpStatus.OK,b.request(HttpMethod.POST,"/rooms/"+room+"/join",Map.of("playerId",b.id)).getStatusCode());
        a.connect(room);b.connect(room);
        a.send(room,"ready",Map.of());b.send(room,"ready",Map.of());
        JsonNode first=a.state(),other=b.state();
        assertEquals("BATTLE",first.path("phase").asText());assertEquals(first.path("board"),other.path("board"));
        assertEquals(first.path("encounterId"),other.path("encounterId"));
        assertEquals(a.id,first.path("currentPlayer").asText());
        assertEquals("LE_HOAN",first.path("fighters").get(0).path("hero").path("id").asText());
        assertEquals(10,first.path("fighters").get(0).path("energy").asInt());
        assertEquals(140,first.path("fighters").get(1).path("maxHp").asInt());
        assertEquals(0,first.path("fighters").get(1).path("shield").asInt());
        var engine=(WarlordsGame)games.getGame(room);var move=engine.generateBotMove();
        a.send(room,"move",move);
        JsonNode moved=a.after(0),movedOther=b.after(0);
        assertEquals(1,moved.path("revision").asLong());assertEquals(moved.path("board"),movedOther.path("board"));
        assertEquals(first.path("encounterId"),moved.path("encounterId"));
        assertEquals(b.id,moved.path("currentPlayer").asText());
        a.send(room,"move",move);assertNotNull(a.errors.poll(5,TimeUnit.SECONDS),"Duplicate input rejected");
        b.send(room,"move",Map.of("action","RESIGN","encounterId",moved.path("encounterId").asText(),"revision",moved.path("revision").asLong()));
        JsonNode result=a.after(1);assertEquals("RESULT",result.path("phase").asText());assertEquals(a.id,result.path("winner").asText());
        int winnerGold=100;
        for(JsonNode grant:result.path("rewards"))if(grant.path("playerId").asText().equals(a.id))winnerGold+=grant.path("gold").asInt();
        assertTrue(winnerGold>=125);
        assertEquals(winnerGold,a.request(HttpMethod.GET,"/warlords?playerId="+b.id,null).getBody().path("profile").path("gold").asInt());
        assertEquals(105,b.request(HttpMethod.GET,"/warlords",null).getBody().path("profile").path("gold").asInt());
        assertEquals(winnerGold-60,a.request(HttpMethod.POST,"/warlords/upgrade",Map.of("type","WEAPON")).getBody().path("gold").asInt());
        assertEquals(105,b.request(HttpMethod.GET,"/warlords",null).getBody().path("profile").path("gold").asInt());
        a.states.clear();a.send(room,"join",Map.of("id",a.id,"username",a.name,"ready",true));
        assertEquals("RESULT",a.state().path("phase").asText());assertNull(games.getGame(room));
    }
    @Test void campaignPublishesHumanAndBotBoardActionsInOrder() throws Exception {
        var user=new User("Campaign General");
        var created=user.request(HttpMethod.POST,"/rooms",Map.of("name","Campaign replay","gameType","WARLORDS_CAMPAIGN","maxPlayers",1));
        assertEquals(HttpStatus.OK,created.getStatusCode());String room=created.getBody().path("id").asText();
        user.connect(room);user.send(room,"ready",Map.of());
        var map=user.state();assertEquals("MAP",map.path("phase").asText());
        user.send(room,"move",Map.of("action","SELECT_HERO","option","LE_HOAN","encounterId",map.path("encounterId").asText(),"revision",map.path("revision").asLong()));
        var selected=user.after(map.path("revision").asLong());
        assertEquals("LE_HOAN",selected.path("profile").path("heroId").asText());
        assertEquals(1,selected.path("chapters").get(0).path("botLevel").asInt());
        assertEquals(4,selected.path("chapters").get(11).path("botLevel").asInt());
        user.send(room,"move",Map.of("action","START","chapter",1,"encounterId",selected.path("encounterId").asText(),"revision",selected.path("revision").asLong()));
        var battle=user.after(selected.path("revision").asLong());
        assertEquals(140,battle.path("fighters").get(0).path("maxHp").asInt());
        assertEquals(25,battle.path("fighters").get(0).path("energy").asInt());
        // A defensive skill passes the turn without risking a random first-turn victory.
        user.send(room,"move",Map.of("action","SKILL","option","GUARD","encounterId",battle.path("encounterId").asText(),"revision",battle.path("revision").asLong()));
        var human=user.after(battle.path("revision").asLong());
        assertEquals("bot-warlord-1",human.path("currentPlayer").asText());
        assertEquals(user.id,human.path("events").get(0).path("actor").asText());
        var bot=user.after(human.path("revision").asLong());
        assertEquals(user.id,bot.path("currentPlayer").asText());
        var swap=bot.path("events").get(0);
        assertEquals("SWAP",swap.path("type").asText());assertEquals("bot-warlord-1",swap.path("actor").asText());
        assertEquals(2,swap.path("cells").size());assertEquals(8,swap.path("board").size());
        assertTrue(bot.path("revision").asLong()>human.path("revision").asLong());
        games.removeGame(room);
    }
    @Test void registrationKeepsGuestProgressAndNewLoginRestoresCampaignAfterEngineLoss() throws Exception {
        var first=new User("Account General");
        assertEquals("LE_HOAN",first.request(HttpMethod.POST,"/warlords/hero",Map.of("heroId","LE_HOAN")).getBody().path("heroId").asText());
        String room=first.request(HttpMethod.POST,"/rooms",Map.of("name","Saved campaign","gameType","WARLORDS_CAMPAIGN","maxPlayers",1)).getBody().path("id").asText();
        first.connect(room);var map=first.state();
        first.send(room,"move",Map.of("action","START","chapter",1,"encounterId",map.path("encounterId").asText(),"revision",map.path("revision").asLong()));
        var battle=first.after(map.path("revision").asLong());
        var registered=first.request(HttpMethod.POST,"/auth/register",Map.of("username","permanent_general","password","a-long-password"));
        assertEquals(HttpStatus.OK,registered.getStatusCode());assertEquals(first.id,registered.getBody().path("id").asText());assertTrue(registered.getBody().path("account").asBoolean());first.csrf();
        games.removeGame(room); // Redis room remains; simulate the previous JVM's missing runtime.
        var second=new User("New device");
        assertEquals(HttpStatus.UNAUTHORIZED,second.request(HttpMethod.POST,"/auth/login",Map.of("username","permanent_general","password","wrong-password")).getStatusCode());
        var login=second.request(HttpMethod.POST,"/auth/login",Map.of("username","permanent_general","password","a-long-password"));
        assertEquals(HttpStatus.OK,login.getStatusCode());second.id=login.getBody().path("id").asText();second.name=login.getBody().path("username").asText();second.csrf();
        assertEquals(first.id,second.id);assertEquals(HttpStatus.UNAUTHORIZED,first.request(HttpMethod.GET,"/auth/session",null).getStatusCode());
        var hub=second.request(HttpMethod.GET,"/warlords",null).getBody();assertEquals("LE_HOAN",hub.path("profile").path("heroId").asText());assertEquals("BATTLE",hub.path("savedCampaign").path("phase").asText());
        second.connect(room);var restored=second.state();
        assertEquals("BATTLE",restored.path("phase").asText());assertEquals(battle.path("board"),restored.path("board"));assertEquals(battle.path("fighters"),restored.path("fighters"));
        second.send(room,"move",Map.of("action","SKILL","option","GUARD","encounterId",restored.path("encounterId").asText(),"revision",restored.path("revision").asLong()));
        var moved=second.after(restored.path("revision").asLong());assertEquals(40,moved.path("fighters").get(0).path("shield").asInt());
    }
}
