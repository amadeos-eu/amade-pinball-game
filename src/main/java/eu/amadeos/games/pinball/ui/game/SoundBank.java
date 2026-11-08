package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;
import eu.amadeos.gameengine.audio.Speech;
import eu.amadeos.games.pinball.domain.Sfx;

import java.util.EnumMap;
import java.util.Map;

/**
 * The sounds of the machine, made once at start from tones, and the voice that calls out the big moments:
 * no recordings. Each {@link Sfx} of the rules has one sound.
 */
// --8<-- [start:sounds]
final class SoundBank {
    private static final Speech.Voice CALLER = Speech.Voice.MAN.withPitch(98);

    private final Map<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);

    SoundBank() {
        Sound knock = Sound.mix(Sound.tone(Wave.TRIANGLE, 180, 60, 0.09, 0.45), Sound.tone(Wave.NOISE, 0, 0, 0.04, 0.2));
        sounds.put(Sfx.BUMPER, Sound.mix(knock, Sound.tone(Wave.SQUARE, 520, 380, 0.07, 0.06)));
        sounds.put(Sfx.SLING, Sound.mix(Sound.tone(Wave.TRIANGLE, 240, 90, 0.07, 0.4), Sound.tone(Wave.NOISE, 0, 0, 0.03, 0.15)));
        sounds.put(Sfx.TARGET, Sound.tone(Wave.SQUARE, 880, 660, 0.05, 0.06));
        sounds.put(Sfx.DROP, Sound.mix(Sound.tone(Wave.TRIANGLE, 300, 120, 0.06, 0.3), Sound.tone(Wave.SQUARE, 1100, 900, 0.04, 0.04)));
        sounds.put(Sfx.BANK, chime(523, 659, 784, 1047));
        sounds.put(Sfx.ROLLOVER, Sound.tone(Wave.SQUARE, 1320, 1320, 0.03, 0.04));
        sounds.put(Sfx.SPINNER, Sound.tone(Wave.SQUARE, 1600, 1500, 0.015, 0.035));
        sounds.put(Sfx.RAMP, Sound.tone(Wave.SQUARE, 440, 1320, 0.25, 0.05));
        sounds.put(Sfx.RAMP_FAIL, Sound.tone(Wave.TRIANGLE, 400, 200, 0.2, 0.15));
        sounds.put(Sfx.ORBIT, Sound.tone(Wave.TRIANGLE, 300, 900, 0.3, 0.15));
        sounds.put(Sfx.HOLE, Sound.mix(Sound.tone(Wave.TRIANGLE, 150, 70, 0.15, 0.4), Sound.tone(Wave.NOISE, 0, 0, 0.05, 0.12)));
        sounds.put(Sfx.KICK, Sound.mix(Sound.tone(Wave.TRIANGLE, 120, 50, 0.1, 0.5), Sound.tone(Wave.NOISE, 0, 0, 0.06, 0.2)));
        sounds.put(Sfx.LAUNCH, Sound.mix(Sound.tone(Wave.TRIANGLE, 90, 140, 0.12, 0.3), Sound.tone(Wave.NOISE, 0, 0, 0.08, 0.1)));
        sounds.put(Sfx.LOCK_LIT, chime(659, 880));
        sounds.put(Sfx.LOCK, chime(392, 523, 659).then(say("l aa1 k t !")));
        sounds.put(Sfx.MULTIBALL, chime(523, 659, 784, 1047, 1319).then(say("m ah1 l t iy b ao1 l !")));
        sounds.put(Sfx.JACKPOT, say("jh ae1 k p aa t !"));
        sounds.put(Sfx.SUPER_JACKPOT, say("s uw1 p er , jh ae1 k p aa t !"));
        sounds.put(Sfx.MODE_START, chime(392, 523, 784));
        sounds.put(Sfx.MODE_SHOT, chime(784, 1047));
        sounds.put(Sfx.MODE_DONE, chime(523, 659, 784, 1047, 784, 1047));
        sounds.put(Sfx.MODE_OVER, Sound.tone(Wave.SQUARE, 523, 262, 0.4, 0.05));
        sounds.put(Sfx.WIZARD, chime(262, 330, 392, 523, 659, 784, 1047).then(say("w ih1 z er d !")));
        sounds.put(Sfx.COMBO, chime(1047, 1319));
        sounds.put(Sfx.SKILL_SHOT, chime(784, 988, 1175).then(say("s k ih1 l , sh aa1 t !")));
        sounds.put(Sfx.EXTRA_BALL_LIT, chime(659, 784, 988));
        sounds.put(Sfx.EXTRA_BALL, say("eh1 k s t r ax , b ao1 l !"));
        sounds.put(Sfx.SHOOT_AGAIN, say("sh uw1 t , ax g eh1 n !"));
        sounds.put(Sfx.BALL_SAVED, say("b ao1 l , s ey1 v d"));
        sounds.put(Sfx.DRAIN, Sound.tone(Wave.TRIANGLE, 330, 110, 0.6, 0.12));
        sounds.put(Sfx.TILT_WARNING, Sound.tone(Wave.SQUARE, 220, 220, 0.25, 0.08));
        sounds.put(Sfx.TILT, Sound.tone(Wave.SQUARE, 110, 110, 0.6, 0.1).then(say("t ih1 l t")));
        sounds.put(Sfx.BONUS, Sound.tone(Wave.SQUARE, 660, 660, 0.03, 0.04).then(Sound.tone(Wave.SQUARE, 880, 880, 0.03, 0.04)));
        sounds.put(Sfx.MAGNET, Sound.tone(Wave.TRIANGLE, 60, 60, 0.4, 0.2));
        sounds.put(Sfx.SPIN_DISC, Sound.tone(Wave.TRIANGLE, 80, 200, 0.3, 0.2));
        sounds.put(Sfx.KICKBACK, Sound.mix(Sound.tone(Wave.TRIANGLE, 100, 40, 0.15, 0.5), Sound.tone(Wave.NOISE, 0, 0, 0.08, 0.25)));
        sounds.put(Sfx.LANES, chime(880, 1175, 1568));
        sounds.put(Sfx.GAME_OVER, say("g ey1 m , ow1 v er"));
    }

    Sound sound(Sfx sfx) {
        return sounds.get(sfx);
    }

    /** Short bell notes, one after another, as the machine's chimes. */
    private static Sound chime(double... hz) {
        Sound s = null;
        for (double f : hz) {
            Sound note = Sound.mix(Sound.tone(Wave.TRIANGLE, f, f, 0.09, 0.14), Sound.tone(Wave.SQUARE, f * 2, f * 2, 0.04, 0.025));
            s = s == null ? note : s.then(note);
        }
        return s;
    }

    private static Sound say(String phrase) {
        return Speech.say(CALLER, phrase, 0.7);
    }
}
// --8<-- [end:sounds]
