package eu.amadeos.games.pinball.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.games.pinball.ui.game.PinballGame;
import eu.amadeos.games.pinball.ui.main.PinballPresenter;
import eu.amadeos.games.pinball.ui.main.PinballWindow;

public final class AmadePinballApplication {
    public static final AppInfo APP = new AppInfo("amade-pinball-game", "Pinball", "pinball");

    private AmadePinballApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadePinballApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            Mixer mixer = injector.getInstance(Mixer.class);
            mixer.start();
            Gamepads gamepads = injector.getInstance(Gamepads.class);
            PinballWindow window = injector.getInstance(PinballWindow.class);
            PinballPresenter presenter = injector.getInstance(PinballPresenter.class);
            PinballGame game = injector.getInstance(PinballGame.class);
            window.bind(presenter);
            presenter.start();
            window.whenClosed(() -> {
                game.stop();
                gamepads.close();
                mixer.close();
                System.exit(0);
            });
            window.setLocationByPlatform(true);
            window.setVisible(true);
            game.start(window.canvas());
        }, new PinballModule());
    }
}
