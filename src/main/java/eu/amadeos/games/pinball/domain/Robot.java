package eu.amadeos.games.pinball.domain;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.pinball.Flipper;

import java.util.Random;

/**
 * A player for the attract mode and the tests: it plunges the ball, holds a flipper up when a ball comes
 * down towards it, and lets go a moment later, so that the ball is thrown back up the table. It does not
 * aim, so it makes about as many shots as a beginner.
 */
public final class Robot {
    private final Random random;
    private double pulled;
    private double pull;
    private double holdLeft, holdRight;

    public Robot(Random random) {
        this.random = random;
        this.pull = 0.4 + random.nextDouble() * 0.6;
    }

    // --8<-- [start:robot]
    public Input input(Table table, double seconds) {
        holdLeft = Math.max(0, holdLeft - seconds);
        holdRight = Math.max(0, holdRight - seconds);
        for (Ball b : table.field().balls()) {
            if (b.captive || b.state != Ball.State.FREE || b.vy < -50) continue;
            for (Flipper f : table.field().flippers()) {
                if (f.layer != b.layer) continue;
                double reach = f.length + 8;
                double dx = b.x - f.px, dy = b.y - f.py;
                // the ball is over the flipper's length and close above it
                double along = (dx * Math.cos(f.rest) + dy * Math.sin(f.rest)) / f.length;
                if (along < 0.15 || along > 1.1 || Math.hypot(dx, dy) > reach) continue;
                double side = -dx * Math.sin(f.rest) + dy * Math.cos(f.rest);
                if (Math.abs(side) > 13) continue;
                if (table.leftFlippers().contains(f)) holdLeft = 0.18 + random.nextDouble() * 0.06;
                else holdRight = 0.18 + random.nextDouble() * 0.06;
            }
        }
        boolean plunger = false;
        if (table.ballInLane() && pulled < pull) {
            pulled += seconds;
            plunger = true;
        } else if (pulled > 0) {
            pulled = 0;
            pull = 0.4 + random.nextDouble() * 0.6;
        }
        return new Input(holdLeft > 0, holdRight > 0, plunger, 0, false, false);
    }
    // --8<-- [end:robot]
}
