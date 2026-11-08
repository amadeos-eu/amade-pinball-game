package eu.amadeos.games.pinball.domain;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.pinball.Lamps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules of a table, the same for all three; the table file names the parts they use. Points for every
 * part; eight modes started at the start hole, one after another, each a few shots within a time, two
 * running at once when the stack is lit; three locked balls start a multiball with jackpots on the ramps
 * and orbits and a super jackpot at the lock; all modes played start the wizard mode. Shots in quick
 * succession are combos; lit lanes raise the bonus multiplier; the skill shot pays for a good plunge.
 */
public final class Rules {
    /** One mode being played. */
    public static final class Running {
        public final TableSpec.ModeSpec spec;
        public final int index;
        public int hits;
        public double left;

        Running(TableSpec.ModeSpec spec, int index) {
            this.spec = spec;
            this.index = index;
            this.left = spec.seconds();
        }
    }

    static final double COMBO_SECONDS = 3;
    static final double SKILL_SECONDS = 5;

    private final Game game;
    private final TableSpec spec;
    private final List<Running> running = new ArrayList<>();
    private final Set<String> jackpotsLit = new LinkedHashSet<>();
    private final Set<String> standupsHit = new HashSet<>();
    private Progress p;
    private boolean multiball;
    private boolean superLit;
    private int jackpots;
    private double comboTime = -10;
    private int combo;
    private String lastCombo;
    private int skillLane;
    private boolean skillArmed;
    private double sinceLaunch;
    private int ramps, orbits, targets, modesThisBall;
    private double magnetCooldown;
    private int bankCompletions;
    private Running wizard;
    private double time;

    Rules(Game game) {
        this.game = game;
        this.spec = game.table().spec();
    }

    // ---- the ball's life ----------------------------------------------------------------------------

    void newBall(Progress progress) {
        p = progress;
        running.clear();
        jackpotsLit.clear();
        multiball = false;
        superLit = false;
        jackpots = 0;
        combo = 0;
        ramps = orbits = targets = modesThisBall = 0;
        wizard = null;
        skillArmed = true;
        skillLane = lanes().isEmpty() ? 0 : game.random().nextInt(lanes().size());
        game.table().setKickback(p.kickbackLit);
        for (String bank : game.table().banks().keySet()) game.table().resetBank(bank, 0);
    }

    void launched() {
        sinceLaunch = 0;
    }

    void ballOver() {
        running.clear();
        wizard = null;
        multiball = false;
        p.kickbackLit = game.table().kickbackLit();
    }

    void tilt() {
        running.clear();
        wizard = null;
    }

    /** The bonus at the end of a ball: shots made this ball times the multiplier. */
    long bonus() {
        long b = ramps * 10_000L + orbits * 5_000L + targets * 2_000L + modesThisBall * 50_000L + 5_000;
        long total = b * p.multiplier;
        p.multiplier = 1;
        return total;
    }

    void update(double seconds) {
        time += seconds;
        sinceLaunch += seconds;
        magnetCooldown = Math.max(0, magnetCooldown - seconds);
        if (sinceLaunch > SKILL_SECONDS) skillArmed = false;
        for (Running r : new ArrayList<>(running)) {
            r.left -= seconds;
            if (r.left <= 0) {
                running.remove(r);
                p.played[r.index] = true;
                game.show("dmd.mode-over", 2, r.spec.name());
                game.sound(Sfx.MODE_OVER);
                lightExtraBall();
            }
        }
        if (wizard != null) {
            wizard.left -= seconds;
            if (wizard.left <= 0) endWizard();
        }
        int inPlay = game.table().field().inPlay();
        if (multiball && inPlay <= 1) {
            multiball = false;
            jackpotsLit.clear();
            superLit = false;
            if (wizard != null) endWizard();
            game.show("dmd.multiball-over", 2, jackpots);
        }
    }

    // ---- the parts --------------------------------------------------------------------------------------

    void hit(String id, Ball ball, double speed) {
        if (id.equals("post") || id.equals("plunger") || id.equals("ball")) return;
        touched(id);
        if (game.table().isFlipper(id)) return;
        if (id.startsWith("pop") || id.equals("holdpop")) {
            award(id, 1_000, Sfx.BUMPER);
        } else if (id.startsWith("sling")) {
            award(id, 110, Sfx.SLING);
            rotateMode();
        } else if (id.equals("captive")) {
            game.score(1_000);
            game.sound(Sfx.TARGET);
        } else if (game.table().target(id) != null) {
            target(id, speed);
        }
    }

