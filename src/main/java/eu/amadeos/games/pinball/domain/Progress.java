package eu.amadeos.games.pinball.domain;

import java.util.HashSet;
import java.util.Set;

/** What one player has reached on the table; it stays from ball to ball. */
public final class Progress {
    public long score;
    public int ball = 1;
    /** The modes played, by their place in the ring. */
    public final boolean[] played;
    /** The mode the start hole starts next. */
    public int selected;
    public int locks;
    public int lockShots;
    public boolean lockLit;
    public int multiplier = 1;
    public final Set<String> lanesLit = new HashSet<>();
    public boolean extraBallLit;
    public boolean extraBallGiven;
    public int extraBalls;
    public boolean wizardPlayed;
    public boolean kickbackLit = true;
    public boolean stackLit;

    public Progress(int modes) {
        played = new boolean[modes];
    }

    public int modesPlayed() {
        int n = 0;
        for (boolean p : played) if (p) n++;
        return n;
    }

    public boolean allPlayed() {
        return modesPlayed() == played.length;
    }
}
