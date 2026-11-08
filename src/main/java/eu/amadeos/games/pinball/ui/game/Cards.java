package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.games.pinball.domain.TableSpec;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import static eu.amadeos.games.pinball.ui.game.Art.c;

/** The cards over the table: the title with the choice of table and players, the pause and the game's end. */
final class Cards {
    /** The middle of the table on the screen. */
    static final double CX = 206;

    private final Messages msg;
    private final Backglass glass;

    Cards(Messages msg, Backglass glass) {
        this.msg = msg;
        this.glass = glass;
    }

    // --8<-- [start:title]
    void title(Graphics2D g, TableSpec spec, int index, int tables, int players, boolean ready, double time) {
        hints(g);
        card(g, 96, 300, 196);
        g.setFont(Art.font(Font.BOLD, 26f));
        Backglass.drawGlow(g, msg.get("title.name"), CX, 132, c(0xff3ac8), c(0xfff1f8));
        g.setFont(Art.font(Font.PLAIN, 7.5f));
        g.setColor(c(0xb8b0d8));
        centre(g, msg.get("title.tagline"), 146);
        // the table, with arrows to the others
        g.setFont(Art.font(Font.BOLD, 13f));
        g.setColor(c(0xffd27a));
        centre(g, spec.name(), 172);
        arrow(g, CX - 112, 167, -1, index > 0);
        arrow(g, CX + 112, 167, 1, index < tables - 1);
        g.setFont(Art.font(Font.PLAIN, 7.5f));
        g.setColor(c(0x9a8cc0));
        centre(g, msg.format("title.level", spec.level(), spec.modes().size(), spec.wizard().name()), 184);
        // the players
        g.setFont(Art.font(Font.BOLD, 9f));
        g.setColor(c(0xece6ff));
        centre(g, msg.format("title.players", players), 206);
        g.setFont(Art.font(Font.BOLD, 10f));
        boolean blink = ((int) (time * 2)) % 2 == 0;
        g.setColor(ready ? (blink ? c(0xffffff) : c(0xffb43c)) : c(0x8a80b0));
        centre(g, msg.get(ready ? "title.start" : "title.loading"), 228);
        g.setFont(Art.font(Font.PLAIN, 6.8f));
        g.setColor(c(0x9a8cc0));
        centre(g, msg.get("title.keys"), 248);
        centre(g, msg.get("title.keys2"), 258);
        centre(g, msg.get("title.screen"), 270);
    }
    // --8<-- [end:title]

    void loading(Graphics2D g) {
        hints(g);
        g.setFont(Art.font(Font.BOLD, 10f));
        g.setColor(c(0x8a80b0));
        centre(g, msg.get("title.loading"), 200);
    }

    void pause(Graphics2D g) {
        hints(g);
        card(g, 150, 212, 80);
        g.setFont(Art.font(Font.BOLD, 18f));
        Backglass.drawGlow(g, msg.get("pause.title"), CX, 178, c(0x5aa8ff), c(0xf0f8ff));
        g.setFont(Art.font(Font.PLAIN, 7.5f));
        g.setColor(c(0xd8ccff));
        centre(g, msg.get("pause.continue"), 198);
        centre(g, msg.get("pause.leave"), 210);
    }

    void gameOver(Graphics2D g, TableSpec spec, List<Long> scores, int winner, boolean canLeave) {
        hints(g);
        double h = 90 + scores.size() * 14;
        double top = 200 - h / 2;
        card(g, top, 240, h);
        g.setFont(Art.font(Font.BOLD, 18f));
        Backglass.drawGlow(g, msg.get("over.title"), CX, top + 28, c(0xff5a5a), c(0xfff0f0));
        g.setFont(Art.font(Font.PLAIN, 7.5f));
        g.setColor(c(0x9a8cc0));
        centre(g, spec.name(), top + 42);
        g.setFont(Art.font(Font.BOLD, 10f));
        for (int i = 0; i < scores.size(); i++) {
            double y = top + 62 + i * 14;
            g.setColor(i == winner && scores.size() > 1 ? c(0xffd27a) : c(0xece6ff));
            g.drawString(msg.format("hud.player", i + 1), (float) (CX - 90), (float) y);
            Backglass.rightText(g, glass.number(scores.get(i)), CX + 90, y);
        }
        if (canLeave) {
            g.setFont(Art.font(Font.PLAIN, 7.5f));
            g.setColor(c(0xd8ccff));
            centre(g, msg.get("over.continue"), top + h - 12);
        }
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void card(Graphics2D g, double top, double w, double h) {
        RoundRectangle2D box = new RoundRectangle2D.Double(CX - w / 2, top, w, h, 12, 12);
        g.setColor(c(0x0b0816, 225));
        g.fill(box);
        g.setColor(c(0x8d86a0));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(box);
    }

    private static void arrow(Graphics2D g, double x, double y, int dir, boolean on) {
        Path2D a = new Path2D.Double();
        a.moveTo(x + dir * 6, y);
        a.lineTo(x - dir * 3, y - 6);
        a.lineTo(x - dir * 3, y + 6);
        a.closePath();
        g.setColor(on ? c(0xffd27a) : c(0x3a3050));
        g.fill(a);
    }

    private static void centre(Graphics2D g, String s, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (CX - fm.stringWidth(s) / 2.0), (float) y);
    }
}
