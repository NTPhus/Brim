package com.brim.game.game;

import com.brim.game.game.tank.TankMaps;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TankMapsTest {
    @Test void everyLayoutHasConnectedTankPassagesAtMinimumAndDefaultWorldSizes() {
        assertEquals(8, TankMaps.Layout.values().length);
        for (var layout : TankMaps.Layout.values()) {
            assertEquals(layout, TankMaps.parse(layout.name().toLowerCase(java.util.Locale.ROOT)));
            for (int[] size : new int[][]{{800, 600}, {3600, 2400}}) {
                var walls = TankMaps.walls(layout, size[0], size[1]);
                int cols = (size[0] - 80) / 20 + 1, rows = (size[1] - 80) / 20 + 1;
                boolean[] open = new boolean[cols * rows];
                int total = 0, start = -1;
                for (int cell = 0; cell < open.length; cell++) {
                    int x = 40 + cell % cols * 20, y = 40 + cell / cols * 20;
                    open[cell] = walls.stream().noneMatch(w -> w.intersects(x, y, 20));
                    if (open[cell]) { total++; start = cell; }
                }
                assertTrue(total > cols * rows / 4, layout + " should have room to maneuver");
                var queue = new ArrayDeque<Integer>(); queue.add(start); open[start] = false;
                int visited = 0;
                while (!queue.isEmpty()) {
                    int cell = queue.removeFirst(); visited++;
                    for (int next : new int[]{cell - cols, cell + cols, cell % cols > 0 ? cell - 1 : -1, cell % cols < cols - 1 ? cell + 1 : -1}) {
                        if (next < 0 || next >= open.length || !open[next]) continue;
                        open[next] = false; queue.addLast(next);
                    }
                }
                assertEquals(total, visited, layout + " must not trap tanks in disconnected pockets at " + size[0]);
            }
        }
        assertEquals(TankMaps.Layout.ARENA, TankMaps.parse(null));
    }
}
