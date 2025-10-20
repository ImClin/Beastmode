package com.colin.beastmode.setup;

import java.util.Locale;
import java.util.Optional;

public enum SetupSpawnType {
    RUNNER,
    BEAST;

    public static Optional<SetupSpawnType> fromInput(String input) {
        if (input == null) {
            return Optional.empty();
        }
        switch (input.toLowerCase(Locale.ENGLISH)) {
            case "runner":
                return Optional.of(RUNNER);
            case "beast":
                return Optional.of(BEAST);
            default:
                return Optional.empty();
        }
    }
}
