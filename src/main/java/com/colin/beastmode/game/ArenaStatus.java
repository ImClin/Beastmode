package com.colin.beastmode.game;

public final class ArenaStatus {

    private final String arenaName;
    private final boolean available;
    private final boolean complete;
    private final int playerCount;
    private final int capacity;
    private final boolean running;
    private final boolean selecting;
    private final boolean matchActive;

    ArenaStatus(String arenaName, boolean available, boolean complete, int playerCount, int capacity,
                boolean running, boolean selecting, boolean matchActive) {
        this.arenaName = arenaName;
        this.available = available;
        this.complete = complete;
        this.playerCount = playerCount;
        this.capacity = capacity;
        this.running = running;
        this.selecting = selecting;
        this.matchActive = matchActive;
    }

    static ArenaStatus unavailable(String arenaName) {
        return new ArenaStatus(arenaName, false, false, 0, -1, false, false, false);
    }

    public String getArenaName() {
        return arenaName;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isComplete() {
        return complete;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean hasCapacityLimit() {
        return capacity >= 0;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isSelecting() {
        return selecting;
    }

    public boolean isMatchActive() {
        return matchActive;
    }

    public boolean isBusy() {
        return running || selecting || matchActive;
    }
}
