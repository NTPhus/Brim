package com.brim.game.game.tactical;

/** Distinct terrain capabilities, including cover that obscures vision but does not stop a shell. */
public interface BattleObstacle {
    double left(); double top(); double width(); double height();
    boolean blocksMovement(); boolean blocksVision(); boolean blocksProjectile(); boolean blocksBlast();
    default double hitFraction(double fromX, double fromY, double toX, double toY, double radius) {
        double enter = 0, exit = 1;
        double[] origin = {fromX, fromY}, delta = {toX - fromX, toY - fromY};
        double[] min = {left() - radius, top() - radius}, max = {left() + width() + radius, top() + height() + radius};
        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(delta[axis]) < 1e-9) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) return Double.POSITIVE_INFINITY;
            } else {
                double near = (min[axis] - origin[axis]) / delta[axis], far = (max[axis] - origin[axis]) / delta[axis];
                enter = Math.max(enter, Math.min(near, far)); exit = Math.min(exit, Math.max(near, far));
                if (enter > exit) return Double.POSITIVE_INFINITY;
            }
        }
        return enter;
    }
}
