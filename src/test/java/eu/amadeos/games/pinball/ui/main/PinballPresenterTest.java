package eu.amadeos.games.pinball.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.Ranking;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.pinball.bootstrap.PinballModule;
import eu.amadeos.games.pinball.repository.PinballSettings;
import eu.amadeos.games.pinball.ui.game.Arena;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PinballPresenterTest {
    @TempDir
    Path dir;

    private final List<String> log = new ArrayList<>();
    private Consumer<Arena.Result> gameOver;
    private PinballPresenter presenter;
    private HighScores scores;
    private PinballSettings settings;

    private final PinballView view = new PinballView() {
        @Override
        public void showOptions(boolean sound, RenderMode mode) {
            log.add("options " + sound + " " + mode);
        }

        @Override
        public void useRenderMode(RenderMode mode) {
            log.add("mode " + mode);
        }

        @Override
        public Optional<String> askName(String message) {
            log.add("ask " + message);
            return message.contains("Player 2") ? Optional.empty() : Optional.of("Ada");
        }

        @Override
        public void showScores(String table, int highlight) {
            log.add("scores " + table + " " + highlight);
        }

        @Override
        public void showAbout(String gamepads) {
            log.add("about " + gamepads);
        }
    };

    private final Arena arena = new Arena() {
        @Override
        public void showTitle() {
            log.add("title");
        }

        @Override
        public void newGame() {
            log.add("new");
        }

        @Override
        public void pause() {
            log.add("pause");
        }

        @Override
        public void setSound(boolean on) {
            log.add("sound " + on);
        }

        @Override
        public String table() {
            return "coral-deep";
        }

        @Override
        public String gamepads() {
            return "Gamepads: none";
        }

        @Override
        public void onGameOver(Consumer<Result> listener) {
            gameOver = listener;
        }

        @Override
        public void scoresChanged() {
            log.add("changed");
        }
    };

    @BeforeEach
    void create() {
        Messages msg = Messages.load(PinballModule.class, PinballModule.MESSAGES, Locale.US);
        settings = new PinballSettings(dir.resolve("pinball.properties"));
        scores = new HighScores(dir.resolve("scores"), Ranking.HIGHER_WINS);
        presenter = new PinballPresenter(view, arena, msg, settings, scores, () -> Instant.EPOCH);
    }

    @Test
    void startShowsTheSettingsAndTheTitle() {
        settings.setSound(false);
        presenter.start();
        assertEquals(List.of("options false DIRECT", "mode DIRECT", "sound false", "title"), log);
    }

    @Test
    void theMenuCommandsReachTheGame() {
        presenter.newGame();
        presenter.chooseTable();
        presenter.focusLost();
        presenter.showScores();
        presenter.showAbout();
        assertEquals(List.of("new", "title", "pause", "scores coral-deep -1", "about Gamepads: none"), log);
    }

    @Test
    void eachPlayerWithAHighScoreIsAskedForTheirName() {
        presenter.start();
        log.clear();
        gameOver.accept(new Arena.Result("orbit-yard", List.of(1_250_000L, 900_000L, 0L)));
        assertEquals(List.of("ask Player 1: a high score, 1,250,000 points!", "ask Player 2: a high score, 900,000 points!",
                "changed", "scores orbit-yard 0"), log);
        assertEquals(1, scores.list("orbit-yard").size());
        assertEquals("Ada", scores.list("orbit-yard").getFirst().name());
    }

    @Test
    void oneLonePlayerIsAskedWithoutANumber() {
        presenter.start();
        log.clear();
        gameOver.accept(new Arena.Result("lantern-fair", List.of(40_000L)));
        assertEquals(List.of("ask A high score: 40,000 points!", "changed", "scores lantern-fair 0"), log);
    }
}
