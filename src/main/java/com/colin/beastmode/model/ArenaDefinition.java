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
    private final int minRunners;
    private final int maxRunners;
    private final int beastSpeedLevel;

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
        this.minRunners = Math.max(builder.minRunners, 1);
        int sanitizedMax = Math.max(builder.maxRunners, 0);
        if (sanitizedMax > 0 && sanitizedMax < this.minRunners) {
            sanitizedMax = this.minRunners;
        }
        this.maxRunners = sanitizedMax;
        this.beastSpeedLevel = Math.max(builder.beastSpeedLevel, 0);
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

    public int getBeastSpeedLevel() {
        return beastSpeedLevel;
    }

    public Location getFinishButton() {
        return finishButton != null ? finishButton.clone() : null;
    }

    public int getMinRunners() {
        return Math.max(minRunners, 1);
    }

    public int getMaxRunners() {
        return Math.max(maxRunners, 0);
    }

    public boolean isComplete() {
        return runnerWall != null && beastWall != null && (finishButton != null || finishRegion != null)
                && runnerSpawn != null && beastSpawn != null
                && runnerWallDelaySeconds >= 0 && beastReleaseDelaySeconds >= 0
        && beastSpeedLevel >= 0
                && minRunners >= 1
                && maxRunners >= 0
                && (maxRunners == 0 || maxRunners >= minRunners);
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
        private int minRunners = 1;
        private int maxRunners = 0;
    private int beastSpeedLevel = 1;

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

        public Builder beastSpeedLevel(int speedLevel) {
            this.beastSpeedLevel = speedLevel;
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

        public Builder minRunners(int minRunners) {
            this.minRunners = minRunners;
            return this;
        }

        public Builder maxRunners(int maxRunners) {
            this.maxRunners = maxRunners;
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
                .waitingSpawn(location != null ? location.clone() : null)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withRunnerSpawn(Location location) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(location != null ? location.clone() : null)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withBeastSpawn(Location location) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(location != null ? location.clone() : null)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withRunnerWall(Cuboid wall) {
        return builder(name)
                .runnerWall(wall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withBeastWall(Cuboid wall) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(wall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
        .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withRunnerWallDelay(int seconds) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(seconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withBeastReleaseDelay(int seconds) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(seconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withFinishButton(Location location) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(location != null ? location.clone() : null)
                .finishRegion(null)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withMinRunners(int value) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(value)
                .maxRunners(maxRunners)
                .build();
    }

    public ArenaDefinition withMaxRunners(int value) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(beastSpeedLevel)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(value)
                .build();
    }

    public ArenaDefinition withBeastSpeedLevel(int level) {
        return builder(name)
                .runnerWall(runnerWall)
                .beastWall(beastWall)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .waitingSpawn(waitingSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
                .beastSpeedLevel(level)
                .finishButton(finishButton)
                .finishRegion(finishRegion)
                .minRunners(minRunners)
                .maxRunners(maxRunners)
                .build();
    }

    public Cuboid getFinishRegion() {
        return finishRegion;
    }
}
