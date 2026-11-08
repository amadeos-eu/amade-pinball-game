package eu.amadeos.games.pinball.domain;

/**
 * The buttons of the cabinet for one step: the flipper buttons, the plunger and a nudge of the table
 * ({@code nudgeX} -1 left, 1 right; {@code nudgeUp} forwards), each nudge only on the step it was pressed.
 */
public record Input(boolean left, boolean right, boolean plunger, int nudgeX, boolean nudgeUp, boolean launch) {
    public static final Input NONE = new Input(false, false, false, 0, false, false);
}
