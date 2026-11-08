package eu.amadeos.games.pinball.domain;

/**
 * A message on the dot-matrix display for {@code seconds}: a text key of the messages file (its text has a
 * big line and, after {@code |}, a small one) and the values to fill in.
 */
public record Message(String key, double seconds, Object... args) {
}
