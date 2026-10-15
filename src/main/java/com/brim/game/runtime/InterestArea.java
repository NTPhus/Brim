package com.brim.game.runtime;

/** Server-owned visibility window. Padding covers camera motion between snapshots. */
public record InterestArea(double x, double y, double halfWidth, double halfHeight) {
    public boolean contains(double px, double py) {
        return Math.abs(px - x) <= halfWidth && Math.abs(py - y) <= halfHeight;
    }
    public boolean intersects(double left, double top, double width, double height) {
        return left <= x + halfWidth && left + width >= x - halfWidth
                && top <= y + halfHeight && top + height >= y - halfHeight;
    }
}
