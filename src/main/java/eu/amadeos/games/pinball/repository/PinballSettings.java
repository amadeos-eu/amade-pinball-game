package eu.amadeos.games.pinball.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.pinball.domain.TableFile;

import javax.swing.KeyStroke;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The settings in {@code ~/.config/amadeos/pinball/pinball.properties}: the graphics mode, the sound, the
 * keys, the last table and the number of players. Keys are written as Java names them, several separated
 * by spaces, e.g. {@code keys.left-flipper=LEFT_SHIFT Z}; {@code LEFT_SHIFT}, {@code RIGHT_SHIFT},
 * {@code LEFT_CONTROL} and {@code RIGHT_CONTROL} name one side's key only.
 */
public final class PinballSettings {
    private static final String GRAPHICS = "graphics";
    private static final String SOUND = "sound";
    private static final String TABLE = "table";
    private static final String PLAYERS = "players";
    // --8<-- [start:sides]
    private static final Map<String, Integer> SIDES = Map.of(
            "LEFT_SHIFT", Keyboard.LEFT_SHIFT, "RIGHT_SHIFT", Keyboard.RIGHT_SHIFT,
            "LEFT_CONTROL", Keyboard.LEFT_CONTROL, "RIGHT_CONTROL", Keyboard.RIGHT_CONTROL);
    // --8<-- [end:sides]

    private final Path file;

    @Inject
    public PinballSettings(AppDirectories directories) {
        this(directories.configFile("pinball.properties"));
    }

    public PinballSettings(Path file) {
        this.file = file;
    }

    public RenderMode renderMode() {
        return RenderMode.ofId(PropertiesFile.load(file).getProperty(GRAPHICS, "")).orElse(RenderMode.DIRECT);
    }

    public void setRenderMode(RenderMode mode) {
        set(GRAPHICS, mode.id());
    }

    public boolean sound() {
        return !"off".equals(PropertiesFile.load(file).getProperty(SOUND, "on"));
    }

    public void setSound(boolean on) {
        set(SOUND, on ? "on" : "off");
    }

    /** The table played last, or the first. */
    public String table() {
        String id = PropertiesFile.load(file).getProperty(TABLE, "");
        return TableFile.TABLES.contains(id) ? id : TableFile.TABLES.getFirst();
    }

    public void setTable(String id) {
        set(TABLE, id);
    }

    /** How many played last time, 1 to 4. */
    public int players() {
        try {
            return Math.max(1, Math.min(4, Integer.parseInt(PropertiesFile.load(file).getProperty(PLAYERS, "1").strip())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public void setPlayers(int players) {
        set(PLAYERS, Integer.toString(players));
    }

    private void set(String key, String value) {
        Properties properties = PropertiesFile.load(file);
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-pinball-game settings");
    }

    /**
     * The keys of an action, e.g. {@code keys("plunger", KeyEvent.VK_SPACE)}: those in the file if it names any
     * it understands, else the defaults.
     */
    public int[] keys(String action, int... defaults) {
        String names = PropertiesFile.load(file).getProperty("keys." + action, "").strip();
        List<Integer> codes = new ArrayList<>();
        for (String name : names.split("\\s+")) {
            if (name.isEmpty()) continue;
            String upper = name.toUpperCase(Locale.ROOT);
            Integer side = SIDES.get(upper);
            if (side != null) {
                codes.add(side);
                continue;
            }
            KeyStroke stroke = KeyStroke.getKeyStroke(upper);
            if (stroke != null && stroke.getKeyCode() != 0) codes.add(stroke.getKeyCode());
        }
        return codes.isEmpty() ? defaults : codes.stream().mapToInt(Integer::intValue).toArray();
    }
}
