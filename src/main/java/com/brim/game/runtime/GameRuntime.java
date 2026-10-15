package com.brim.game.runtime;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Shared execution limits. New realtime games register a tick instead of creating threads. */
@Component
public class GameRuntime {
    private static final Logger log = LoggerFactory.getLogger(GameRuntime.class);
    private final ScheduledThreadPoolExecutor ticks;
    private final ThreadPoolExecutor output;
    private final ThreadPoolExecutor events;
    private final Set<String> pendingEvents = ConcurrentHashMap.newKeySet();
    private final int maxRooms;
    private final Map<String, ScheduledFuture<?>> rooms = new HashMap<>();
    private final Set<String> pendingOutput = ConcurrentHashMap.newKeySet();

    public GameRuntime(@Value("${game.runtime.workers:2}") int workers,
                       @Value("${game.runtime.max-rooms:64}") int maxRooms) {
        this.maxRooms = Math.max(1, maxRooms);
        ticks = new ScheduledThreadPoolExecutor(Math.max(1, workers), factory("game-tick-"));
        ticks.setRemoveOnCancelPolicy(true);
        output = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(128), factory("game-output-"), new ThreadPoolExecutor.AbortPolicy());
        events = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), factory("game-events-"), new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized boolean start(String roomId, long tickMs, BooleanSupplier tick) {
        if (!rooms.containsKey(roomId) && rooms.size() >= maxRooms) return false;
        stop(roomId);
        ScheduledFuture<?>[] reference = new ScheduledFuture<?>[1];
        ScheduledFuture<?> task = ticks.scheduleWithFixedDelay(() -> {
            try {
                if (!tick.getAsBoolean()) stopIfCurrent(roomId, reference[0]);
            } catch (Exception error) {
                // Don't let one malformed command terminate every room on a worker.
                log.error("Room tick failed: {}", roomId, error);
            }
        }, tickMs, tickMs, TimeUnit.MILLISECONDS);
        reference[0] = task;
        rooms.put(roomId, task);
        return true;
    }

    /** Drop intermediate snapshots when output is busy; the next snapshot is authoritative. */
    public void publish(String roomId, Runnable snapshot) {
        if (!pendingOutput.add(roomId)) return;
        try {
            output.execute(() -> {
                try { snapshot.run(); }
                catch (Exception error) { log.warn("Snapshot failed for {}", roomId, error); }
                finally { pendingOutput.remove(roomId); }
            });
        } catch (RejectedExecutionException busy) { pendingOutput.remove(roomId); }
    }

    /** Kafka/Redis availability must not block snapshot delivery. */
    public void publishEvent(String roomId, Runnable event) {
        if (!pendingEvents.add(roomId)) return;
        try {
            events.execute(() -> {
                try { event.run(); }
                catch (Exception error) { log.warn("Event output failed for {}", roomId, error); }
                finally { pendingEvents.remove(roomId); }
            });
        } catch (RejectedExecutionException busy) { pendingEvents.remove(roomId); }
    }

    public synchronized void stop(String roomId) {
        ScheduledFuture<?> old = rooms.remove(roomId);
        if (old != null) old.cancel(false);
    }

    private synchronized void stopIfCurrent(String roomId, ScheduledFuture<?> expected) {
        if (rooms.get(roomId) == expected) stop(roomId);
    }

    public synchronized int roomCount() { return rooms.size(); }

    public static ThreadFactory factory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy public void close() { ticks.shutdownNow(); output.shutdownNow(); events.shutdownNow(); }
}
