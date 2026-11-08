package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.pinball.DotMatrix;
import eu.amadeos.games.pinball.bootstrap.PinballModule;
import eu.amadeos.games.pinball.domain.Game;
import eu.amadeos.games.pinball.domain.Input;
import eu.amadeos.games.pinball.domain.Table;
import eu.amadeos.games.pinball.domain.TableFile;
import eu.amadeos.gameengine.pinball.Ball;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplaysTest {
    private final DotMatrix status = new DotMatrix(128, 32);
    private final DotMatrix messages = new DotMatrix(128, 32);
    private Displays displays;
    private Table table;
    private Game game;

    @BeforeEach
    void create() {
        Messages msg = Messages.load(PinballModule.class, PinballModule.MESSAGES, Locale.US);
        displays = new Displays(msg, Locale.US, status, messages);
        table = new Table(TableFile.load("lantern-fair"));
        game = new Game(table, new Random(1));
        game.start(2);
    }

    private void step(double seconds) {
        for (double t = 0; t < seconds; t += 1 / 120.0) {
            game.step(1 / 120.0, Input.NONE);
            displays.update(1 / 120.0, game, table.spec());
        }
    }

    @Test
    void eachMessageShowsForItsTimeThenTheScore() {
        step(0.1);
        assertEquals("PLAYER 1|BALL 1", displays.messageText());
        step(2.6);
        assertEquals("0|PLAYER 1  BALL 1", displays.messageText());
        assertEquals("MODES 0 OF 8|NO BALLS LOCKED", displays.statusText());
        assertTrue(messages.on(64, 13) || anyDot(messages), "the text is on the dots");
    }

    @Test
    void aRunningModeShowsItsShotsAndTime() {
        step(0.2);
        Ball ball = table.field().balls().stream().filter(b -> !b.captive).findFirst().orElseThrow();
        game.caught("fortune", ball);
        step(0.05);
        assertEquals("FERRIS WHEEL 0 OF 3|0:45", displays.statusText());
        step(0.5);                                // the message showing makes way soon
        assertEquals("FERRIS WHEEL|3 SHOTS IN 45 SEC", displays.messageText());
    }

    @Test
    void theAttractModeTurnsItsPages() {
        displays.attract(0.1, table.spec(), 0, 2);
        assertEquals("LANTERN FAIR|EASY", displays.messageText());
        displays.attract(3, table.spec(), 1_500_000, 2);
        assertEquals("HIGH SCORE|1,500,000", displays.messageText());
        displays.attract(3, table.spec(), 0, 2);
        assertEquals("PRESS ENTER|2 PLAYERS", displays.messageText());
        assertEquals("8 MODES|GRAND PARADE", displays.statusText());
    }

    private static boolean anyDot(DotMatrix dmd) {
        for (int y = 0; y < dmd.rows(); y++) for (int x = 0; x < dmd.columns(); x++) if (dmd.on(x, y)) return true;
        return false;
    }
}
