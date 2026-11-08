package eu.amadeos.games.pinball.ui.game;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.input.Controls;
import eu.amadeos.gameengine.input.Gamepad;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.loop.GameLoop;
import eu.amadeos.gameengine.scene.Scene;
import eu.amadeos.gameengine.scene.SceneStack;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.Score;
import eu.amadeos.gameengine.swing.GameCanvas;
import eu.amadeos.games.pinball.domain.Game;
import eu.amadeos.games.pinball.domain.Input;
import eu.amadeos.games.pinball.domain.Progress;
import eu.amadeos.games.pinball.domain.Robot;
import eu.amadeos.games.pinball.domain.Sfx;
import eu.amadeos.games.pinball.domain.Table;
import eu.amadeos.games.pinball.domain.TableFile;
import eu.amadeos.games.pinball.domain.TableSpec;
import eu.amadeos.games.pinball.repository.PinballSettings;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The running game: the loop, the controls, the sounds and the scenes (the title, where the robot plays the
 * chosen table, a game being played, the pause and the end of a game). Everything here runs on the game
 * loop thread, except the {@link Arena} methods, which only post commands to it.
 */
public final class PinballGame implements Arena {
    enum Action { LEFT_FLIPPER, RIGHT_FLIPPER, PLUNGER, NUDGE_LEFT, NUDGE_RIGHT, NUDGE_UP, PAUSE, OK, LEAVE }

    static final int UPDATES_PER_SECOND = 120;
    static final int FRAMES_PER_SECOND = 60;
    static final double STEP = 1.0 / UPDATES_PER_SECOND;
    /** How long a finished game waits before a key leads back to the title. */
    static final double OVER_SECONDS = 1.5;
    /** The robot's game in the title starts again this long after it ended. */
    static final double ATTRACT_RESTART = 3;
    /** A gamepad stick pushed this far nudges the table, once per push. */
    static final double STICK_NUDGE = 0.7;
    /** Table pixels per unit: the table picture is baked at this resolution. */
    static final double PIXELS_PER_UNIT = 4;

