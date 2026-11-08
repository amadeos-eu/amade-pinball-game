package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.pinball.Lamps;

import java.awt.Graphics2D;

/** How a table is drawn: from the pictures Blender rendered ({@link RenderedView}), or drawn by hand ({@link TableView}). */
interface View {
    /** Draws the table, in logical coordinates; {@code g}'s transform maps them to the device. */
    void paint(Graphics2D g, Lamps lamps, int locks, double seconds, double alpha);

    /** Prepares pictures on the drawing thread, at once: for screenshots and tests. */
    void synchronous(boolean on);
}
