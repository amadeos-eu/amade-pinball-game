package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.games.pinball.domain.Game;
import eu.amadeos.games.pinball.domain.Robot;
import eu.amadeos.games.pinball.domain.Table;
import eu.amadeos.games.pinball.domain.TableFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every table has its pictures, rendered from where the game's view puts the playfield, and they draw a frame. */
class RenderedViewTest {
    static List<String> tables() {
        return TableFile.TABLES;
    }

    @ParameterizedTest
    @MethodSource("tables")
    void theCameraPutsTheTableOnTheViewsTrapezoid(String id) {
        RenderedView view = RenderedView.load(new Table(TableFile.load(id)));
        assertNotNull(view, id + " has no rendered pictures");
        double[] q = TableView.QUAD;
        double[][] corners = {{0, 0}, {Table.WIDTH, 0}, {Table.WIDTH, Table.LENGTH}, {0, Table.LENGTH}};
        for (int i = 0; i < 4; i++) {
            double[] p = view.screen(corners[i][0], corners[i][1], 0);
            assertEquals(q[2 * i], p[0], 0.5, id + " corner " + i + " x");
            assertEquals(q[2 * i + 1], p[1], 0.5, id + " corner " + i + " y");
        }
    }

    @ParameterizedTest
    @MethodSource("tables")
    void aFrameShowsTheBallsAndTheTable(String id) {
        Table table = new Table(TableFile.load(id));
        RenderedView view = RenderedView.load(table);
        Game game = new Game(table, new Random(1));
        Robot robot = new Robot(new Random(2));
        game.start(1);
        for (int i = 0; i < 600; i++) game.step(1 / 120.0, robot.input(table, 1 / 120.0));
        BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.scale(2, 2);
        view.paint(g, game.lamps(), 0, 1 / 60.0, 1);
        g.dispose();
        // the middle of the playfield is drawn, and not black
        assertNotEquals(0, image.getRGB(412, 400) & 0xffffff);
        for (Ball b : table.field().balls()) {
            if (b.state == Ball.State.LOST || b.state == Ball.State.ON_TRACK) continue;
            double[] p = view.screen(b.x, b.y, table.field().layerHeight(b.layer) + b.radius);
            assertNotEquals(0, image.getRGB((int) (p[0] * 2), (int) (p[1] * 2)) & 0xffffff, id + ": a ball is not drawn");
        }
    }
}