    private static final ExecutorService BAKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pinball-bake");
        t.setDaemon(true);
        return t;
    });

    /** One table with its own game, its robot and, once baked, its picture. */
    static final class Machine {
        final TableSpec spec;
        final Table table;
        final Game game;
        final Robot robot;
        private volatile TableView view;
        private boolean baking;

        Machine(TableSpec spec, long seed) {
            this.spec = spec;
            this.table = new Table(spec);
            this.game = new Game(table, new Random(seed));
            this.robot = new Robot(new Random(seed + 1));
        }

        TableView view() {
            return view;
        }
    }

    private final Keyboard keyboard;
    private final Gamepads gamepads;
    private final Messages msg;
    private final Mixer mixer;
    private final PinballSettings settings;
    private final HighScores scores;
    private final Executor ui;
    private final SoundBank sounds = new SoundBank();
    private final Music music = new Music();
    private final Backglass glass;
    private final Displays displays;
    private final Cards cards;
    private final SceneStack scenes = new SceneStack();
    private final Controls<Action> controls;
    private final List<Machine> machines = new ArrayList<>();
    private volatile Consumer<Result> onGameOver = result -> { };
    private volatile String tableId;
    private boolean synchronous;
    private GameLoop loop;
    private double time;
    private long lastFrame;
    private int players;
    private final double[] stick = new double[8];

    @Inject
    public PinballGame(Keyboard keyboard, Gamepads gamepads, Messages msg, PinballSettings settings, HighScores scores,
                       Mixer mixer, Locale locale) {
        this(keyboard, gamepads, msg, settings, scores, mixer, locale, SwingUtilities::invokeLater);
    }

    PinballGame(Keyboard keyboard, Gamepads gamepads, Messages msg, PinballSettings settings, HighScores scores, Mixer mixer,
                Locale locale, Executor ui) {
        this.keyboard = keyboard;
        this.gamepads = gamepads;
        this.msg = msg;
        this.mixer = mixer;
        this.settings = settings;
        this.scores = scores;
        this.ui = ui;
        this.glass = new Backglass(msg, locale);
        this.displays = new Displays(msg, locale, glass.status, glass.messages);
        this.cards = new Cards(msg, glass);
        this.players = settings.players();
        // --8<-- [start:controls]
        controls = new Controls<>(Action.class, keyboard)
                .bind(Action.LEFT_FLIPPER, settings.keys("left-flipper", Keyboard.LEFT_SHIFT, KeyEvent.VK_Z))
                .bind(Action.RIGHT_FLIPPER, settings.keys("right-flipper", Keyboard.RIGHT_SHIFT, KeyEvent.VK_MINUS,
                        KeyEvent.VK_SLASH))
                .bind(Action.PLUNGER, settings.keys("plunger", KeyEvent.VK_SPACE, KeyEvent.VK_DOWN))
                .bind(Action.NUDGE_LEFT, settings.keys("nudge-left", KeyEvent.VK_X))
                .bind(Action.NUDGE_RIGHT, settings.keys("nudge-right", KeyEvent.VK_PERIOD))
                .bind(Action.NUDGE_UP, settings.keys("nudge-up", KeyEvent.VK_UP))
                .bind(Action.PAUSE, settings.keys("pause", KeyEvent.VK_ESCAPE, KeyEvent.VK_P))
                .bind(Action.OK, KeyEvent.VK_ENTER)
                .bind(Action.LEAVE, KeyEvent.VK_Q);
        for (Gamepad pad : gamepads.pads()) {
            controls.bind(Action.LEFT_FLIPPER, pad, Gamepad.Button.LB, Gamepad.Button.X)
                    .bind(Action.RIGHT_FLIPPER, pad, Gamepad.Button.RB, Gamepad.Button.B)
                    .bind(Action.PLUNGER, pad, Gamepad.Button.A)
                    .bind(Action.PAUSE, pad, Gamepad.Button.START)
                    .bind(Action.OK, pad, Gamepad.Button.START, Gamepad.Button.A)
                    .bind(Action.LEAVE, pad, Gamepad.Button.BACK);
        }
        // --8<-- [end:controls]
        long seed = System.nanoTime();
        for (String id : TableFile.TABLES) machines.add(new Machine(TableFile.load(id), seed += 7));
        tableId = settings.table();
        scenes.push(new AttractScene(machine(tableId)));
    }

    /** Starts the loop; the canvas draws the table, then the backglass and the cards. */
    public void start(GameCanvas canvas) {
        loop = new GameLoop(UPDATES_PER_SECOND, FRAMES_PER_SECOND, this::update, canvas::render);
        loop.start();
    }

    public void stop() {
        if (loop != null) loop.stop();
    }

    void update(double seconds) {
        keyboard.poll();
        gamepads.poll();
        time += seconds;
        scenes.update(seconds);
    }

    public void render(Graphics2D g, double alpha) {
        long now = System.nanoTime();
        double seconds = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        Machine m = shown();
        TableView view = view(m);
        if (view != null) view.paint(g, m.game.lamps(), m.game.rules().locks(), seconds, alpha);
    }

    public void renderOverlay(Graphics2D g, double alpha) {
        Machine m = shown();
        if (view(m) == null) cards.loading(g);
        scenes.renderOverlay(g, alpha);
    }

    /** The table's picture, baked on a background thread the first time it is wanted. */
    private TableView view(Machine m) {
        if (m.view == null && !m.baking) {
            m.baking = true;
            Runnable bake = () -> {
                TableView v = new TableView(m.table, new TableArt(m.table, PIXELS_PER_UNIT));
                v.synchronous(synchronous);
                m.view = v;
            };
            if (synchronous) bake.run();
            else BAKER.execute(bake);
        }
        return m.view;
    }

    private Machine shown() {
        return scenes.top() instanceof Showing s ? s.machine() : machine(tableId);
    }

    Machine machine(String id) {
        for (Machine m : machines) if (m.spec.id().equals(id)) return m;
        return machines.getFirst();
    }

    // ---- Arena: called on the Swing thread, carried out on the loop thread -----------------------

    @Override
    public void showTitle() {
        run(() -> scenes.replace(new AttractScene(machine(tableId))));
    }

    @Override
    public void newGame() {
        run(() -> scenes.replace(new PlayScene(machine(tableId), players)));
    }

    @Override
    public void pause() {
        run(() -> {
            if (scenes.top() instanceof PlayScene play) scenes.push(new PauseScene(play));
        });
    }

    @Override
    public void setSound(boolean on) {
        mixer.setMuted(!on);
    }

    @Override
    public String table() {
        return tableId;
    }

    @Override
    public String gamepads() {
        return msg.format("about.gamepads", gamepads.backend(), gamepads.connected());
    }

    @Override
    public void onGameOver(Consumer<Result> listener) {
        onGameOver = listener;
    }

    @Override
    public void scoresChanged() {
        run(() -> {
            if (scenes.top() instanceof AttractScene a) a.best = best(a.machine.spec.id());
        });
    }

    private void run(Runnable command) {
        if (loop != null && loop.running()) loop.post(command);
        else command.run();
    }

    private long best(String table) {
        List<Score> list = scores.list(table);
        return list.isEmpty() ? 0 : list.getFirst().value();
    }

    // ---- for tests -------------------------------------------------------------------------------

    /** Bakes and tilts the table pictures on the calling thread, at once: for screenshots and tests. */
    void synchronous(boolean on) {
        synchronous = on;
    }

    Scene top() {
        return scenes.top();
    }

    Displays displays() {
        return displays;
    }

    PlayScene play(String table, int count) {
        tableId = table;
        PlayScene play = new PlayScene(machine(table), count);
        scenes.replace(play);
        return play;
    }

    // ---- the scenes ------------------------------------------------------------------------------

    /** A scene that shows one table. */
    interface Showing extends Scene {
        Machine machine();
    }

    // --8<-- [start:attract]
    /**
     * The title: the robot plays the chosen table without a sound while the card shows how to start. Left
     * and right choose the table, up, down or 1 to 4 the number of players, Enter starts.
     */
    final class AttractScene implements Showing {
        private final Machine machine;
        private long best;
        private double over;

        AttractScene(Machine machine) {
            this.machine = machine;
            this.best = best(machine.spec.id());
            mixer.stopMusic();
            displays.clear();
            machine.game.demo(true);
            machine.game.start(1);
        }

        @Override
        public Machine machine() {
            return machine;
        }

        @Override
        public void update(double seconds) {
            int index = machines.indexOf(machine);
            if (keyboard.wasPressed(KeyEvent.VK_LEFT) || pad(Gamepad.Button.LEFT)) {
                choose(index - 1);
                return;
            }
            if (keyboard.wasPressed(KeyEvent.VK_RIGHT) || pad(Gamepad.Button.RIGHT)) {
                choose(index + 1);
                return;
            }
            if (keyboard.wasPressed(KeyEvent.VK_UP) || pad(Gamepad.Button.UP)) setPlayers(players + 1);
            if (keyboard.wasPressed(KeyEvent.VK_DOWN) || pad(Gamepad.Button.DOWN)) setPlayers(players - 1);
            for (int n = 1; n <= 4; n++) {
                if (keyboard.wasPressed(KeyEvent.VK_0 + n) || keyboard.wasPressed(KeyEvent.VK_NUMPAD0 + n)) setPlayers(n);
            }
            if ((controls.wasPressed(Action.OK) || keyboard.wasPressed(KeyEvent.VK_SPACE)) && machine.view != null) {
                scenes.replace(new PlayScene(machine, players));
                return;
            }
            Game game = machine.game;
            game.step(seconds, machine.robot.input(machine.table, seconds));
            game.sounds();
            game.messages();
            if (game.phase() == Game.Phase.GAME_OVER && (over += seconds) > ATTRACT_RESTART) {
                over = 0;
                game.start(1);
            }
            displays.attract(seconds, machine.spec, best, players);
        }

        private void choose(int index) {
            if (index < 0 || index >= machines.size()) return;
            Machine next = machines.get(index);
            tableId = next.spec.id();
            settings.setTable(tableId);
            scenes.replace(new AttractScene(next));
        }

        private void setPlayers(int n) {
            int clamped = Math.max(1, Math.min(4, n));
            if (clamped == players) return;
            players = clamped;
            settings.setPlayers(players);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            glass.paint(g, machine.spec, machine.game, best, time);
            cards.title(g, machine.spec, machines.indexOf(machine), machines.size(), players, machine.view != null, time);
        }
    }
    // --8<-- [end:attract]

    private boolean pad(Gamepad.Button button) {
        for (Gamepad pad : gamepads.pads()) if (pad.connected() && pad.wasPressed(button)) return true;
        return false;
    }

    // --8<-- [start:play]
    /** A game being played. */
    final class PlayScene implements Showing {
        private final Machine machine;
        private double over;
        private boolean told;

        PlayScene(Machine machine, int count) {
            this.machine = machine;
            displays.clear();
            machine.game.demo(false);
            machine.game.start(count);
            mixer.playMusic(music.table(machine.spec.art()), true);
        }

        @Override
        public Machine machine() {
            return machine;
        }

        Game game() {
            return machine.game;
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.PAUSE)) {
                scenes.push(new PauseScene(this));
                return;
            }
            step(seconds, input());
            if (machine.game.phase() == Game.Phase.GAME_OVER && !told) {
                told = true;
                mixer.playMusic(music.over, false);
            }
            if (told && (over += seconds) >= OVER_SECONDS) {
                Result result = result();
                Consumer<Result> listener = onGameOver;
                ui.execute(() -> listener.accept(result));
                scenes.replace(new OverScene(this, result));
            }
        }

        /** The flipper buttons held, the plunger held, and a nudge on the step its key went down. */
        private Input input() {
            int nudgeX = (controls.wasPressed(Action.NUDGE_RIGHT) ? 1 : 0) - (controls.wasPressed(Action.NUDGE_LEFT) ? 1 : 0);
            boolean nudgeUp = controls.wasPressed(Action.NUDGE_UP);
            // a gamepad nudges when its stick is pushed hard, once per push
            for (Gamepad pad : gamepads.pads()) {
                if (!pad.connected() || pad.index() >= stick.length / 2) continue;
                int i = pad.index() * 2;
                double x = pad.x(), y = pad.y();
                if (Math.abs(x) > STICK_NUDGE && Math.abs(stick[i]) <= STICK_NUDGE) nudgeX = x > 0 ? 1 : -1;
                if (y < -STICK_NUDGE && stick[i + 1] >= -STICK_NUDGE) nudgeUp = true;
                stick[i] = x;
                stick[i + 1] = y;
            }
            return new Input(controls.isDown(Action.LEFT_FLIPPER), controls.isDown(Action.RIGHT_FLIPPER),
                    controls.isDown(Action.PLUNGER), nudgeX, nudgeUp, false);
        }

        void step(double seconds, Input input) {
            Game game = machine.game;
            game.step(seconds, input);
            for (Sfx sfx : game.sounds()) {
                Sound s = sounds.sound(sfx);
                if (s != null) mixer.play(s);
            }
            displays.update(seconds, game, machine.spec);
        }
        // --8<-- [end:play]

        Result result() {
            List<Long> points = new ArrayList<>();
            for (Progress p : machine.game.players()) points.add(p.score);
            return new Result(machine.spec.id(), points);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            glass.paint(g, machine.spec, machine.game, -1, time);
        }
    }

    /** Over the frozen table until Esc, P, Enter or Start; Q ends the game and goes back to the title. */
    final class PauseScene implements Showing {
        private final PlayScene play;

        PauseScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public Machine machine() {
            return play.machine;
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.LEAVE)) scenes.replace(new AttractScene(play.machine));
            else if (controls.wasPressed(Action.PAUSE) || controls.wasPressed(Action.OK)) scenes.pop();
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            play.renderOverlay(g, alpha);
            cards.pause(g);
        }
    }

    /** The end of a game: the scores stay on the table; Enter leads back to the title. */
    final class OverScene implements Showing {
        private final PlayScene play;
        private final Result result;
        private double shown;

        OverScene(PlayScene play, Result result) {
            this.play = play;
            this.result = result;
        }

        @Override
        public Machine machine() {
            return play.machine;
        }

        @Override
        public void update(double seconds) {
            play.step(seconds, Input.NONE);
            shown += seconds;
            if (shown > 1 && (controls.wasPressed(Action.OK) || controls.wasPressed(Action.PAUSE)
                    || keyboard.wasPressed(KeyEvent.VK_SPACE))) {
                scenes.replace(new AttractScene(play.machine));
            }
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            play.renderOverlay(g, alpha);
            int winner = 0;
            for (int i = 1; i < result.scores().size(); i++) if (result.scores().get(i) > result.scores().get(winner)) winner = i;
            cards.gameOver(g, play.machine.spec, result.scores(), winner, shown > 1);
        }

        Result result() {
            return result;
        }
    }
}
