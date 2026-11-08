package eu.amadeos.games.pinball.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.PlayerName;
import eu.amadeos.gameengine.swing.FullScreen;
import eu.amadeos.gameengine.swing.GameCanvas;
import eu.amadeos.gameengine.swing.GameMenus;
import eu.amadeos.gameengine.swing.GameTexts;
import eu.amadeos.gameengine.swing.HighScoreDialog;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.pinball.bootstrap.AmadePinballApplication;
import eu.amadeos.games.pinball.domain.TableFile;
import eu.amadeos.games.pinball.ui.game.PinballGame;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The game window: a menu and the cabinet, a {@link GameCanvas} the game loop draws on. The window can be
 * resized or switched to full screen with F11; the picture scales with it, in the render mode chosen under
 * <i>Game → Graphics</i>.
 */
public final class PinballWindow extends JFrame implements PinballView {
    /** The logical screen: the table at the left, the backglass at the right. */
    public static final int WIDTH = 640, HEIGHT = 400;

    private final Messages msg;
    private final GameMenus menus = new GameMenus(GameTexts.load());
    private final HighScores scores;
    private final PlayerName playerName;
    private final GameCanvas canvas;
    private final FullScreen fullScreen;
    private final JCheckBoxMenuItem sound;
    private JMenu graphics;
    private PinballPresenter presenter;

    @Inject
    public PinballWindow(Messages msg, HighScores scores, PlayerName playerName, PinballGame game, Keyboard keyboard) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.scores = scores;
        this.playerName = playerName;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-pinball-game.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());

        // The canvas is a heavyweight AWT component: lightweight menus would open behind it.
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        canvas = new GameCanvas(WIDTH, HEIGHT, game::render, game::renderOverlay);
        canvas.setPreferredSize(startSize());
        keyboard.attach(canvas);
        getContentPane().add(canvas, BorderLayout.CENTER);
        fullScreen = new FullScreen(this, canvas);
        sound = Mnemonics.apply(new JCheckBoxMenuItem(), msg.get("menu.game.sound"));
        sound.setName("game-sound");
        sound.addActionListener(e -> {
            presenter.setSound(sound.isSelected());
            canvas.requestFocusInWindow();
        });
        setJMenuBar(menuBar());
        fullScreen.whenDeactivated(() -> {
            if (presenter != null) presenter.focusLost();
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                canvas.requestFocusInWindow();
            }
        });
        pack();
    }

    /** Twice the logical size where the screen has room for it, the table is too small to play otherwise. */
    private static Dimension startSize() {
        if (GraphicsEnvironment.isHeadless()) return new Dimension(WIDTH, HEIGHT);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double scale = Math.max(1, Math.min(2, Math.min((screen.width - 40) / (double) WIDTH, (screen.height - 100) / (double) HEIGHT)));
        return new Dimension((int) (WIDTH * scale), (int) (HEIGHT * scale));
    }

    /** Connects the window with the presenter; call once before {@link PinballPresenter#start()}. */
    public void bind(PinballPresenter presenter) {
        this.presenter = presenter;
    }

    public GameCanvas canvas() {
        return canvas;
    }

    public FullScreen fullScreen() {
        return fullScreen;
    }

    /** Runs the action when the window closes, but not when it only switches to or from full screen. */
    public void whenClosed(Runnable action) {
        fullScreen.whenClosed(action);
    }

    private JMenuBar menuBar() {
        JMenu game = menus.game();
        game.add(focusing(menus.newGame(() -> presenter.newGame())));
        JMenuItem table = Mnemonics.apply(new JMenuItem(), msg.get("menu.game.table"));
        table.setName("game-table");
        table.addActionListener(e -> presenter.chooseTable());
        game.add(focusing(table));
        // No shortcut: Esc and P are the game's own pause keys.
        JMenuItem pause = Mnemonics.apply(new JMenuItem(), msg.get("menu.game.pause"));
        pause.setName("game-pause");
        pause.addActionListener(e -> presenter.pause());
        game.add(focusing(pause));
        game.addSeparator();
        game.add(sound);
        graphics = menus.graphics(RenderMode.DIRECT, mode -> {
            presenter.setRenderMode(mode);
            canvas.requestFocusInWindow();
        });
        game.add(graphics);
        game.add(focusing(menus.frameTimes(canvas::setShowFrameTimes)));
        game.add(focusing(menus.fullScreen(fullScreen::toggle)));
        game.addSeparator();
        game.add(menus.highScores(() -> presenter.showScores()));
        game.addSeparator();
        game.add(menus.exit(this::dispose));
        JMenu help = menus.help();
        help.add(menus.about(msg.get("window.title"), () -> presenter.showAbout()));
        JMenuBar bar = new JMenuBar();
        bar.add(game);
        bar.add(help);
        return bar;
    }

    /** After a menu command the keys belong to the game again. */
    private <T extends JMenuItem> T focusing(T item) {
        item.addActionListener(e -> canvas.requestFocusInWindow());
        return item;
    }

    // ---- PinballView --------------------------------------------------------------------------------

    @Override
    public void showOptions(boolean soundOn, RenderMode mode) {
        sound.setSelected(soundOn);
        for (int i = 0; i < graphics.getItemCount(); i++) {
            JMenuItem item = graphics.getItem(i);
            item.setSelected(("game-graphics-" + mode.id()).equals(item.getName()));
        }
    }

    @Override
    public void useRenderMode(RenderMode mode) {
        canvas.setRenderMode(mode);
    }

    @Override
    public Optional<String> askName(String message) {
        Optional<String> name = HighScoreDialog.askName(this, message, playerName);
        canvas.requestFocusInWindow();
        return name;
    }

    @Override
    public void showScores(String table, int highlight) {
        List<HighScoreDialog.Table> tables = new ArrayList<>();
        for (String id : TableFile.TABLES) tables.add(new HighScoreDialog.Table(id, msg.get("table." + id)));
        HighScoreDialog.show(this, scores, tables, points -> String.format(getLocale(), "%,d", points), table, highlight);
        canvas.requestFocusInWindow();
    }

    @Override
    public void showAbout(String gamepads) {
        JOptionPane.showMessageDialog(this,
                "<html><body style='width: 320px'><center><h2>"
                        + msg.get("window.title") + " " + AmadePinballApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<p>" + msg.get("dialog.about.keys") + "</p><br>"
                        + "<p>" + gamepads + "</p><br>"
                        + "<small>amade-pinball-game &bull; &copy; 2026 datazuul.com &bull; EUPL-1.2</small>"
                        + "</center></html>",
                Mnemonics.strip(menus.texts().format("menu.help.about", msg.get("window.title"))),
                JOptionPane.INFORMATION_MESSAGE);
        canvas.requestFocusInWindow();
    }
}
