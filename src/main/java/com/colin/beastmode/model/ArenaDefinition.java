package com.colin.beastmode.model;

import org.bukkit.Location;
import java.util.Objects;

public class ArenaDefinition {

    private final String name;
    private final Cuboid runnerWall;
    private final Cuboid beastWall;
    private final Location runnerSpawn;
    private final Location beastSpawn;
    private final Location waitingSpawn;
    private final int runnerWallDelaySeconds;
    private final int beastReleaseDelaySeconds;
    private final Location finishButton;
    private final Cuboid finishRegion;

    private ArenaDefinition(Builder builder) {
        this.name = builder.name;
        this.runnerWall = builder.runnerWall;
        this.beastWall = builder.beastWall;
    this.runnerSpawn = builder.runnerSpawn;
    this.beastSpawn = builder.beastSpawn;
    this.waitingSpawn = builder.waitingSpawn;
    this.runnerWallDelaySeconds = builder.runnerWallDelaySeconds;
    this.beastReleaseDelaySeconds = builder.beastReleaseDelaySeconds;
    this.finishButton = builder.finishButton;
    this.finishRegion = builder.finishRegion;
    }

    public String getName() {
        return name;
    }

    public Cuboid getRunnerWall() {
        return runnerWall;
    }

    public Cuboid getBeastWall() {
        return beastWall;
    }

    public Location getRunnerSpawn() {
        return runnerSpawn;
    }

    public Location getBeastSpawn() {
        return beastSpawn;
    }

    public Location getWaitingSpawn() {
        return waitingSpawn;
    }

    public int getRunnerWallDelaySeconds() {
        return runnerWallDelaySeconds;
    }

    public int getBeastReleaseDelaySeconds() {
        return beastReleaseDelaySeconds;
    }

    public Location getFinishButton() {
        return finishButton != null ? finishButton.clone() : null;
    }

    public boolean isComplete() {
        return runnerWall != null && beastWall != null && (finishButton != null || finishRegion != null)
                && runnerSpawn != null && beastSpawn != null
                && runnerWallDelaySeconds >= 0 && beastReleaseDelaySeconds >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArenaDefinition)) {
            return false;
        }
        ArenaDefinition that = (ArenaDefinition) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private Cuboid runnerWall;
        private Cuboid beastWall;
    private Location runnerSpawn;
    private Location beastSpawn;
    private Location waitingSpawn;
    private int runnerWallDelaySeconds = -1;
    private int beastReleaseDelaySeconds = -1;
    private Location finishButton;
    private Cuboid finishRegion;

        public Builder(String name) {
            this.name = name;
        }

        public Builder runnerWall(Cuboid runnerWall) {
            this.runnerWall = runnerWall;
            return this;
        }

        public Builder beastWall(Cuboid beastWall) {
            this.beastWall = beastWall;
            return this;
        }

        public Builder runnerSpawn(Location runnerSpawn) {
            this.runnerSpawn = runnerSpawn;
            return this;
        }

        public Builder beastSpawn(Location beastSpawn) {
            this.beastSpawn = beastSpawn;
            return this;
        }

        public Builder waitingSpawn(Location waitingSpawn) {
            this.waitingSpawn = waitingSpawn;
            return this;
        }

        public Builder runnerWallDelaySeconds(int seconds) {
            this.runnerWallDelaySeconds = seconds;
            return this;
        }

        public Builder beastReleaseDelaySeconds(int seconds) {
            this.beastReleaseDelaySeconds = seconds;
            return this;
        }

        public Builder finishButton(Location finishButton) {
            this.finishButton = finishButton;
            return this;
        }

        public Builder finishRegion(Cuboid finishRegion) {
            this.finishRegion = finishRegion;
            return this;
        }

        public ArenaDefinition build() {
            return new ArenaDefinition(this);
        }
    }

    public ArenaDefinition withWaitingSpawn(Location location) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(location)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .build();
    }

    public Cuboid getFinishRegion() {
        return finishRegion;
    }
}
