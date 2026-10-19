package com.brim.game.game.tactical;

/** The single allocated shell is counted during loading as well as when ready. */
public final class CannonState {
    public enum Phase { READY, LOADING, EMPTY, DISABLED }
    public record View(Phase state, int chamber, int reserve, long remainingMs) {}
    private final int capacity;
    private final double reloadSeconds;
    private int reserve;
    private boolean allocated = true;
    private double remaining;
    private boolean disabled;
    public CannonState(TacticalBalance balance) {
        capacity = balance.shellCapacity(); reloadSeconds = balance.reloadSeconds(); reserve = balance.initialShells() - 1;
    }
    public void advance(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid time");
        if (!disabled) remaining = Math.max(0, remaining - seconds);
    }
    public Phase phase() {
        if (disabled) return Phase.DISABLED;
        if (!allocated) return Phase.EMPTY;
        return remaining > 1e-8 ? Phase.LOADING : Phase.READY;
    }
    public boolean fire() {
        if (phase() != Phase.READY) return false;
        allocated = false; beginLoading(); return true;
    }
    public void beginLoading() {
        if (disabled || allocated || reserve == 0) return;
        reserve--; allocated = true; remaining = reloadSeconds;
    }
    public int refill(int available) {
        if (disabled || available <= 0) return 0;
        int accepted = Math.min(available, capacity - total());
        reserve += accepted; beginLoading(); return accepted;
    }
    public int total() { return reserve + (allocated ? 1 : 0); }
    public void disable() { disabled = true; }
    public View view() { return new View(phase(), allocated ? 1 : 0, reserve, (long) Math.ceil(Math.max(0, remaining - 1e-8) * 1000)); }
}
