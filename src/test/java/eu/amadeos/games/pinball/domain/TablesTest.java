package eu.amadeos.games.pinball.domain;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.pinball.Playfield;
import eu.amadeos.gameengine.pinball.Zone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every table: its file reads, the robot plays whole games on it, and every ramp can be made from a flipper. */
class TablesTest {
    static List<String> tables() {
        return TableFile.TABLES;
    }

    @ParameterizedTest
    @MethodSource("tables")
    void theFileHasModesAWizardAndThePartsOfAMachine(String id) {
        TableSpec spec = TableFile.load(id);
        assertEquals(id, spec.id());
        assertEquals(8, spec.modes().size());
        assertNotNull(spec.wizard());
        assertEquals(8, spec.palette().length);
        Table table = new Table(spec);
        assertTrue(table.field().flippers().size() >= 3, "flippers");
        assertTrue(table.ramps().size() >= 2, "ramps");
        assertFalse(spec.parts("bumper").isEmpty(), "pop bumpers");
        assertFalse(spec.rule("start", "").isEmpty(), "a start hole");
        assertFalse(spec.rule("lock", "").isEmpty(), "a lock");
    }

    // --8<-- [start:robot]
    /** The robot plays three whole games; no ball ever leaves the cabinet and every game ends. */
    @ParameterizedTest
    @MethodSource("tables")
    void theRobotPlaysWholeGamesAndNoBallLeavesTheTable(String id) {
        for (long seed = 1; seed <= 3; seed++) {
            Table table = new Table(TableFile.load(id));
            Game game = new Game(table, new Random(seed));
            Robot robot = new Robot(new Random(seed + 10));
            game.start(1);
            double t = 0;
            while (t < 900 && game.phase() != Game.Phase.GAME_OVER) {
                game.step(1 / 120.0, robot.input(table, 1 / 120.0));
                t += 1 / 120.0;
                for (Ball b : table.field().balls()) {
                    assertTrue(b.x > -2 && b.x < Table.WIDTH + 2 && b.y > -45 && b.y < Table.LENGTH + 10,
                            () -> id + ": a ball at " + b.x + ", " + b.y + " on layer " + b.layer);
                }
            }
            assertEquals(Game.Phase.GAME_OVER, game.phase(), id + " seed " + seed + " did not end");
            assertTrue(game.current().score > 0);
        }
    }
    // --8<-- [end:robot]

    /** Somewhere between a weak and a full plunge lies the skill shot. */
    @ParameterizedTest
    @MethodSource("tables")
    void somePlungeMakesTheSkillShot(String id) {
        boolean made = false;
        for (double hold = 0.58; hold <= 0.75 && !made; hold += 0.005) {
            Table table = new Table(TableFile.load(id));
            Game game = new Game(table, new Random(1));
            game.start(1);
            for (double t = 0; t < 4 && !made; t += 1 / 120.0) {
                game.step(1 / 120.0, new Input(false, false, t < hold, 0, false, false));
                made = game.sounds().contains(Sfx.SKILL_SHOT);
            }
        }
        assertTrue(made);
    }

    /** A ball shot from the left or the right flipper at the mouth of each ramp rides it to its end. */
    @ParameterizedTest
    @MethodSource("tables")
    void everyRampCanBeMadeFromAFlipper(String id) {
        List<String> missed = new ArrayList<>();
        for (String ramp : new Table(TableFile.load(id)).ramps().keySet()) {
            boolean made = false;
            for (double fx : new double[]{90, 130}) {
                for (double v : new double[]{1000, 1200, 1400}) {
                    if (!made) made = rides(id, ramp, fx, v);
                }
            }
            if (!made) missed.add(ramp);
        }
        assertEquals(List.of(), missed);
    }

    private static boolean rides(String id, String ramp, double fx, double v) {
        Table table = new Table(TableFile.load(id));
        Zone mouth = table.field().zone(ramp + "-mouth");
        boolean[] rode = {false};
        table.field().listen(new Playfield.Events() {
            @Override
            public void rode(String track, Ball ball) {
                if (track.equals(ramp)) rode[0] = true;
            }
        });
        double sx = fx, sy = 330;
        double tx = mouth.x + mouth.dx * 4, ty = mouth.y + mouth.dy * 4;
        double l = Math.hypot(tx - sx, ty - sy);
        Ball b = table.field().add(new Ball(sx, sy, Table.BALL_RADIUS));
        b.vx = (tx - sx) / l * v;
        b.vy = (ty - sy) / l * v;
        for (double t = 0; t < 2 && !rode[0]; t += 1 / 120.0) table.step(1 / 120.0);
        return rode[0];
    }
}
