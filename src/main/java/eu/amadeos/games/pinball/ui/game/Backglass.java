package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.pinball.DotMatrix;
import eu.amadeos.games.pinball.domain.Game;
import eu.amadeos.games.pinball.domain.Progress;
import eu.amadeos.games.pinball.domain.Rules;
import eu.amadeos.games.pinball.domain.TableSpec;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.List;
import java.util.Random;

import static eu.amadeos.games.pinball.ui.game.Art.c;

/**
 * The panel right of the table: the backglass with the table's painted art and title, the score of the
 * players, two dot-matrix windows (messages above, the running modes below) and the list of the table's
 * modes with their lamps. Drawn in the overlay, so its texts stay sharp at any size.
 */
final class Backglass {
    static final double X = 418, Y = 8, W = 214, H = 384;
    static final double ART_H = 176;
    static final Color DOTS = new Color(0xff9a2a);

    private final Messages msg;
    private final NumberFormat numbers;
    final DotMatrix messages = new DotMatrix(128, 32);
    final DotMatrix status = new DotMatrix(128, 32);
    private BufferedImage art;
    private TableSpec artFor;
    private double artScale;

    Backglass(Messages msg, java.util.Locale locale) {
        this.msg = msg;
        this.numbers = NumberFormat.getIntegerInstance(locale);
    }

    String number(long n) {
        return numbers.format(n);
    }

    // --8<-- [start:paint]
    /** The panel; {@code best} is the table's high score in the attract mode, -1 while a game runs. */
    void paint(Graphics2D g, TableSpec spec, Game game, long best, double time) {
        paintArt(g, spec, time);
        double sy = Y + ART_H + 8;
        if (best >= 0) best(g, best, sy);
        else scores(g, game, sy);
        status.paint(g, X, sy + 48, W, 40, DOTS);
        messages.paint(g, X, sy + 94, W, 40, DOTS);
        modes(g, spec, best >= 0 ? null : game, sy + 142, time);
    }
    // --8<-- [end:paint]

    /** The painted backglass, made once per table and size. */
    void paintArt(Graphics2D g, TableSpec spec, double time) {
        AffineTransform t = g.getTransform();
        double scale = t.getScaleX();
        if (art == null || artFor != spec || Math.abs(artScale - scale) > 1e-6) {
            int w = (int) Math.ceil(W * scale), h = (int) Math.ceil(ART_H * scale);
            art = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            Graphics2D a = art.createGraphics();
            a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            a.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            a.scale(scale, scale);
            a.translate(-X, -Y);
            drawArt(a, spec);
            a.dispose();
            artFor = spec;
            artScale = scale;
        }
        g.setTransform(new AffineTransform());
        g.drawImage(art, (int) Math.round(t.getTranslateX() + X * scale), (int) Math.round(t.getTranslateY() + Y * scale), null);
        g.setTransform(t);
        // the title's lights chase round the frame
        RoundRectangle2D box = new RoundRectangle2D.Double(X, Y, W, ART_H, 8, 8);
        int n = 28;
        for (int i = 0; i < n; i++) {
            double u = i / (double) n;
            double px, py;
            double per = 2 * (W + ART_H), d = u * per;
            if (d < W) { px = X + d; py = Y + 2; } else if (d < W + ART_H) { px = X + W - 2; py = Y + d - W; }
            else if (d < 2 * W + ART_H) { px = X + W - (d - W - ART_H); py = Y + ART_H - 2; } else { px = X + 2; py = Y + ART_H - (d - 2 * W - ART_H); }
            boolean on = ((int) (time * 8) + i) % 4 == 0;
            g.setColor(on ? c(0xfff0c0) : c(0x6a5030));
            g.fill(new Ellipse2D.Double(px - 1.2, py - 1.2, 2.4, 2.4));
        }
        g.setColor(c(0x8d86a0));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(box);
    }

