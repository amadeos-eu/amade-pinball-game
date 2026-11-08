package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.Ranking;
import eu.amadeos.games.pinball.bootstrap.PinballModule;
import eu.amadeos.games.pinball.domain.Game;
import eu.amadeos.games.pinball.domain.Robot;
import eu.amadeos.games.pinball.domain.TableFile;
import eu.amadeos.games.pinball.repository.PinballSettings;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The screenshots of chapter 70 and of the README, drawn without a screen: the title, each table while the
 * robot plays it, the pause and the end of a game. {@code ./gradlew screenshots -Pshots=<folder>}.
 */
public final class Screenshots {
    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final PinballGame game;

    private Screenshots() throws Exception {
        Path dir = Files.createTempDirectory("pinball");
        Messages msg = Messages.load(PinballModule.class, PinballModule.MESSAGES, Locale.ENGLISH);
        PinballSettings settings = new PinballSettings(dir.resolve("pinball.properties"));
        Mixer mixer = new Mixer();
        mixer.setMuted(true);
        game = new PinballGame(keyboard, Gamepads.none(), msg, settings, new HighScores(dir, Ranking.HIGHER_WINS), mixer,
                Locale.US, Runnable::run);
        game.synchronous(true);
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "screenshots");
        dir.mkdirs();
        Screenshots shots = new Screenshots();
        shots.seconds(8);
        shots.save(new File(dir, "title.png"));

        int i = 0;
        for (String id : TableFile.TABLES) {
            PinballGame.PlayScene play = shots.game.play(id, 1);
            // the robot plays until a ball rolls up the middle of the table, where it shows well
            shots.robot(play, 30, 240, g -> g.machine().field().balls().stream()
                    .anyMatch(b -> !b.captive && b.state == Ball.State.FREE && b.layer == 0 && b.y > 150 && b.y < 260
                            && b.x > 50 && b.x < 180 && b.vy < 0));
            shots.save(new File(dir, id + ".png"));
            if (i++ == 0) shots.save(new File(dir, "../screenshot.png"));
        }
        PinballGame.PlayScene play = shots.game.play(TableFile.TABLES.get(1), 2);
        shots.robot(play, 10, 30, g -> false);
        shots.tap(KeyEvent.VK_ESCAPE);
        shots.save(new File(dir, "pause.png"));
        shots.tap(KeyEvent.VK_ESCAPE);
        shots.robot(play, 0, 900, g -> g.phase() == Game.Phase.GAME_OVER);
        shots.seconds(PinballGame.OVER_SECONDS + 1.5);
        shots.save(new File(dir, "over.png"));
    }

    /** The robot plays for at least {@code min} seconds, then until {@code until} or {@code max} seconds. */
    private void robot(PinballGame.PlayScene play, double min, double max, Predicate<Game> until) {
        Robot robot = new Robot(new Random(5));
        double t = 0;
        while (t < max && (t < min || !until.test(play.game()))) {
            now[0] += (long) (PinballGame.STEP * 1e9);
            play.step(PinballGame.STEP, robot.input(play.game().machine(), PinballGame.STEP));
            t += PinballGame.STEP;
        }
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(4);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (PinballGame.STEP * 1e9);
            game.update(PinballGame.STEP);
        }
    }

    private void seconds(double s) {
        steps((int) Math.round(s * PinballGame.UPDATES_PER_SECOND));
    }

    private void save(File file) throws Exception {
        BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(2, 2);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
