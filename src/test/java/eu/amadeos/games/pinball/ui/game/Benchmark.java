package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.Ranking;
import eu.amadeos.games.pinball.bootstrap.PinballModule;
import eu.amadeos.games.pinball.domain.Robot;
import eu.amadeos.games.pinball.domain.TableFile;
import eu.amadeos.games.pinball.repository.PinballSettings;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * How long a frame of each table takes to draw, at two window sizes: {@code ./gradlew benchmark}. The robot
 * plays, so the flippers move, the lamps blink and balls roll. The frames are drawn into a compatible image,
 * as the canvas draws, but without waiting for the screen. Also prints how long baking a table takes.
 */
public final class Benchmark {
    static final int WARM_UP = 60;
    static final int FRAMES = 300;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("pinball");
        Messages msg = Messages.load(PinballModule.class, PinballModule.MESSAGES, Locale.ENGLISH);
        Mixer mixer = new Mixer();
        mixer.setMuted(true);
        PinballGame game = new PinballGame(new Keyboard(), Gamepads.none(), msg, new PinballSettings(dir.resolve("p.properties")),
                new HighScores(dir, Ranking.HIGHER_WINS), mixer, Locale.US, Runnable::run);
        game.synchronous(true);
        GraphicsConfiguration gc = GraphicsEnvironment.isHeadless() ? null
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        for (String id : TableFile.TABLES) {
            PinballGame.Machine m = game.machine(id);
            long t0 = System.nanoTime();
            new TableArt(m.table, PinballGame.PIXELS_PER_UNIT);
            System.out.printf(Locale.ROOT, "%-13s baked in %5d ms%n", id, (System.nanoTime() - t0) / 1_000_000);
        }
        System.out.println("ms per frame");
        for (int scale : new int[]{2, 3}) {
            for (String id : TableFile.TABLES) {
                PinballGame.PlayScene play = game.play(id, 1);
                Robot robot = new Robot(new Random(1));
                int w = 640 * scale, h = 400 * scale;
                BufferedImage image = gc != null ? gc.createCompatibleImage(w, h) : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                long total = 0;
                for (int i = 0; i < WARM_UP + FRAMES; i++) {
                    for (int k = 0; k < 2; k++) play.step(PinballGame.STEP, robot.input(play.game().machine(), PinballGame.STEP));
                    Graphics2D g = image.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.scale(scale, scale);
                    long start = System.nanoTime();
                    game.render(g, 0.5);
                    game.renderOverlay(g, 0.5);
                    if (i >= WARM_UP) total += System.nanoTime() - start;
                    g.dispose();
                }
                System.out.printf(Locale.ROOT, "%-13s at %4d x %4d   %6.2f%n", id, w, h, total / 1e6 / FRAMES);
            }
        }
        System.exit(0);
    }
}
