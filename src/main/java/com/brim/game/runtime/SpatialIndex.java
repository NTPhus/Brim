package com.brim.game.runtime;

import java.util.*;
import java.util.function.Predicate;

/** Uniform grid broad phase: queries examine nearby cells rather than every body segment. */
public final class SpatialIndex<T> {
    private final double cellSize;
    private final Map<Long, List<T>> cells = new HashMap<>();
    public SpatialIndex(double cellSize) { this.cellSize = cellSize; }
    private int cell(double position) { return (int) Math.floor(position / cellSize); }
    private long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
    public void add(double x, double y, T item) {
        cells.computeIfAbsent(key(cell(x), cell(y)), ignored -> new ArrayList<>()).add(item);
    }
    public boolean anyMatch(double x, double y, double radius, Predicate<T> predicate) {
        for (int cx = cell(x - radius); cx <= cell(x + radius); cx++) {
            for (int cy = cell(y - radius); cy <= cell(y + radius); cy++) {
                for (T item : cells.getOrDefault(key(cx, cy), List.of())) if (predicate.test(item)) return true;
            }
        }
        return false;
    }
}
