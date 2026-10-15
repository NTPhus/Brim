package com.brim.game.game.tank;

import java.util.*;
import com.brim.game.game.tank.TankGame.Wall;

/** Layouts use normalized coordinates; changing world dimensions preserves corridors. */
public final class TankMaps {
    public enum Layout { ARENA, WAREHOUSE, CROSSROADS }
    private TankMaps() {}
    public static Layout parse(String value) {
        try { return Layout.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return Layout.ARENA; }
    }
    public static List<Wall> walls(Layout layout, int width, int height) {
        List<Wall> walls = new ArrayList<>(List.of(new Wall(0, 0, width, 20), new Wall(0, height - 20, width, 20),
                new Wall(0, 0, 20, height), new Wall(width - 20, 0, 20, height)));
        if (layout == Layout.WAREHOUSE) {
            for (int x = 1; x <= 4; x++) for (int y = 1; y <= 3; y++)
                walls.add(new Wall(width * x / 5 - 45, height * y / 4 - 60, 90, 120));
        } else if (layout == Layout.CROSSROADS) {
            for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1})
                walls.add(new Wall(width / 2 + x * width / 5 - width / 12,
                        height / 2 + y * height / 5 - height / 12, width / 6, height / 6));
        } else {
            for (int x : new int[]{1, 3}) for (int y : new int[]{1, 3})
                walls.add(new Wall(width * x / 4 - 60, height * y / 4 - 40, 120, 80));
        }
        return walls;
    }
}
