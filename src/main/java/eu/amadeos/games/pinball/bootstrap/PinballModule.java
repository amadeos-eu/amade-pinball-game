package eu.amadeos.games.pinball.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.score.HighScores;
import eu.amadeos.gameengine.score.PlayerName;
import eu.amadeos.gameengine.score.Ranking;
import eu.amadeos.games.pinball.repository.PinballSettings;
import eu.amadeos.games.pinball.ui.game.Arena;
import eu.amadeos.games.pinball.ui.game.PinballGame;
import eu.amadeos.games.pinball.ui.main.PinballPresenter;
import eu.amadeos.games.pinball.ui.main.PinballView;
import eu.amadeos.games.pinball.ui.main.PinballWindow;

import java.util.Locale;

/**
 * amade-pinball-game bindings. Theme, language and the XDG directories come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class PinballModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.games.pinball.messages";

    @Override
    protected void configure() {
        bind(PinballSettings.class).in(Scopes.SINGLETON);
        bind(Keyboard.class).in(Scopes.SINGLETON);
        bind(Mixer.class).in(Scopes.SINGLETON);
        bind(PinballGame.class).in(Scopes.SINGLETON);
        bind(Arena.class).to(PinballGame.class);
        bind(PinballWindow.class).in(Scopes.SINGLETON);
        bind(PinballView.class).to(PinballWindow.class);
        bind(PinballPresenter.class).in(Scopes.SINGLETON);
    }

    /** The best games of each table in {@code ~/.local/share/amadeos/games/pinball}. */
    @Provides
    @Singleton
    HighScores highScores(XdgDirectories xdg, AppInfo app) {
        return HighScores.of(xdg, app.configName(), Ranking.HIGHER_WINS);
    }

    @Provides
    @Singleton
    PlayerName playerName(AppDirectories directories) {
        return new PlayerName(directories.config(), System.getProperty("user.name"));
    }

    /** The gamepads of this computer, by the backend of its operating system. */
    @Provides
    @Singleton
    Gamepads gamepads() {
        return Gamepads.open();
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(PinballModule.class, MESSAGES, locale);
    }
}
