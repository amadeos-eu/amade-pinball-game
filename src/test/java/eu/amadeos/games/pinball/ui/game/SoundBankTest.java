package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.games.pinball.domain.Sfx;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SoundBankTest {
    @Test
    void everySoundOfTheRulesHasOne() {
        SoundBank bank = new SoundBank();
        for (Sfx sfx : Sfx.values()) assertNotNull(bank.sound(sfx), sfx.name());
        Music music = new Music();
        for (String art : new String[]{"fair", "reef", "orbit"}) assertNotNull(music.table(art));
    }
}
