package com.brim.game.security;

import jakarta.servlet.http.HttpSession;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One active HTTP login per account, including its WebSocket connections. */
@Component
public class AccountSessions {
    private final ConcurrentHashMap<String,HttpSession> sessions=new ConcurrentHashMap<>();
    private final SocketSessions sockets;
    public AccountSessions(SocketSessions sockets){this.sockets=sockets;}
    public synchronized void replace(String id,HttpSession next) {
        HttpSession old=sessions.put(id,next);
        if(old!=null&&old!=next){sockets.revoke(old);try{old.invalidate();}catch(IllegalStateException expired){}}
    }
    @Scheduled(fixedDelay=60000) public void clean() {
        sessions.entrySet().removeIf(entry->{try{GuestSession.require(entry.getValue());return false;}catch(RuntimeException expired){return true;}});
    }
}
