package com.brim.game.runtime;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GameRuntimeTest {
    @Test void enforcesRoomLimitAndFreesCapacity() {
        GameRuntime runtime = new GameRuntime(1, 1);
        try {
            assertTrue(runtime.start("one", 1000, () -> true));
            assertFalse(runtime.start("two", 1000, () -> true));
            assertEquals(1, runtime.roomCount());
            runtime.stop("one");
            assertTrue(runtime.start("two", 1000, () -> true));
        } finally { runtime.close(); }
    }
    @Test void oldTickCannotCancelReplacementRoom() throws Exception {
        GameRuntime runtime = new GameRuntime(1, 1);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), replacement = new CountDownLatch(2);
        try {
            runtime.start("room", 1, () -> {
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return false;
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(runtime.start("room", 1, () -> { replacement.countDown(); return true; }));
            release.countDown();
            assertTrue(replacement.await(2, TimeUnit.SECONDS));
            assertEquals(1, runtime.roomCount());
        } finally { release.countDown(); runtime.close(); }
    }
    @Test void onlyOneSnapshotPerRoomIsQueuedAtATime() throws Exception {
        GameRuntime runtime = new GameRuntime(1, 1);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        try {
            assertTrue(runtime.tryPublish("room", () -> {
                executed.incrementAndGet(); entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                completed.countDown();
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) assertFalse(runtime.tryPublish("room", executed::incrementAndGet));
            release.countDown();
            assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertEquals(1, executed.get());
            CountDownLatch terminal = new CountDownLatch(1);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!runtime.tryPublish("room", terminal::countDown) && System.nanoTime() < deadline) Thread.yield();
            assertTrue(terminal.await(2, TimeUnit.SECONDS));
        } finally { release.countDown(); runtime.close(); }
    }
    @Test void botSearchQueueIsBounded() throws Exception {
        BotExecutor bots = new BotExecutor(1);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            assertTrue(bots.submit(() -> {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 32; i++) assertTrue(bots.submit(() -> {}));
            assertFalse(bots.submit(() -> fail("Rejected work must not run")));
        } finally { release.countDown(); bots.close(); }
    }
    @Test void spatialIndexHandlesNegativeCoordinatesAndCellBoundaries() {
        SpatialIndex<String> index = new SpatialIndex<>(64);
        index.add(63, 0, "left"); index.add(65, 0, "right"); index.add(-1, -1, "negative");
        assertTrue(index.anyMatch(64, 0, 2, "left"::equals));
        assertTrue(index.anyMatch(64, 0, 2, "right"::equals));
        assertTrue(index.anyMatch(0, 0, 2, "negative"::equals));
        assertFalse(index.anyMatch(1000, 1000, 2, x -> true));
    }
}
