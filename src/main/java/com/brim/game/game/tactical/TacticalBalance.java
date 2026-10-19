package com.brim.game.game.tactical;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Versioned world-units/seconds profile; deliberately independent of transport and tick rate. */
public record TacticalBalance(String version, int playerHp, int enemyHp, double forwardSpeed, double reverseSpeed,
        double acceleration, double braking, double hullDegreesPerSecond, double turretDegreesPerSecond,
        int initialShells, int shellCapacity, double reloadSeconds, double range, double projectileSpeed,
        int shellDamage, int blastRadius, double settledSeconds, double movingSpreadDegrees, double settledSpreadDegrees,
        double fuelRange, double repairSeconds, int repairHp, double repairCap, double sightRange) {
    public TacticalBalance {
        if (version == null || version.isBlank() || playerHp <= 0 || enemyHp <= 0 || initialShells < 1
                || shellCapacity < initialShells || shellDamage <= 0 || blastRadius <= 0 || repairHp <= 0)
            throw new IllegalArgumentException("Invalid tactical inventory/health profile");
        for (double v : new double[]{forwardSpeed, reverseSpeed, acceleration, braking, hullDegreesPerSecond,
                turretDegreesPerSecond, reloadSeconds, range, projectileSpeed, settledSeconds,
                movingSpreadDegrees, settledSpreadDegrees, fuelRange, repairSeconds, repairCap, sightRange})
            if (!Double.isFinite(v) || v <= 0) throw new IllegalArgumentException("Invalid tactical rate");
        if (repairCap > 1 || settledSpreadDegrees > movingSpreadDegrees) throw new IllegalArgumentException("Invalid tactical limits");
    }
    private static final class Standard {
        static final TacticalBalance VALUE = load();
        private static TacticalBalance load() {
            try (var stream = TacticalBalance.class.getResourceAsStream("/games/tactical-standard.json")) {
                if (stream == null) throw new IllegalStateException("Missing tactical balance");
                return new ObjectMapper().readValue(stream, TacticalBalance.class);
            } catch (Exception error) { throw new IllegalStateException("Invalid tactical balance", error); }
        }
    }
    public static TacticalBalance standard() { return Standard.VALUE; }
}
