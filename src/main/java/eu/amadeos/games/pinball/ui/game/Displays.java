package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.pinball.DotMatrix;
import eu.amadeos.games.pinball.domain.Game;
import eu.amadeos.games.pinball.domain.Message;
import eu.amadeos.games.pinball.domain.Progress;
import eu.amadeos.games.pinball.domain.Rules;
import eu.amadeos.games.pinball.domain.TableSpec;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * What the two dot-matrix windows show. The upper one tells what is running (a mode and its shots, the
 * multiball, the wizard mode), the lower one the game's messages one after another, each for its time,
 * and between them the score. Texts are written again only when they change.
 */
final class Displays {
    private final Messages msg;
    private final Locale locale;
    private final DotMatrix status;
    private final DotMatrix messages;
    private final Deque<Message> queue = new ArrayDeque<>();
    private final FontMetrics[] metrics = new FontMetrics[16];
    private Message showing;
    private double left;
    private String statusText = "", messageText = "";
    private double time;

    Displays(Messages msg, Locale locale, DotMatrix status, DotMatrix messages) {
        this.msg = msg;
        this.locale = locale;
        this.status = status;
        this.messages = messages;
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY).createGraphics();
        for (int size = 6; size < metrics.length; size++) {
            metrics[size] = g.getFontMetrics(new Font(Font.MONOSPACED, Font.BOLD, size));
        }
        g.dispose();
    }

    void clear() {
        queue.clear();
        showing = null;
        left = 0;
    }

    // --8<-- [start:update]
    /** A game being played: its new messages join the queue; the status shows what runs. */
    void update(double seconds, Game game, TableSpec spec) {
        time += seconds;
        for (Message m : game.messages()) {
            // a newer message of the same kind replaces the waiting one, so the queue never lags behind,
            // and the one showing makes way soon
            queue.removeIf(q -> q.key().equals(m.key()));
            queue.add(m);
            if (showing != null) left = Math.min(left, 0.5);
        }
        left -= seconds;
        if ((showing == null || left <= 0) && !queue.isEmpty()) {
            showing = queue.poll();
            // when others wait, each gets a shorter turn
            left = queue.isEmpty() ? showing.seconds() : Math.min(showing.seconds(), 1.2);
        } else if (showing != null && left <= 0) {
            showing = null;
        }
        if (showing != null) {
            message(msg.format(showing.key(), showing.args()).toUpperCase(locale));
        } else {
            Progress p = game.current();
            if (p == null) message(msg.get("dmd.press-start"));
            else message(String.format(locale, "%,d", p.score) + "|"
                    + msg.format("dmd.player-ball", game.player() + 1, Math.min(Game.BALLS, p.ball)));
        }
        status(game, spec);
    }
    // --8<-- [end:update]

    /** The attract mode: the table's name, the high score and how to start, in turn. */
    void attract(double seconds, TableSpec spec, long best, int players) {
        time += seconds;
        queue.clear();
        showing = null;
        int page = (int) (time / 3) % 3;
        message(switch (page) {
            case 0 -> msg.format("dmd.attract-table", spec.name().toUpperCase(locale), spec.level());
            case 1 -> best > 0 ? msg.format("dmd.attract-best", best) : msg.get("dmd.attract-no-best");
            default -> msg.format("dmd.attract-start", players);
        });
        setStatus(msg.format("dmd.attract-modes", spec.modes().size()) + "|" + spec.wizard().name().toUpperCase(locale));
    }

    private void status(Game game, TableSpec spec) {
        Rules rules = game.rules();
        Rules.Running wizard = rules.wizard();
        Progress p = game.current();
        String text;
        if (p == null) {
            text = msg.format("dmd.attract-modes", spec.modes().size()) + "|";
        } else if (wizard != null) {
            text = wizard.spec.name().toUpperCase(locale) + "|" + msg.format("dmd.status-wizard", wizard.hits, wizard.spec.count());
        } else if (!rules.running().isEmpty()) {
            // with stacked modes, each shows in turn
            int i = (int) (time / 2) % rules.running().size();
            Rules.Running r = rules.running().get(i);
            int secs = (int) Math.ceil(r.left);
            text = msg.format("dmd.status-mode", r.spec.name().toUpperCase(locale), r.hits, r.spec.count()) + "|"
                    + (rules.combo() > 1 ? msg.format("dmd.status-combo", rules.combo()) + "   " : "")
                    + String.format(locale, "%d:%02d", secs / 60, secs % 60);
        } else if (rules.multiball()) {
            text = msg.get("dmd.status-multiball") + "|" + msg.format("dmd.status-jackpots", rules.jackpots());
        } else {
            text = msg.format("dmd.status-modes", p.modesPlayed(), spec.modes().size()) + "|"
                    + msg.format("dmd.status-locks", rules.locks());
        }
        setStatus(text);
    }

    private void message(String text) {
        if (text.equals(messageText)) return;
        messageText = text;
        write(messages, text);
    }

    private void setStatus(String text) {
        if (text.equals(statusText)) return;
        statusText = text;
        write(status, text);
    }

    /** "BIG|small": each line as big as fits the 128 dots. */
    private void write(DotMatrix dmd, String text) {
        int bar = text.indexOf('|');
        String big = (bar < 0 ? text : text.substring(0, bar)).strip();
        String small = bar < 0 ? "" : text.substring(bar + 1).strip();
        dmd.clear();
        if (small.isEmpty()) {
            dmd.line(big, dmd.rows() / 2 + 6, fit(big, 15, dmd.columns()), true);
        } else {
            dmd.line(big, 13, fit(big, 12, dmd.columns()), true);
            dmd.line(small, 28, fit(small, 10, dmd.columns()), false);
        }
    }

    private int fit(String text, int size, int width) {
        while (size > 6 && metrics[size].stringWidth(text) > width - 2) size--;
        return size;
    }

    String messageText() {
        return messageText;
    }

    String statusText() {
        return statusText;
    }
}
