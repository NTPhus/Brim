package com.brim.game.game.tactical;

public final class TacticalDamage {
    private TacticalDamage() {}
    public static int radial(double distance, double radius, int damage) {
        if (!Double.isFinite(distance) || distance < 0 || distance >= radius) return 0;
        return (int) Math.round(damage * (1 - distance / radius));
    }
    public static double armor(double hullAngle, double sourceX, double sourceY, double targetX, double targetY) {
        if (Math.hypot(sourceX-targetX,sourceY-targetY)<1e-6) return 1;
        double delta = Math.abs(HeavyVehicle.wrap(Math.atan2(sourceY - targetY, sourceX - targetX) - hullAngle));
        return delta <= Math.PI / 3 ? .55 : delta >= Math.PI * 2 / 3 ? 1 : .8;
    }
}
