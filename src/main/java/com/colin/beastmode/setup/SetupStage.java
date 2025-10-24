package com.colin.beastmode.setup;

public enum SetupStage {
    RUNNER_WALL_POS1,
    RUNNER_WALL_POS2,
    BEAST_WALL_POS1,
    BEAST_WALL_POS2,
    RUNNER_SPAWN,
    BEAST_SPAWN,
    RUNNER_WALL_DELAY,
    BEAST_RELEASE_DELAY,
    BEAST_SPEED_LEVEL,
    MIN_RUNNERS,
    MAX_RUNNERS,
    FINISH_BUTTON,
    COMPLETE;

    public boolean expectsBlockSelection() {
        return switch (this) {
            case RUNNER_WALL_POS1, RUNNER_WALL_POS2, BEAST_WALL_POS1, BEAST_WALL_POS2,
                    FINISH_BUTTON -> true;
            default -> false;
        };
    }

    public boolean expectsSpawnCommand() {
        return this == RUNNER_SPAWN || this == BEAST_SPAWN;
    }

    public boolean expectsChatNumber() {
        return switch (this) {
            case RUNNER_WALL_DELAY, BEAST_RELEASE_DELAY, BEAST_SPEED_LEVEL, MIN_RUNNERS, MAX_RUNNERS -> true;
            default -> false;
        };
    }

    public String getFriendlyDescription() {
        return switch (this) {
            case RUNNER_WALL_POS1 -> "Select the first corner of the runner's wall.";
            case RUNNER_WALL_POS2 -> "Select the opposite corner of the runner's wall.";
            case BEAST_WALL_POS1 -> "Select the first corner of the beast's wall.";
            case BEAST_WALL_POS2 -> "Select the opposite corner of the beast's wall.";
            case RUNNER_SPAWN -> "Stand at the runner spawn and run /beastmode setspawn <arena> runner.";
            case BEAST_SPAWN -> "Stand at the beast spawn and run /beastmode setspawn <arena> beast.";
            case RUNNER_WALL_DELAY -> "Type in chat how many seconds until the runner wall opens after the game starts.";
            case BEAST_RELEASE_DELAY -> "Type in chat how many seconds after the runner starts the beast wall should open.";
            case BEAST_SPEED_LEVEL -> "Type in chat the beast's speed level (0 for none).";
            case MIN_RUNNERS -> "Type in chat the minimum number of runners required (at least 1).";
            case MAX_RUNNERS -> "Type in chat the maximum number of runners (0 for no limit).";
            case FINISH_BUTTON -> "Click the finish button with the setup wand.";
            default -> "Setup is complete.";
        };
    }
}
