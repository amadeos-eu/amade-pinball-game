package eu.amadeos.games.pinball.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import eu.amadeos.gameengine.swing.GameTexts;
import eu.amadeos.games.pinball.bootstrap.PinballModule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(PinballModule.class, PinballModule.MESSAGES));
    }

    @Test
    void textsAreFormattedInTheLanguage() {
        Messages german = Messages.load(PinballModule.class, PinballModule.MESSAGES, Locale.GERMANY);
        assertEquals("JACKPOT|1.500.000", german.format("dmd.jackpot", 1_500_000L));
        assertEquals("3 Spieler", german.format("title.players", 3));
        assertEquals("1 Spieler", german.format("title.players", 1));
        assertEquals("LANTERN FAIR|LEICHT", german.format("dmd.attract-table", "LANTERN FAIR", 1));
        assertEquals("2 KUGELN GEFANGEN", german.format("dmd.status-locks", 2));
        Messages english = Messages.load(PinballModule.class, PinballModule.MESSAGES, Locale.US);
        assertEquals("NO BALLS LOCKED", english.format("dmd.status-locks", 0));
    }

    /** The Game menu mixes the engine's entries with the game's own; no two may share a letter. */
    @Test
    void mnemonicsAreUniqueInTheGameMenu() {
        Map<String, String> clashes = new HashMap<>();
        for (Language language : Language.values()) {
            Messages engine = GameTexts.load(language.locale());
            Messages game = Messages.load(PinballModule.class, PinballModule.MESSAGES, language.locale());
            List<String> texts = List.of(engine.get("menu.game.new"), game.get("menu.game.table"), game.get("menu.game.pause"),
                    game.get("menu.game.sound"), engine.get("menu.game.graphics"), engine.get("menu.game.frame-times"),
                    engine.get("menu.game.full-screen"), engine.get("menu.game.scores"), engine.get("menu.game.exit"));
            Map<Character, String> seen = new HashMap<>();
            for (String text : texts) {
                Mnemonics.Text t = Mnemonics.parse(text);
                if (t.index() < 0) {
                    clashes.put(language.code() + " " + text, "no mnemonic");
                    continue;
                }
                String other = seen.put(Character.toLowerCase(t.text().charAt(t.index())), text);
                if (other != null) clashes.put(language.code() + " " + text, other);
            }
        }
        assertEquals(Map.of(), clashes);
    }
}
