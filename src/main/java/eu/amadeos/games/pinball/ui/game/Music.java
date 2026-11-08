package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;
import eu.amadeos.gameengine.audio.Tune;

/**
 * The tunes, in MML: a quiet loop for each table while a ball is in play (a fairground waltz, a slow
 * tune under water, a driving one in orbit), and a short one when a game ends.
 */
final class Music {
    final Sound fair = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.035, "t150 o5 l4 c e g | g2 e | f a >c< | c2. | b >d< g | g2 f | e g c | c2."),
            Tune.voice(Wave.TRIANGLE, 0.12, "t150 o3 l4 c g g | c g g | f a a | f a a | g b b | g b b | c g g | c g g"));

    final Sound reef = Sound.mix(
            Tune.voice(Wave.TRIANGLE, 0.08, "t84 o4 l8 a >c e d c< a4 | g a >c< a g e4 | f a >c d e d c< a | g2 e2"),
            Tune.voice(Wave.TRIANGLE, 0.12, "t84 o2 l2 a e | g c | f d | e e"));

    final Sound orbit = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.03, "t132 o4 l16 e e e e g g e e d d d d c c d d e e e e g g a a b b a a g g e e"),
            Tune.voice(Wave.TRIANGLE, 0.13, "t132 o2 l8 e e >e< e e e >e< e c c >c< c d d >d< d"));

    final Sound over = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.06, "t130 o5 l8 g e c < g >c4 e4 c2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t130 o3 l4 c g c2"));

    Sound table(String art) {
        return switch (art) {
            case "reef" -> reef;
            case "orbit" -> orbit;
            default -> fair;
        };
    }
}
