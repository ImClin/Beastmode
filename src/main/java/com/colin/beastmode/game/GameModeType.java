package com.colin.beastmode.game;

/**
 * Represents the available gameplay flows for arenas.
 */
public enum GameModeType {
    HUNT,
    TIME_TRIAL;

    public boolean isTimeTrial() {
        return this == TIME_TRIAL;
    }
}
