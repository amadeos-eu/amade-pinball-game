package eu.amadeos.games.pinball.ui.main;

import eu.amadeos.gameengine.swing.RenderMode;

import java.util.Optional;

/** What {@link PinballPresenter} needs from the window; tests replace it. The table itself is drawn by the game. */
public interface PinballView {
    /** Shows the settings in the menu. */
    void showOptions(boolean sound, RenderMode mode);

    /** Switches the canvas to a render mode. */
    void useRenderMode(RenderMode mode);

    /** Asks a player for their name after a high score; empty if cancelled. */
    Optional<String> askName(String message);

    /** Shows the best games of the tables, {@code table} first; {@code highlight} -1 for no new entry. */
    void showScores(String table, int highlight);

    /** The About dialog, with a line on the gamepads. */
    void showAbout(String gamepads);
}
