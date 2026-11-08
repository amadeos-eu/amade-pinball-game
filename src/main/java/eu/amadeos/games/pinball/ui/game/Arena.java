package eu.amadeos.games.pinball.ui.game;

import java.util.List;
import java.util.function.Consumer;

/**
 * What the presenter may ask of the running game. The game runs on its own thread; every method here is
 * safe to call from the Swing thread and takes effect at the start of the game's next step.
 */
public interface Arena {
    /** The scores of a finished game: the table and each player's points, in the order they played. */
    record Result(String table, List<Long> scores) {
    }

    /** Back to the title, where the robot plays the chosen table. */
    void showTitle();

    /** A new game on the chosen table, with as many players as last time. */
    void newGame();

    /** Pauses a running game; does nothing elsewhere. */
    void pause();

    void setSound(boolean on);

    /** The table on the screen now. */
    String table();

    /** Which gamepads the game sees, for the About dialog. */
    String gamepads();

    /** Called on the Swing thread when a game ends. */
    void onGameOver(Consumer<Result> listener);

    /** The best score has changed; the title shows it. */
    void scoresChanged();
}