    private void scores(Graphics2D g, Game game, double sy) {
        panelBox(g, X, sy, W, 40);
        Progress p = game.current();
        if (p == null) return;
        g.setFont(Art.font(Font.PLAIN, 7f));
        g.setColor(c(0x9a8cc0));
        g.drawString(msg.format("hud.player", game.player() + 1), (float) X + 8, (float) sy + 12);
        String ball = game.phase() == Game.Phase.GAME_OVER ? msg.get("hud.game-over") : msg.format("hud.ball", Math.min(Game.BALLS, p.ball));
        rightText(g, ball, X + W - 8, sy + 12);
        g.setFont(Art.font(Font.BOLD, 19f));
        g.setColor(c(0xffd27a));
        rightText(g, number(p.score), X + W - 8, sy + 34);
        // the other players' scores, small, at the left
        List<Progress> players = game.players();
        g.setFont(Art.font(Font.PLAIN, 6.5f));
        g.setColor(c(0x8a80b0));
        double ly = sy + 21;
        for (int i = 0; i < players.size(); i++) {
            if (i == game.player()) continue;
            g.drawString((i + 1) + ": " + number(players.get(i).score), (float) X + 8, (float) ly);
            ly += 7.5;
        }
    }

    private void best(Graphics2D g, long best, double sy) {
        panelBox(g, X, sy, W, 40);
        g.setFont(Art.font(Font.PLAIN, 7f));
        g.setColor(c(0x9a8cc0));
        g.drawString(msg.get("hud.high-score"), (float) X + 8, (float) sy + 12);
        g.setFont(Art.font(Font.BOLD, 19f));
        g.setColor(c(0xffd27a));
        rightText(g, best > 0 ? number(best) : "-", X + W - 8, sy + 34);
    }

    private void modes(Graphics2D g, TableSpec spec, Game game, double ry, double time) {
        panelBox(g, X, ry, W, H - (ry - Y) + Y - 8);
        Progress p = game == null ? null : game.current();
        Rules rules = game == null ? null : game.rules();
        g.setFont(Art.font(Font.BOLD, 7f));
        g.setColor(c(0xd8ccff));
        int played = p == null ? 0 : p.modesPlayed();
        g.drawString(msg.format("hud.modes", played, spec.modes().size()), (float) X + 8, (float) ry + 11);
        g.setFont(Art.font(Font.BOLD, 6.5f));
        for (int i = 0; i < spec.modes().size(); i++) {
            double cx = X + 8 + (i % 2) * (W / 2), cy = ry + 22 + (i / 2) * 10;
            boolean done = p != null && p.played[i];
            final int k = i;
            boolean running = p != null && rules.running().stream().anyMatch(r -> r.index == k);
            boolean next = p != null && p.selected == i && !done && !running;
            Color lamp = done ? c(0x4ad87a) : running ? (((int) (time * 6)) % 2 == 0 ? c(0xffb43c) : c(0x7a5420))
                    : next ? (((int) (time * 2.5)) % 2 == 0 ? c(0xd8ccff) : c(0x3a3050)) : c(0x3a3050);
            g.setColor(lamp);
            g.fill(new Ellipse2D.Double(cx, cy - 5, 6, 6));
            g.setColor(done || running ? c(0xece6ff) : c(0x8a80b0));
            g.drawString(spec.modes().get(i).name(), (float) cx + 10, (float) cy);
        }
        if (spec.wizard() != null) {
            // the wizard mode at the right of the heading, its star lit once every mode is played
            boolean lit = p != null && (p.allPlayed() || rules.wizard() != null);
            g.setFont(Art.font(Font.BOLD, 7f));
            String name = spec.wizard().name();
            double right = X + W - 8, nameX = right - g.getFontMetrics().stringWidth(name);
            g.setColor(lit ? (((int) (time * 4)) % 2 == 0 ? c(0xff5a8c) : c(0x5a2040)) : c(0x3a3050));
            g.fill(Art.star(nameX - 7, ry + 8.5, 5));
            g.setColor(lit ? c(0xffd0e0) : c(0x6a6090));
            g.drawString(name, (float) nameX, (float) ry + 11);
        }
    }

    // ---- the painted backglass -------------------------------------------------------------------------