    private void target(String id, double speed) {
        if (speed < 40) return;
        String bank = Table.bank(id);
        targets++;
        if (game.table().banks().containsKey(bank)) {
            if (game.table().target(id).down) return;
            game.table().drop(id);
            award(id, 7_500, Sfx.DROP);
            if (game.table().bankDown(bank)) {
                bankCompletions++;
                game.score(100_000);
                game.show("dmd.bank", 2, bank.toUpperCase());
                game.sound(Sfx.BANK);
                if (!bank.equals(spec.rule("start", ""))) game.table().resetBank(bank, 1.5);
                if (bankCompletions % 2 == 0) lightStack();
            }
            return;
        }
        award(id, 5_000, Sfx.TARGET);
        String group = Table.bank(id);
        if (!spec.ruleList("standups").contains(group)) return;
        standupsHit.add(id);
        long total = spec.parts().stream().filter(x -> x.id() != null && Table.bank(x.id()).equals(group)
                && (x.kind().equals("standup"))).count();
        long hit = standupsHit.stream().filter(s -> Table.bank(s).equals(group)).count();
        if (hit >= total) {
            standupsHit.removeIf(s -> Table.bank(s).equals(group));
            game.score(50_000);
            if (!spec.rule("kickback", "").isEmpty() && !game.table().kickbackLit()) {
                game.table().setKickback(true);
                game.show("dmd.kickback-lit", 2);
                game.sound(Sfx.KICKBACK);
            } else {
                lightStack();
            }
        }
    }

    void entered(String zone, Ball ball) {
        String lanesId = spec.rule("lanes", "");
        if (!lanesId.isEmpty() && zone.startsWith(lanesId) && zone.length() > lanesId.length()) {
            lane(zone);
            return;
        }
        touched(zone);
        switch (zone) {
            case "inL", "inR" -> {
                game.score(1_000);
                game.sound(Sfx.ROLLOVER);
            }
            case "outL", "outR" -> {
                game.score(2_500);
                game.sound(Sfx.ROLLOVER);
            }
            default -> {
            }
        }
        if (zone.startsWith("spin")) {
            game.table().spinSpinner(zone, ball.speed());
            int turns = (int) Math.max(1, Math.min(20, ball.speed() / 60));
            game.score(100L * turns);
            game.sound(Sfx.SPINNER);
            count(zone);
        } else if (zone.startsWith("orbit")) {
            if (ball.vy > 0) return;
            orbits++;
            award(zone, 15_000, Sfx.ORBIT);
            shot(zone);
        } else if (zone.equals("whirlpool")) {
            if (magnetCooldown > 0) return;
            magnetCooldown = 4;
            game.table().grab(spec.rule("magnet", "whirl"), ball, 1.0);
            award(zone, 10_000, Sfx.MAGNET);
        } else if (zone.equals("satellite")) {
            game.table().spin(ball);
            award(zone, 10_000, Sfx.SPIN_DISC);
        } else if (zone.equals(spec.rule("kickback", "-"))) {
            if (game.table().kickbackLit() && ball.vy > 0) {
                game.table().kick(ball);
                game.sound(Sfx.KICKBACK);
                game.show("dmd.kickback", 1.5);
            }
        }
    }

    private void lane(String zone) {
        int i = Integer.parseInt(zone.substring(spec.rule("lanes", "").length())) - 1;
        if (skillArmed && i == skillLane) skill();
        skillArmed = false;
        game.score(2_000);
        game.sound(Sfx.ROLLOVER);
        p.lanesLit.add(zone);
        if (p.lanesLit.size() == lanes().size()) {
            p.lanesLit.clear();
            p.multiplier = Math.min(6, p.multiplier + 1);
            game.score(25_000);
            game.show("dmd.multiplier", 2, p.multiplier);
            game.sound(Sfx.LANES);
        }
    }

    // --8<-- [start:skill]
    /**
     * The first part the plunged ball touches decides the skill shot: on a table whose skill shot is a part
     * ({@code skill=satellite}, {@code skill=treasure*}) it is that part; on one whose skill shot is its top
     * lanes, the lit lane decides, and anything touched before it ends the chance.
     */
    private void touched(String id) {
        if (!skillArmed) return;
        String skill = spec.rule("skill", "-");
        // a skill shot into the top lanes is judged by the lane itself: the lit one counts
        if (skill.equals(spec.rule("lanes", "-")) && id.startsWith(skill)) return;
        skillArmed = false;
        if (skill.endsWith("*") ? id.startsWith(skill.substring(0, skill.length() - 1)) : id.equals(skill)) skill();
    }
    // --8<-- [end:skill]

