package eu.amadeos.games.pinball.repository;

import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.swing.RenderMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PinballSettingsTest {
    @TempDir
    Path dir;

    @Test
    void withoutAFileTheDefaultsApply() {
        PinballSettings settings = new PinballSettings(dir.resolve("pinball.properties"));
        assertEquals("lantern-fair", settings.table());
        assertEquals(1, settings.players());
        assertEquals(RenderMode.DIRECT, settings.renderMode());
        assertArrayEquals(new int[]{Keyboard.LEFT_SHIFT, KeyEvent.VK_Z}, settings.keys("left-flipper", Keyboard.LEFT_SHIFT, KeyEvent.VK_Z));
    }

    @Test
    void choicesAreKept() {
        PinballSettings settings = new PinballSettings(dir.resolve("pinball.properties"));
        settings.setTable("orbit-yard");
        settings.setPlayers(3);
        settings.setSound(false);
        PinballSettings again = new PinballSettings(dir.resolve("pinball.properties"));
        assertEquals("orbit-yard", again.table());
        assertEquals(3, again.players());
        assertFalse(again.sound());
    }

    @Test
    void keysByNameIncludingOneSideOfShiftAndControl() throws Exception {
        Path file = dir.resolve("pinball.properties");
        Files.writeString(file, "keys.left-flipper=left_control A\nkeys.right-flipper=RIGHT_SHIFT\nkeys.plunger=nonsense\n"
                + "table=no-such-table\nplayers=9\n");
        PinballSettings settings = new PinballSettings(file);
        assertArrayEquals(new int[]{Keyboard.LEFT_CONTROL, KeyEvent.VK_A}, settings.keys("left-flipper"));
        assertArrayEquals(new int[]{Keyboard.RIGHT_SHIFT}, settings.keys("right-flipper"));
        assertArrayEquals(new int[]{KeyEvent.VK_SPACE}, settings.keys("plunger", KeyEvent.VK_SPACE));
        assertEquals("lantern-fair", settings.table());
        assertEquals(4, settings.players());
    }
}
