package eu.amadeos.games.pinball.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.Score;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.pinball.repository.PinballSettings;
import eu.amadeos.games.pinball.ui.game.Arena;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Everything around the table, without Swing: the menu commands, the settings, pausing when the window
 * loses the focus, and the best games of each table. All methods run on the Swing thread.
 */
public final class PinballPresenter {
    private final PinballView view;
    private final Arena arena;
    private final Messages msg;
    private final PinballSettings settings;
    private final HighScores scores;
    private final Supplier<Instant> now;

    @Inject
    public PinballPresenter(PinballView view, Arena arena, Messages msg, PinballSettings settings, HighScores scores) {
        this(view, arena, msg, settings, scores, Instant::now);
    }

    PinballPresenter(PinballView view, Arena arena, Messages msg, PinballSettings settings, HighScores scores,
                     Supplier<Instant> now) {
        this.view = view;
        this.arena = arena;
        this.msg = msg;
        this.settings = settings;
        this.scores = scores;
        this.now = now;
    }

    public void start() {
        boolean sound = settings.sound();
        RenderMode mode = settings.renderMode();
        view.showOptions(sound, mode);
        view.useRenderMode(mode);
        arena.setSound(sound);
        arena.onGameOver(this::gameOver);
        arena.showTitle();
    }

    public void newGame() {
        arena.newGame();
    }

    /** Back to the title, where the table is chosen. */
    public void chooseTable() {
        arena.showTitle();
    }

    public void pause() {
        arena.pause();
    }

    /** The window lost the focus: nobody is at the buttons, so a running game waits. */
    public void focusLost() {
        arena.pause();
    }

    public void setSound(boolean on) {
        settings.setSound(on);
        arena.setSound(on);
    }

    public void setRenderMode(RenderMode mode) {
        settings.setRenderMode(mode);
        view.useRenderMode(mode);
    }

    public void showScores() {
        view.showScores(arena.table(), -1);
    }

    public void showAbout() {
        view.showAbout(arena.gamepads());
    }

    // --8<-- [start:over]
    /** A game ended: each player whose score is good enough enters the table's list, one after another. */
    void gameOver(Arena.Result result) {
        int place = -1;
        boolean several = result.scores().size() > 1;
        for (int i = 0; i < result.scores().size(); i++) {
            long points = result.scores().get(i);
            if (points <= 0 || !scores.qualifies(result.table(), points)) continue;
            String text = several ? msg.format("scores.reached-player", i + 1, points) : msg.format("scores.reached", points);
            var name = view.askName(text);
            if (name.isPresent()) place = scores.add(result.table(), new Score(name.get(), points, now.get()));
        }
        if (place >= 0) {
            arena.scoresChanged();
            view.showScores(result.table(), place);
        }
    }
    // --8<-- [end:over]
}
