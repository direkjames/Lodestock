package io.github.direkjames.lodestock.core.stats;

import java.util.Locale;
import java.util.Optional;

/** The leaderboards. {@code key} is what players type and what placeholders use. */
public enum Board {
    SELLERS("sellers"),
    SPENDERS("spenders"),
    ACTIVE("active"),
    BIGGEST("biggest"),
    NET("net");

    private final String key;

    Board(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** True for boards that rank money, false for the one that counts trades. */
    public boolean isMoney() {
        return this != ACTIVE;
    }

    public static Optional<Board> parse(String text) {
        if (text == null) return Optional.empty();
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (Board board : values()) {
            if (board.key.equals(wanted)) return Optional.of(board);
        }
        return Optional.empty();
    }
}