    private static void drawArt(Graphics2D g, TableSpec spec) {
        double x = X, y = Y, w = W, bh = ART_H;
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, w, bh, 8, 8);
        Color[] p = spec.palette();
        g.setPaint(new GradientPaint(0, (float) y, Relief(p[0], 1.3), 0, (float) (y + bh), Relief(p[1], 0.8)));
        g.fill(box);
        Shape old = g.getClip();
        g.clip(box);
        Random r = new Random(4);
        switch (spec.art()) {
            case "fair" -> {
                for (int i = 0; i < 70; i++) {
                    g.setColor(c(0xffffff, 40 + r.nextInt(120)));
                    g.fill(new Ellipse2D.Double(x + r.nextDouble() * w, y + r.nextDouble() * 110, 1.1, 1.1));
                }
                g.setColor(c(0xfff2c4));
                g.fill(new Ellipse2D.Double(x + 170, y + 14, 20, 20));
                g.setColor(c(0x2a1648));
                g.fill(new Ellipse2D.Double(x + 176, y + 10, 20, 20));
                ferris(g, x + 56, y + 82, 46);
                TableArt.tent(g, x + 112, y + 132, 46, 40, c(0xd8443a), c(0xf4e6c8));
                TableArt.tent(g, x + 150, y + 140, 34, 28, c(0x3a78c8), c(0xf4e6c8));
            }
            case "reef" -> {
                for (int i = 0; i < 7; i++) {
                    Path2D ray = new Path2D.Double();
                    double rx = x + 10 + i * 32;
                    ray.moveTo(rx, y);
                    ray.lineTo(rx + 14, y);
                    ray.lineTo(rx + 44, y + bh);
                    ray.lineTo(rx + 20, y + bh);
                    ray.closePath();
                    g.setColor(c(0xaef8ff, 20));
                    g.fill(ray);
                }
                Path2D hull = new Path2D.Double();
                hull.moveTo(x + 20, y + 120);
                hull.lineTo(x + 190, y + 98);
                hull.lineTo(x + 176, y + 140);
                hull.lineTo(x + 40, y + 150);
                hull.closePath();
                g.setColor(c(0x3a2a1c));
                g.fill(hull);
                g.setColor(c(0x5a4028));
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new Line2D.Double(x + 100, y + 112, x + 90, y + 40));
                g.draw(new Line2D.Double(x + 92, y + 52, x + 60, y + 70));
                for (int i = 0; i < 5; i++) g.fill(new Ellipse2D.Double(x + 60 + i * 24, y + 118 - i * 3, 5, 5));
                // the kraken's eyes in the dark of the hold, its arms round the hull
                g.setColor(c(0x8a2a62));
                g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 3; i++) {
                    Path2D arm = new Path2D.Double();
                    arm.moveTo(x + 150 + i * 12, y + 140);
                    arm.curveTo(x + 140 + i * 18, y + 100, x + 190 + i * 6, y + 90, x + 170 + i * 14, y + 60 + i * 8);
                    g.draw(arm);
                }
                g.setColor(c(0xffe060));
                g.fill(new Ellipse2D.Double(x + 150, y + 116, 7, 5));
                g.fill(new Ellipse2D.Double(x + 162, y + 116, 7, 5));
                for (int i = 0; i < 9; i++) fish(g, x + 20 + r.nextDouble() * 170, y + 20 + r.nextDouble() * 70, 3 + r.nextDouble() * 3,
                        new Color[]{c(0xffd040), c(0xff7a5a), c(0x7ae8ff)}[i % 3]);
                for (int i = 0; i < 30; i++) {
                    g.setColor(c(0xd8ffff, 90));
                    g.setStroke(new BasicStroke(0.6f));
                    double s = 1 + r.nextDouble() * 3;
                    g.draw(new Ellipse2D.Double(x + r.nextDouble() * w, y + r.nextDouble() * bh, s, s));
                }
            }
            default -> {
                for (int i = 0; i < 120; i++) {
                    g.setColor(c(0xffffff, 40 + r.nextInt(170)));
                    g.fill(new Ellipse2D.Double(x + r.nextDouble() * w, y + r.nextDouble() * bh, 1, 1));
                }
                g.setPaint(new RadialGradientPaint(new Point2D.Double(x + 60, y + 80), 60f, new float[]{0f, 1f},
                        new Color[]{c(0x4a6ad8), c(0x101840)}));
                g.fill(new Ellipse2D.Double(x + 10, y + 30, 100, 100));
                g.setColor(c(0x9ab8ff, 140));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(x - 14, y + 66, 150, 26));
                // the station: a ring and docking arms
                g.setColor(c(0xc8d4f0));
                g.setStroke(new BasicStroke(3f));
                g.draw(new Ellipse2D.Double(x + 130, y + 30, 60, 60));
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new Line2D.Double(x + 160, y + 30, x + 160, y + 90));
                g.draw(new Line2D.Double(x + 130, y + 60, x + 190, y + 60));
                g.setColor(c(0xff3ac8));
                g.fill(new Ellipse2D.Double(x + 155, y + 55, 10, 10));
                // a ship leaving
                Path2D ship = new Path2D.Double();
                ship.moveTo(x + 110, y + 120);
                ship.lineTo(x + 130, y + 112);
                ship.lineTo(x + 118, y + 126);
                ship.closePath();
                g.setColor(c(0xe8f4ff));
                g.fill(ship);
                g.setColor(c(0x3ae0ff, 160));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(x + 112, y + 124, x + 84, y + 140));
            }
        }
        g.setColor(c(0x0b0816, 220));
        g.fill(new Rectangle2D.Double(x, y + 140, w, 40));
        g.setFont(Art.font(Font.BOLD, 23f));
        drawGlow(g, spec.name().toUpperCase(), x + w / 2, y + 166, p[5], c(0xfff1c8));
        g.setClip(old);
    }

    private static Color Relief(Color col, double f) {
        return Art.shade(col, f);
    }

    private static void fish(Graphics2D g, double x, double y, double s, Color col) {
        Path2D f = new Path2D.Double();
        f.moveTo(x, y);
        f.quadTo(x + s * 1.5, y - s, x + s * 3, y);
        f.quadTo(x + s * 1.5, y + s, x, y);
        f.lineTo(x - s, y - s * 0.7);
        f.lineTo(x - s, y + s * 0.7);
        f.closePath();
        g.setColor(col);
        g.fill(f);
    }

    private static void ferris(Graphics2D g, double cx, double cy, double r) {
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(c(0x6a5a8a));
        g.draw(new Line2D.Double(cx, cy, cx - r * 0.55, cy + r * 1.15));
        g.draw(new Line2D.Double(cx, cy, cx + r * 0.55, cy + r * 1.15));
        g.setColor(c(0xb8a8d8));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.draw(new Ellipse2D.Double(cx - r * 0.86, cy - r * 0.86, 1.72 * r, 1.72 * r));
        int n = 12;
        Color[] cols = {c(0xff5a5a), c(0xffc83c), c(0x4ad8b0), c(0x5aa8ff)};
        for (int i = 0; i < n; i++) {
            double an = i * Math.PI * 2 / n + 0.1;
            g.setColor(c(0x8a7ab0));
            g.setStroke(new BasicStroke(0.7f));
            g.draw(new Line2D.Double(cx, cy, cx + Math.cos(an) * r, cy + Math.sin(an) * r));
        }
        for (int i = 0; i < n; i++) {
            double an = i * Math.PI * 2 / n + 0.1;
            double gx = cx + Math.cos(an) * r, gy = cy + Math.sin(an) * r;
            g.setColor(cols[i % 4]);
            g.fill(new RoundRectangle2D.Double(gx - 3.5, gy + 1, 7, 6, 2, 2));
            g.setColor(c(0xfff0b0, 200));
            g.fill(new Ellipse2D.Double(gx - 1.3, gy - 1.3, 2.6, 2.6));
        }
        g.setColor(c(0xffd27a));
        g.fill(new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));
    }

    static void drawGlow(Graphics2D g, String s, double cx, double y, Color glow, Color core) {
        FontMetrics fm = g.getFontMetrics();
        float x0 = (float) (cx - fm.stringWidth(s) / 2.0);
        Shape sh = g.getFont().createGlyphVector(g.getFontRenderContext(), s).getOutline(x0, (float) y);
        for (int i = 6; i >= 1; i--) {
            g.setColor(Art.alpha(glow, 18));
            g.setStroke(new BasicStroke(i * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(sh);
        }
        g.setColor(glow);
        g.drawString(s, x0, (float) y);
        g.setColor(core);
        g.drawString(s, x0, (float) y - 0.6f);
    }

    static void panelBox(Graphics2D g, double x, double y, double w, double h) {
        RoundRectangle2D b = new RoundRectangle2D.Double(x, y, w, h, 6, 6);
        g.setColor(c(0x07060c));
        g.fill(b);
        g.setColor(c(0x8d86a0));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(b);
    }

    static void rightText(Graphics2D g, String s, double right, double y) {
        g.drawString(s, (float) (right - g.getFontMetrics().stringWidth(s)), (float) y);
    }
}
