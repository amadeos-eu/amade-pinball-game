package eu.amadeos.games.pinball.domain;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.pinball.Flipper;
import eu.amadeos.gameengine.pinball.Lamps;
import eu.amadeos.gameengine.pinball.Playfield;
import eu.amadeos.gameengine.pinball.Tilt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A game on one table: one to four players in turn, three balls each. It runs the machine
 * ({@link Table}) with the player's buttons and turns what happens on the table into points: the
 * {@link Rules} of the table (modes, locks and multiball, jackpots, combos, the skill shot). What should be
 * shown and heard is queued for the UI: {@link #messages()} for the dot-matrix display, {@link #sounds()}.
 */
public final class Game implements Playfield.Events {
    public enum Phase { PLUNGE, PLAY, BALL_OVER, GAME_OVER }

    public static final int BALLS = 3;
    /** A ball lost this soon after the launch comes back. */
    static final double BALL_SAVE = 8;
    /** How long the bonus count at the end of a ball takes. */
    static final double BONUS_SECONDS = 2.5;

    private final Table table;
    private final Rules rules;
    private final Lamps lamps = new Lamps();
    private final Tilt tilt = new Tilt(3);
    private final Random random;
    private final List<Progress> players = new ArrayList<>();
    private final Deque<Message> messages = new ArrayDeque<>();
    private final Deque<Sfx> sounds = new ArrayDeque<>();
    private final Map<Ball, double[]> still = new HashMap<>();
    private int player;
    private Phase phase = Phase.GAME_OVER;
    private double phaseTime;
    private double ballSave;
    private boolean tilted;
    private boolean wasLeft, wasRight;
    private boolean saveUsed;
    private long bonus;

    public Game(Table table, Random random) {
        this.table = table;
        this.random = random;
        this.rules = new Rules(this);
        table.field().listen(this);
    }

    // ---- the flow of a game ----------------------------------------------------------------------

    /** A new game for {@code count} players; the first one's ball lies on the plunger. */
    public void start(int count) {
        players.clear();
        for (int i = 0; i < count; i++) players.add(new Progress(table.spec().modes().size()));
        player = 0;
        for (Ball b : new ArrayList<>(table.field().balls())) if (!b.captive) table.field().remove(b);
        newBall();
    }

    private void newBall() {
        tilted = false;
        tilt.reset();
        saveUsed = false;
        rules.newBall(current());
        table.serve();
        phase = Phase.PLUNGE;
        phaseTime = 0;
        show("dmd.player-up", 2.5, player + 1, current().ball);
        sound(Sfx.LAUNCH);
    }

    // --8<-- [start:step]
    /** Moves the game on by {@code seconds} with the buttons as they are now. */
    public void step(double seconds, Input in) {
        phaseTime += seconds;
        boolean dead = tilted || phase == Phase.BALL_OVER || phase == Phase.GAME_OVER;
        table.flip(in.left() && !dead, in.right() && !dead);
        if (in.left() && !wasLeft && !dead) rules.laneChange(-1);
        if (in.right() && !wasRight && !dead) rules.laneChange(1);
        wasLeft = in.left();
        wasRight = in.right();
        if (phase != Phase.GAME_OVER) nudge(in);
        boolean laneBall = table.ballInLane();
        if (laneBall && !tilted) {
            double v = in.launch() ? 1250 : table.pullPlunger(in.plunger(), seconds);
            if (in.launch()) table.launch(v);
            if (v > 0) sound(Sfx.LAUNCH);
        } else {
            table.pullPlunger(false, seconds);
        }
        table.step(seconds);
        lamps.update(seconds);
        searchBalls(seconds);
        switch (phase) {
            case PLUNGE -> {
                if (!laneBall && table.field().inPlay() > 0) {
                    phase = Phase.PLAY;
                    phaseTime = 0;
                    if (!saveUsed) ballSave = BALL_SAVE;
                    rules.launched();
                }
            }
            case PLAY -> {
                ballSave = Math.max(0, ballSave - seconds);
                rules.update(seconds);
                if (table.field().inPlay() == 0) ballLost();
            }
            case BALL_OVER -> {
                if (phaseTime >= BONUS_SECONDS) nextBall();
            }
            case GAME_OVER -> {
            }
        }
        rules.lamps(lamps);
    }
    // --8<-- [end:step]

    private void nudge(Input in) {
        if (in.nudgeX() == 0 && !in.nudgeUp()) return;
        table.field().nudge(in.nudgeX() * 70, in.nudgeUp() ? -90 : 0);
        if (tilted) return;
        switch (tilt.nudge()) {
            case WARNING -> {
                show("dmd.warning", 1.5, tilt.warningsGiven());
                sound(Sfx.TILT_WARNING);
            }
            case TILT -> {
                tilted = true;
                rules.tilt();
                show("dmd.tilt", 4);
                sound(Sfx.TILT);
            }
            default -> {
            }
        }
    }

    /** The last ball in play went down the drain. */
    private void ballLost() {
        for (Ball b : new ArrayList<>(table.field().balls())) if (b.state == Ball.State.LOST) table.field().remove(b);
        if (ballSave > 0 && !tilted) {
            saveUsed = true;
            ballSave = 0;
            table.serve();
            phase = Phase.PLUNGE;
            show("dmd.ball-saved", 2);
            sound(Sfx.BALL_SAVED);
            return;
        }
        sound(Sfx.DRAIN);
        bonus = tilted ? 0 : rules.bonus();
        current().score += bonus;
        rules.ballOver();
        phase = Phase.BALL_OVER;
        phaseTime = 0;
        if (!tilted) {
            show("dmd.bonus", BONUS_SECONDS, bonus, current().multiplier);
            sound(Sfx.BONUS);
        }
    }

    private void nextBall() {
        Progress p = current();
        if (p.extraBalls > 0) {
            p.extraBalls--;
            show("dmd.shoot-again", 2);
            sound(Sfx.SHOOT_AGAIN);
            newBall();
            return;
        }
        p.ball++;
        for (int i = 1; i <= players.size(); i++) {
            int next = (player + i) % players.size();
            if (players.get(next).ball <= BALLS) {
                player = next;
                newBall();
                return;
            }
        }
        phase = Phase.GAME_OVER;
        phaseTime = 0;
        show("dmd.game-over", 5);
        sound(Sfx.GAME_OVER);
    }

    /**
     * A ball that has lain still for a while, not held by a hole, the plunger or a raised flipper, is stuck
     * somewhere: like a real machine's ball search, the game kicks it loose.
     */
    private void searchBalls(double seconds) {
        for (Ball b : table.field().balls()) {
            if (b.captive || b.state != Ball.State.FREE || b.x > 216 && b.y > 300) {
                still.remove(b);
                continue;
            }
            double[] s = still.computeIfAbsent(b, k -> new double[]{b.x, b.y, 0});
            if (Math.hypot(b.x - s[0], b.y - s[1]) > 2) {
                s[0] = b.x;
                s[1] = b.y;
                s[2] = 0;
                continue;
            }
            if (cradled(b)) continue;
            s[2] += seconds;
            if (s[2] > 3) {
                b.vx = (random.nextDouble() - 0.5) * 400;
                b.vy = -250 - random.nextDouble() * 200;
                s[2] = 0;
            }
        }
        still.keySet().retainAll(table.field().balls());
    }

    private boolean cradled(Ball b) {
        for (Flipper f : table.field().flippers()) {
            if (f.held() && f.layer == b.layer && Math.hypot(b.x - f.px, b.y - f.py) < f.length + 10) return true;
        }
        return false;
    }

    // ---- what the table tells -------------------------------------------------------------------------

    @Override
    public void hit(String id, Ball ball, double speed) {
        if (tilted || phase == Phase.GAME_OVER && !demo) return;
        rules.hit(id, ball, speed);
    }

    @Override
    public void entered(String zone, Ball ball) {
        if (tilted) return;
        rules.entered(zone, ball);
    }

    @Override
    public void caught(String hole, Ball ball) {
        if (table.drainFloor(ball)) return;
        if (tilted) {
            table.eject(ball, 0.5);
            return;
        }
        rules.caught(hole, ball);
    }

    @Override
    public void climbed(String track, Ball ball) {
        if (!tilted && table.ramps().containsKey(track)) sound(Sfx.RAMP);
    }

    @Override
    public void rode(String track, Ball ball) {
        if (!tilted) rules.ramp(track, ball);
    }

    @Override
    public void rolledBack(String track, Ball ball) {
        if (!tilted && table.ramps().containsKey(track)) sound(Sfx.RAMP_FAIL);
    }

    @Override
    public void drained(Ball ball) {
        still.remove(ball);
    }

    // ---- for the rules ----------------------------------------------------------------------------------

    private boolean demo;

    /** The attract mode plays without a game running: the table scores nothing but its toys move. */
    public void demo(boolean on) {
        demo = on;
    }

    Table table() {
        return table;
    }

    Random random() {
        return random;
    }

    void show(String key, double seconds, Object... args) {
        if (messages.size() > 6) messages.pollFirst();
        messages.add(new Message(key, seconds, args));
    }

    void sound(Sfx sfx) {
        if (sounds.size() > 24) sounds.pollFirst();
        sounds.add(sfx);
    }

    void score(long points) {
        if (phase == Phase.GAME_OVER) return;
        current().score += points;
    }

    void extendSave(double seconds) {
        ballSave = Math.max(ballSave, seconds);
    }

    double phaseTime() {
        return phaseTime;
    }

    // ---- what the UI reads ------------------------------------------------------------------------------

    public Progress current() {
        return players.isEmpty() ? null : players.get(player);
    }

    public int player() {
        return player;
    }

    public List<Progress> players() {
        return players;
    }

    public Phase phase() {
        return phase;
    }

    public Rules rules() {
        return rules;
    }

    public Lamps lamps() {
        return lamps;
    }

    public boolean tilted() {
        return tilted;
    }

    public boolean ballSaveLit() {
        return ballSave > 0 || phase == Phase.PLUNGE && !saveUsed;
    }

    /** The messages for the display since the last call, oldest first. */
    public List<Message> messages() {
        List<Message> out = new ArrayList<>(messages);
        messages.clear();
        return out;
    }

    /** The sounds since the last call. */
    public List<Sfx> sounds() {
        List<Sfx> out = new ArrayList<>(sounds);
        sounds.clear();
        return out;
    }

    public long lastBonus() {
        return bonus;
    }

    public Table machine() {
        return table;
    }
}
