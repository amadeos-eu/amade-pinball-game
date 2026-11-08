package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.Ranking;
import eu.amadeos.gameengine.score.Score;
import eu.amadeos.games.pinball.bootstrap.PinballModule;
import eu.amadeos.games.pinball.domain.Game;
import eu.amadeos.games.pinball.domain.Robot;
import eu.amadeos.games.pinball.repository.PinballSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game without a window and without the loop thread: the test presses keys and calls the steps itself,
 * so everything happens in order on the test thread.
 */
class PinballGameTest {
    private static final double STEP = PinballGame.STEP;

    @TempDir
    Path dir;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final List<Arena.Result> results = new ArrayList<>();
    private PinballSettings settings;
    private HighScores scores;
    private PinballGame game;

    @BeforeEach
    void create() {
        Messages msg = Messages.load(PinballModule.class, PinballModule.MESSAGES, Locale.ENGLISH);
        Mixer mixer = new Mixer();
        mixer.setMuted(true);
        settings = new PinballSettings(dir.resolve("pinball.properties"));
        scores = new HighScores(dir.resolve("scores"), Ranking.HIGHER_WINS);
        game = new PinballGame(keyboard, Gamepads.none(), msg, settings, scores, mixer, Locale.US, Runnable::run);
        game.synchronous(true);
        game.onGameOver(results::add);
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(4);
    }

    private void hold(int key, double seconds) {
        keyboard.pressed(key, now[0]);
        steps((int) (seconds / STEP));
        keyboard.released(key, now[0]);
        steps(4);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    private void frame() {
        BufferedImage image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        game.render(g, 1);
        game.renderOverlay(g, 1);
        g.dispose();
    }

    @Test
    void theTitleChoosesTheTableAndThePlayersAndRemembersThem() {
        PinballGame.AttractScene title = assertInstanceOf(PinballGame.AttractScene.class, game.top());
        assertEquals("lantern-fair", title.machine().spec.id());
        frame();                                  // bakes the table picture
        tap(KeyEvent.VK_RIGHT);
        assertEquals("coral-deep", game.table());
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_RIGHT);                   // the last table stays
        assertEquals("orbit-yard", game.table());
        tap(KeyEvent.VK_3);
        tap(KeyEvent.VK_UP);
        tap(KeyEvent.VK_UP);                      // four at most
        assertEquals("orbit-yard", settings.table());
        assertEquals(4, settings.players());
        frame();
        tap(KeyEvent.VK_ENTER);
        PinballGame.PlayScene play = assertInstanceOf(PinballGame.PlayScene.class, game.top());
        assertEquals(4, play.game().players().size());
    }

    @Test
    void theRobotPlaysInTheTitleWithoutScoringForAnybody() {
        PinballGame.AttractScene title = assertInstanceOf(PinballGame.AttractScene.class, game.top());
        boolean launched = false;
        for (int i = 0; i < 120 * 20; i++) {
            steps(1);
            launched |= title.machine().table.field().balls().stream().anyMatch(b -> !b.captive && b.y < 300);
        }
        assertTrue(launched, "the robot launched a ball");
        assertTrue(game.top() == title);
        String text = game.displays().messageText();
        assertTrue(text.startsWith("LANTERN FAIR") || text.startsWith("PRESS ENTER") || text.contains("HIGH SCORE"), text);
        assertEquals(List.of(), results);
    }

    @Test
    void theShiftKeysWorkTheirOwnFlippers() {
        game.play("lantern-fair", 1);
        var table = game.machine("lantern-fair").table;
        keyboard.pressed(Keyboard.LEFT_SHIFT, now[0]);
        steps(12);
        assertTrue(table.leftFlippers().stream().allMatch(f -> f.held()));
        assertTrue(table.rightFlippers().stream().noneMatch(f -> f.held()));
        keyboard.released(Keyboard.LEFT_SHIFT, now[0]);
        keyboard.pressed(Keyboard.RIGHT_SHIFT, now[0]);
        steps(12);
        assertTrue(table.rightFlippers().stream().allMatch(f -> f.held()));
        assertTrue(table.leftFlippers().stream().noneMatch(f -> f.held()));
    }

    @Test
    void spaceHeldAndLetGoLaunchesTheBall() {
        PinballGame.PlayScene play = game.play("coral-deep", 1);
        assertTrue(play.game().machine().ballInLane());
        hold(KeyEvent.VK_SPACE, 0.8);
        steps(120);
        assertEquals(Game.Phase.PLAY, play.game().phase());
    }

    @Test
    void escPausesAndQEndsTheGame() {
        game.play("orbit-yard", 1);
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(PinballGame.PauseScene.class, game.top());
        frame();
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(PinballGame.PlayScene.class, game.top());
        tap(KeyEvent.VK_P);
        tap(KeyEvent.VK_Q);
        assertInstanceOf(PinballGame.AttractScene.class, game.top());
        assertEquals(List.of(), results);
    }

    @Test
    void aFinishedGameReportsTheScoresAndEnterLeadsBackToTheTitle() {
        PinballGame.PlayScene play = game.play("lantern-fair", 2);
        Robot robot = new Robot(new Random(3));
        for (int i = 0; i < 120 * 1200 && play.game().phase() != Game.Phase.GAME_OVER; i++) {
            now[0] += (long) (STEP * 1e9);
            play.step(STEP, robot.input(play.game().machine(), STEP));
        }
        assertEquals(Game.Phase.GAME_OVER, play.game().phase());
        steps((int) ((PinballGame.OVER_SECONDS + 0.2) / STEP));
        PinballGame.OverScene over = assertInstanceOf(PinballGame.OverScene.class, game.top());
        assertEquals(1, results.size());
        assertEquals("lantern-fair", results.getFirst().table());
        assertEquals(2, results.getFirst().scores().size());
        assertEquals(over.result(), results.getFirst());
        frame();
        steps(130);
        tap(KeyEvent.VK_ENTER);
        assertInstanceOf(PinballGame.AttractScene.class, game.top());
    }

    @Test
    void theTitleShowsTheTablesHighScore() {
        scores.add("lantern-fair", new Score("Ada", 1_234_560, Instant.EPOCH));
        game.scoresChanged();
        steps((int) (3.2 / STEP));
        assertEquals("HIGH SCORE|1,234,560", game.displays().messageText());
    }
}
