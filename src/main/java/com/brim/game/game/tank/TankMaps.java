package com.brim.game.game.tank;

import java.util.*;
import com.brim.game.game.tank.TankGame.Wall;

/** Layouts use normalized coordinates; changing world dimensions preserves corridors. */
public final class TankMaps {
    public enum Layout { ARENA, WAREHOUSE, CROSSROADS, CANYON, RUINS, FORTRESS, DEPOT, BUNKERS }
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
        } else if (layout == Layout.CANYON) {
            // Alternating ridges leave wide passages at opposite ends.
            for (int n = 0; n < 4; n++) {
                block(walls, width, height, .18 + n * .17, n % 2 == 0 ? .12 : .43, .055, .45);
            }
        } else if (layout == Layout.RUINS) {
            for (double x : new double[]{.18, .58}) for (double y : new double[]{.18, .58}) {
                block(walls, width, height, x, y, .23, .04);
                block(walls, width, height, x, y + .04, .035, .17);
            }
            block(walls, width, height, .44, .41, .08, .08);
        } else if (layout == Layout.FORTRESS) {
            // Four corner bastions with entrances in the middle of every side.
            for (double x : new double[]{.25, .57}) for (double y : new double[]{.25, .70})
                block(walls, width, height, x, y, .18, .045);
            for (double x : new double[]{.25, .705}) for (double y : new double[]{.295, .57})
                block(walls, width, height, x, y, .045, .13);
        } else if (layout == Layout.DEPOT) {
            for (int x = 0; x < 4; x++) for (int y = 0; y < 3; y++)
                block(walls, width, height, .13 + x * .21, .16 + y * .27 + (x % 2) * .06, .13, .07);
        } else if (layout == Layout.BUNKERS) {
            for (double x : new double[]{.18, .68}) for (double y : new double[]{.16, .68})
                block(walls, width, height, x, y, .14, .16);
            block(walls, width, height, .43, .28, .14, .07);
            block(walls, width, height, .43, .65, .14, .07);
        } else {
            for (int x : new int[]{1, 3}) for (int y : new int[]{1, 3})
                walls.add(new Wall(width * x / 4 - 60, height * y / 4 - 40, 120, 80));
        }
        return walls;
    }
    private static void block(List<Wall> walls, int width, int height, double x, double y, double w, double h) {
        walls.add(new Wall((int) (width * x), (int) (height * y), (int) (width * w), (int) (height * h)));
    }
}
