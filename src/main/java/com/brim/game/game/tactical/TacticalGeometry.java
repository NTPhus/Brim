package com.brim.game.game.tactical;

import java.util.Collection;
import java.util.function.Predicate;

public final class TacticalGeometry {
    private TacticalGeometry() {}
    public static boolean clear(Collection<? extends BattleObstacle> obstacles, double x, double y, double tx, double ty,
                                Predicate<BattleObstacle> capability) {
        return obstacles.stream().noneMatch(w -> capability.test(w) && w.hitFraction(x, y, tx, ty, 0) <= 1);
    }
    public static double circleHit(double x, double y, double tx, double ty, double cx, double cy, double radius) {
        double dx = tx - x, dy = ty - y, ox = x - cx, oy = y - cy;
        double c = ox * ox + oy * oy - radius * radius;
        if (c <= 0) return 0;
        double a = dx * dx + dy * dy;
        if (a < 1e-12) return Double.POSITIVE_INFINITY;
        double b = 2 * (ox * dx + oy * dy), discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return Double.POSITIVE_INFINITY;
        double t = (-b - Math.sqrt(discriminant)) / (2 * a);
        return t >= 0 && t <= 1 ? t : Double.POSITIVE_INFINITY;
    }
}
