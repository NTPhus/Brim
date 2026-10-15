package com.brim.game.runtime;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/** CPU-heavy searches cannot consume the realtime game worker pool or the common pool. */
@Component
public class BotExecutor {
    private final ThreadPoolExecutor workers;
    public BotExecutor(@Value("${game.bot.workers:1}") int count) {
        workers = new ThreadPoolExecutor(Math.max(1, count), Math.max(1, count), 0,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), GameRuntime.factory("chess-bot-"),
                new ThreadPoolExecutor.AbortPolicy());
    }
    public boolean submit(Runnable search) {
        try { workers.execute(search); return true; }
        catch (RejectedExecutionException busy) { return false; }
    }
    @PreDestroy public void close() { workers.shutdownNow(); }
}
