package com.brim.game.game.tactical;

/** Mutable owner-only state. Only the immutable View is sent to the controlling player. */
public final class HeavyVehicle {
    public record Motion(double distance, double hullAngle, double turretAngle) {}
    public record View(String rulesVersion, CannonState.View weapon, double speed, double fuel, double fuelCapacity,
            int repairKits, long repairRemainingMs, boolean settled, double range, long lastCommandSeq, String commandStatus) {}
    private final TacticalBalance balance;
    private final CannonState cannon;
    private double speed, fuel, settled, repairRemaining;
    private int repairKits = 1;
    private long rng, lastCommandSeq;
    private String commandStatus = "";
    public HeavyVehicle(TacticalBalance balance, long seed) {
        this.balance = balance; cannon = new CannonState(balance); fuel = balance.fuelRange(); rng = seed == 0 ? 1 : seed;
    }
    public Motion advance(double seconds, double throttle, double turning, double hull, double turret, double desiredAim) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 1) throw new IllegalArgumentException("Invalid step");
        cannon.advance(seconds);
        double target = fuel <= 1e-8 ? 0 : throttle > 0 ? balance.forwardSpeed() : throttle < 0 ? -balance.reverseSpeed() : 0;
        double rate = target == 0 || speed * target < 0 || Math.abs(target) < Math.abs(speed) ? balance.braking() : balance.acceleration();
        double changeTime = Math.min(seconds, Math.abs(target - speed) / rate);
        double next = approach(speed, target, rate * seconds);
        double distance = (speed + next) * .5 * changeTime + next * (seconds - changeTime);
        if (Math.abs(distance) > fuel) distance = Math.copySign(fuel, distance);
        double hullRate = Math.toRadians(balance.hullDegreesPerSecond()) * (1 - .6 * Math.min(1, Math.abs(next) / balance.forwardSpeed()));
        double hullNext = hull + Math.max(-1, Math.min(1, turning)) * hullRate * seconds;
        double deltaAim = wrap(desiredAim - turret);
        double turretNext = turret + Math.max(-Math.toRadians(balance.turretDegreesPerSecond()) * seconds,
                Math.min(Math.toRadians(balance.turretDegreesPerSecond()) * seconds, deltaAim));
        settled = Math.abs(next) < .1 && Math.abs(turning) < .01 && Math.abs(deltaAim) < .01 ? settled + seconds : 0;
        speed = fuel <= 1e-8 ? 0 : next;
        return new Motion(distance, hullNext, turretNext);
    }
    private static double approach(double current, double target, double amount) { return current + Math.copySign(Math.min(Math.abs(target - current), amount), target - current); }
    public static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
    public void traveled(double actualDistance) { fuel = Math.max(0, fuel - Math.abs(actualDistance)); }
    public void blocked() { speed = 0; }
    public double shotAngle(double turret) {
        rng ^= rng << 13; rng ^= rng >>> 7; rng ^= rng << 17;
        double unit = (rng >>> 11) * 0x1.0p-53;
        double spread = settled >= balance.settledSeconds() ? balance.settledSpreadDegrees() : balance.movingSpreadDegrees();
        return turret + Math.toRadians((unit * 2 - 1) * spread);
    }
    public double refuel(double available) {
        double accepted = Math.max(0, Math.min(available, balance.fuelRange() - fuel)); fuel += accepted; return accepted;
    }
    public int addRepairKits(int available) {
        int capacity = 2 - repairKits - (repairing() ? 1 : 0);
        int accepted = Math.max(0, Math.min(available, capacity));
        repairKits += accepted;
        return accepted;
    }
    public boolean startRepair(int hp, int maxHp) {
        if (repairRemaining > 0 || repairKits == 0 || hp >= Math.floor(maxHp * balance.repairCap()) || Math.abs(speed) >= .1) return false;
        repairKits--; repairRemaining = balance.repairSeconds(); return true;
    }
    public int advanceRepair(double seconds, boolean interrupted, int hp, int maxHp) {
        if (repairRemaining <= 0) return hp;
        if (interrupted) { interruptRepair(); return hp; }
        repairRemaining = Math.max(0, repairRemaining - seconds);
        if (repairRemaining < 1e-8) {
            repairRemaining = 0;
            return Math.max(hp, Math.min((int) Math.floor(maxHp * balance.repairCap()), hp + balance.repairHp()));
        }
        return hp;
    }
    public void interruptRepair() { if (repairRemaining > 0) { repairRemaining = 0; repairKits++; } }
    public boolean acceptCommand(long sequence) {
        if (sequence <= lastCommandSeq || sequence > 9_007_199_254_740_991L) return false;
        lastCommandSeq = sequence; return true;
    }
    public void commandStatus(String value) { commandStatus = value; }
    public CannonState cannon() { return cannon; }
    public boolean repairing() { return repairRemaining > 0; }
    public View view() { return new View(balance.version(), cannon.view(), speed, fuel, balance.fuelRange(), repairKits,
            (long) Math.ceil(Math.max(0, repairRemaining - 1e-8) * 1000), settled >= balance.settledSeconds(), balance.range(), lastCommandSeq, commandStatus); }
}
