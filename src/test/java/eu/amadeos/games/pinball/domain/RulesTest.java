package eu.amadeos.games.pinball.domain;

import eu.amadeos.gameengine.pinball.Ball;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of Lantern Fair, told by the table's switches directly: the fortune saucer starts modes, two
 * ramps light the lock, three locked balls start the multiball.
 */
class RulesTest {
    private static final double STEP = 1 / 120.0;

    private Table table;
    private Game game;

    @BeforeEach
    void launch() {
        table = new Table(TableFile.load("lantern-fair"));
        game = new Game(table, new Random(1));
        game.start(1);
        for (int i = 0; i < 60; i++) game.step(STEP, new Input(false, false, true, 0, false, false));
        for (int i = 0; i < 240 && game.phase() == Game.Phase.PLUNGE; i++) game.step(STEP, Input.NONE);
        assertEquals(Game.Phase.PLAY, game.phase());
        game.messages();
        game.sounds();
    }

    private Ball ball() {
        return table.field().balls().stream().filter(b -> !b.captive).findFirst().orElseThrow();
    }

    @Test
    void theStartHoleStartsTheSelectedModeAndItsShotsFinishIt() {
        game.caught("fortune", ball());
        List<Rules.Running> running = game.rules().running();
        assertEquals(1, running.size());
        assertEquals("Ferris Wheel", running.getFirst().spec.name());
        assertTrue(game.messages().stream().anyMatch(m -> m.key().equals("dmd.mode-start")));
        for (int i = 0; i < 3; i++) game.rode("coaster", ball());
        assertTrue(game.rules().running().isEmpty());
        assertTrue(game.current().played[0]);
        assertTrue(game.sounds().contains(Sfx.MODE_DONE));
    }

    @Test
    void twoRampsLightTheLockAndThreeLocksStartTheMultiball() {
        for (int lock = 1; lock <= 3; lock++) {
            game.rode("coaster", ball());
            game.rode("dipper", ball());
            assertTrue(game.current().lockLit, "lock lit before lock " + lock);
            game.caught("ferris", ball());
            assertFalse(game.current().lockLit);
        }
        assertTrue(game.rules().multiball());
        assertTrue(game.sounds().contains(Sfx.MULTIBALL));
        // the extra balls come out of the plunger lane one after another
        for (int i = 0; i < 120 * 6; i++) game.step(STEP, Input.NONE);
        assertTrue(table.field().inPlay() >= 2, "balls in play: " + table.field().inPlay());
    }

    @Test
    void aBallLostSoonAfterTheLaunchComesBack() {
        assertTrue(game.ballSaveLit());
        Ball b = ball();
        b.state = Ball.State.LOST;
        game.step(STEP, Input.NONE);
        assertEquals(Game.Phase.PLUNGE, game.phase());
        assertEquals(1, game.current().ball);
        assertTrue(game.messages().stream().anyMatch(m -> m.key().equals("dmd.ball-saved")));
    }

    @Test
    void threeNudgesTooManyTiltTheBall() {
        for (int i = 0; i < 6 && !game.tilted(); i++) {
            game.step(STEP, new Input(false, false, false, 1, false, false));
            for (int k = 0; k < 6; k++) game.step(STEP, Input.NONE);
        }
        assertTrue(game.tilted());
        assertTrue(game.sounds().contains(Sfx.TILT));
    }
}