    private void skill() {
        skillArmed = false;
        long points = 250_000L * spec.level();
        game.score(points);
        game.show("dmd.skill-shot", 2.5, points);
        game.sound(Sfx.SKILL_SHOT);
    }

    /** The flipper buttons move the lit lanes, so that a player can line them up. */
    void laneChange(int dir) {
        List<String> lanes = lanes();
        if (lanes.isEmpty()) return;
        if (skillArmed && game.phase() == Game.Phase.PLUNGE) {
            skillLane = Math.floorMod(skillLane + dir, lanes.size());
            return;
        }
        Set<String> moved = new HashSet<>();
        for (String l : p.lanesLit) {
            int i = lanes.indexOf(l);
            moved.add(lanes.get(Math.floorMod(i + dir, lanes.size())));
        }
        p.lanesLit.clear();
        p.lanesLit.addAll(moved);
    }

    private List<String> lanes() {
        String id = spec.rule("lanes", "");
        if (id.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (Part part : spec.parts("lanes")) {
            if (part.id().equals(id)) for (int i = 1; i <= (int) part.num(2); i++) out.add(id + i);
        }
        return out;
    }

    void caught(String hole, Ball ball) {
        if (hole.equals(spec.rule("lock", "-"))) {
            lock(ball);
            return;
        }
        if (hole.equals(spec.rule("start", "-"))) {
            if (p.lockLit && !spec.rule("lift", "").isEmpty() && !multiball) {
                game.sound(Sfx.HOLE);
                game.show("dmd.lift", 1.5);
                game.table().later(0.6, () -> {
                    ball.state = Ball.State.FREE;
                    game.table().field().ride(ball, spec.rule("lift", ""), 60);
                });
                return;
            }
            start(ball);
            return;
        }
        award(hole, 5_000, Sfx.HOLE);
        shot(hole);
        game.table().eject(ball, 0.8);
    }

    void ramp(String track, Ball ball) {
        if (!game.table().ramps().containsKey(track)) return;
        ramps++;
        award(track, 25_000 + 5_000L * ramps, Sfx.RAMP);
        shot(track);
        if (spec.ruleList("lock-light").contains(track) && !p.lockLit && !multiball) {
            p.lockShots++;
            if (p.lockShots >= spec.rule("lock-needs", 2)) {
                p.lockShots = 0;
                p.lockLit = true;
                game.show("dmd.lock-lit", 2);
                game.sound(Sfx.LOCK_LIT);
            }
        }
    }

    // ---- shots: modes, jackpots, combos -------------------------------------------------------------------

    /** Points and a sound for a part, and the mode shots it counts for. */
    private void award(String id, long points, Sfx sfx) {
        game.score(points);
        game.sound(sfx);
        count(id);
    }

    /** Counts the part for every running mode that wants it. */
    private void count(String id) {
        for (Running r : new ArrayList<>(running)) {
            if (!r.spec.counts(id)) continue;
            r.hits++;
            game.score(r.spec.value());
            if (r.hits >= r.spec.count()) {
                running.remove(r);
                p.played[r.index] = true;
                modesThisBall++;
                long done = r.spec.value() * r.spec.count();
                game.score(done);
                game.show("dmd.mode-done", 3, r.spec.name(), done);
                game.sound(Sfx.MODE_DONE);
                lightExtraBall();
            } else {
                game.show("dmd.mode-shot", 1.5, r.spec.name(), r.hits, r.spec.count());
                game.sound(Sfx.MODE_SHOT);
            }
        }
        if (wizard != null && wizard.spec.counts(id)) {
            wizard.hits++;
            game.score(wizard.spec.value());
            game.show("dmd.wizard-shot", 1.5, wizard.spec.name(), wizard.hits, wizard.spec.count());
            game.sound(Sfx.JACKPOT);
            if (wizard.hits >= wizard.spec.count()) {
                game.score(wizard.spec.value() * 10);
                game.show("dmd.wizard-done", 4, wizard.spec.name());
                endWizard();
            }
        }
    }

    /** A major shot: a ramp, an orbit or a hole. Jackpots and combos. */
    private void shot(String id) {
        if (multiball && jackpotsLit.remove(id)) {
            jackpots++;
            long value = 500_000L + 100_000L * jackpots;
            game.score(value);
            game.show("dmd.jackpot", 3, value);
            game.sound(Sfx.JACKPOT);
            if (jackpotsLit.isEmpty()) {
                superLit = true;
                jackpotsLit.addAll(spec.ruleList("jackpot"));
                game.show("dmd.super-lit", 2);
            }
        }
        if (spec.ruleList("combos").contains(id)) {
            if (time - comboTime <= COMBO_SECONDS && !id.equals(lastCombo)) {
                combo++;
                long value = 50_000L * combo;
                game.score(value);
                game.show("dmd.combo", 1.5, combo + 1, value);
                game.sound(Sfx.COMBO);
                if (combo == 4) lightExtraBall();
            } else {
                combo = 0;
            }
            comboTime = time;
            lastCombo = id;
        }
    }

    /** The start hole: starts the selected mode, the wizard mode, or pays the extra ball. */
    private void start(Ball ball) {
        game.sound(Sfx.HOLE);
        if (p.extraBallLit && spec.rule("extra", "").equals(spec.rule("start", ""))) collectExtraBall();
        if (wizard == null && !multiball && p.allPlayed() && running.isEmpty() && !p.wizardPlayed) {
            startWizard(ball);
            return;
        }
        boolean canStart = running.isEmpty() || running.size() == 1 && p.stackLit;
        int next = nextMode();
        if (canStart && next >= 0 && wizard == null) {
            if (!running.isEmpty()) p.stackLit = false;
            Running r = new Running(spec.modes().get(next), next);
            running.add(r);
            p.selected = Math.floorMod(next + 1, spec.modes().size());
            game.score(20_000);
            game.show("dmd.mode-start", 3, r.spec.name(), r.spec.count(), (int) r.spec.seconds());
            game.sound(Sfx.MODE_START);
            game.extendSave(4);
        } else {
            award(spec.rule("start", ""), 25_000, Sfx.HOLE);
        }
        game.table().eject(ball, canStart ? 1.6 : 0.8);
    }

    private int nextMode() {
        int n = spec.modes().size();
        for (int i = 0; i < n; i++) {
            int k = (p.selected + i) % n;
            if (!p.played[k] && running.stream().noneMatch(r -> r.index == k)) return k;
        }
        return -1;
    }

    /** The slingshots move the mode that the start hole will start next. */
    private void rotateMode() {
        if (!running.isEmpty() || wizard != null) return;
        int n = spec.modes().size();
        for (int i = 1; i <= n; i++) {
            int k = (p.selected + i) % n;
            if (!p.played[k]) {
                p.selected = k;
                return;
            }
        }
    }

    private void lightStack() {
        if (p.stackLit) return;
        p.stackLit = true;
        game.show("dmd.stack-lit", 2);
    }

    private void lightExtraBall() {
        if (p.extraBallLit || p.extraBallGiven) return;
        if (p.modesPlayed() >= 3 || combo >= 4) {
            p.extraBallLit = true;
            game.show("dmd.extra-lit", 2);
            game.sound(Sfx.EXTRA_BALL_LIT);
        }
    }

    private void collectExtraBall() {
        p.extraBallLit = false;
        p.extraBallGiven = true;
        p.extraBalls++;
        game.show("dmd.extra-ball", 3);
        game.sound(Sfx.EXTRA_BALL);
    }

    // ---- locks, multiball and the wizard mode ---------------------------------------------------------------

    private void lock(Ball ball) {
        if (multiball) {
            if (superLit) {
                superLit = false;
                long value = 2_000_000L + 500_000L * jackpots;
                game.score(value);
                game.show("dmd.super-jackpot", 3, value);
                game.sound(Sfx.SUPER_JACKPOT);
            } else {
                award(spec.rule("lock", ""), 10_000, Sfx.HOLE);
            }
            game.table().eject(ball, 0.8);
            return;
        }
        if (!p.lockLit) {
            award(spec.rule("lock", ""), 10_000, Sfx.HOLE);
            shot(spec.rule("lock", ""));
            game.table().eject(ball, 0.8);
            return;
        }
        p.locks++;
        p.lockLit = false;
        game.score(50_000);
        if (p.locks >= 3) {
            p.locks = 0;
            startMultiball(ball, 3);
            game.show("dmd.multiball", 3);
            game.sound(Sfx.MULTIBALL);
            return;
        }
        game.show("dmd.locked", 2.5, p.locks);
        game.sound(Sfx.LOCK);
        // the locked ball stays in the toy; a new one comes to the plunger
        game.table().later(1.0, () -> {
            game.table().field().remove(ball);
            game.table().serve();
        });
    }

    private void startMultiball(Ball held, int balls) {
        multiball = true;
        jackpotsLit.clear();
        jackpotsLit.addAll(spec.ruleList("jackpot"));
        if (held != null) game.table().eject(held, 1.5);
        int add = balls - game.table().field().inPlay();
        for (int i = 0; i < add; i++) game.table().later(0.8 + i * 1.2, () -> game.table().autoLaunch());
        game.extendSave(15);
    }

    private void startWizard(Ball ball) {
        TableSpec.ModeSpec w = spec.wizard();
        if (w == null) return;
        wizard = new Running(w, -1);
        p.wizardPlayed = true;
        game.show("dmd.wizard", 4, w.name());
        game.sound(Sfx.WIZARD);
        startMultiball(ball, w.balls());
    }

    private void endWizard() {
        wizard = null;
        for (int i = 0; i < p.played.length; i++) p.played[i] = false;
        p.wizardPlayed = false;
    }

    // ---- the lamps ------------------------------------------------------------------------------------------

    // --8<-- [start:lamps]
    /** Sets the lamps of the table from the state of the rules: what is lit is worth shooting. */
    void lamps(Lamps lamps) {
        if (p == null) return;
        for (int i = 0; i < spec.modes().size(); i++) {
            lamps.set("mode" + i, p.played[i] ? Lamps.State.ON : isRunning(i) ? Lamps.State.FAST
                    : i == p.selected && !p.played[i] ? Lamps.State.SLOW : Lamps.State.OFF);
        }
        lamps.set("wizard", wizard != null ? Lamps.State.FAST : p.allPlayed() ? Lamps.State.SLOW : Lamps.State.OFF);
        for (String shot : shots()) {
            boolean modeWants = running.stream().anyMatch(r -> r.spec.counts(shot)) || wizard != null && wizard.spec.counts(shot);
            lamps.set(shot + "1", modeWants ? Lamps.State.FAST : Lamps.State.OFF);
            lamps.set(shot + "2", jackpotsLit.contains(shot) ? Lamps.State.FAST : Lamps.State.OFF);
            boolean combo = time - comboTime <= COMBO_SECONDS && spec.ruleList("combos").contains(shot) && !shot.equals(lastCombo);
            boolean lockShot = spec.ruleList("lock-light").contains(shot) && !p.lockLit && !multiball;
            lamps.set(shot + "3", combo ? Lamps.State.FAST : lockShot ? Lamps.State.SLOW : Lamps.State.OFF);
        }
        lamps.set("lock", p.lockLit ? Lamps.State.SLOW : superLit ? Lamps.State.FAST : Lamps.State.OFF);
        lamps.set("jackpot", multiball ? Lamps.State.FAST : Lamps.State.OFF);
        lamps.set("extra", p.extraBallLit ? Lamps.State.SLOW : Lamps.State.OFF);
        lamps.set("again", p.extraBalls > 0 || game.ballSaveLit() ? Lamps.State.SLOW : Lamps.State.OFF);
        lamps.set("kickback", game.table().kickbackLit() ? Lamps.State.ON : Lamps.State.OFF);
        lamps.set("outL", game.table().kickbackLit() ? Lamps.State.ON : Lamps.State.OFF);
        lamps.set("inL", Lamps.State.OFF);
        for (int m = 2; m <= 6; m++) lamps.set("mult" + m, p.multiplier >= m ? Lamps.State.ON : Lamps.State.OFF);
        List<String> lanes = lanes();
        for (int i = 0; i < lanes.size(); i++) {
            String l = lanes.get(i);
            boolean skill = skillArmed && i == skillLane && game.phase() == Game.Phase.PLUNGE;
            lamps.set(l, skill ? Lamps.State.FAST : p.lanesLit.contains(l) ? Lamps.State.ON : Lamps.State.OFF);
        }
    }
    // --8<-- [end:lamps]

    private boolean isRunning(int i) {
        for (Running r : running) if (r.index == i) return true;
        return false;
    }

    /** The shots that have arrows on the table: the parts named by its {@code fan} inserts. */
    private List<String> shots() {
        List<String> out = new ArrayList<>();
        for (Part part : spec.parts("fan")) out.add(part.id());
        return out;
    }

    // ---- what the UI reads --------------------------------------------------------------------------------------

    public List<Running> running() {
        return running;
    }

    public Running wizard() {
        return wizard;
    }

    public boolean multiball() {
        return multiball;
    }

    public int locks() {
        return p == null ? 0 : p.locks;
    }

    public int combo() {
        return combo;
    }

    public int jackpots() {
        return jackpots;
    }
}
