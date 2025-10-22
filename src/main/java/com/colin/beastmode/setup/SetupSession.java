package com.colin.beastmode.setup;

import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.model.Cuboid;
import org.bukkit.Location;

import java.util.UUID;

public class SetupSession {

    private final UUID playerId;
    private final String arenaName;
    private SetupStage stage;
    private SetupMode mode;
    private Location runnerWallPos1;
    private Location runnerWallPos2;
    private Location beastWallPos1;
    private Location beastWallPos2;
    private Location finishButton;
    private Location runnerSpawn;
    private Location beastSpawn;
    private Integer runnerWallDelaySeconds;
    private Integer beastReleaseDelaySeconds;
    private Integer minRunners;
    private Integer maxRunners;

    public SetupSession(UUID playerId, String arenaName) {
        this.playerId = playerId;
        this.arenaName = arenaName;
        this.stage = SetupStage.RUNNER_WALL_POS1;
        this.mode = SetupMode.CREATE;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getArenaName() {
        return arenaName;
    }

    public SetupStage getStage() {
        return stage;
    }

    public void setStage(SetupStage stage) {
        this.stage = stage;
    }

    public SetupMode getMode() {
        return mode;
    }

    public void setMode(SetupMode mode) {
        this.mode = mode;
    }

    public void setRunnerWallPos1(Location location) {
        this.runnerWallPos1 = location;
    }

    public void setRunnerWallPos2(Location location) {
        this.runnerWallPos2 = location;
    }

    public void setBeastWallPos1(Location location) {
        this.beastWallPos1 = location;
    }

    public void setBeastWallPos2(Location location) {
        this.beastWallPos2 = location;
    }

    public void setFinishButton(Location location) {
        this.finishButton = location;
    }

    public void setRunnerSpawn(Location location) {
        this.runnerSpawn = location;
    }

    public void setBeastSpawn(Location location) {
        this.beastSpawn = location;
    }

    public void setRunnerWallDelaySeconds(int seconds) {
        this.runnerWallDelaySeconds = seconds;
    }

    public void setBeastReleaseDelaySeconds(int seconds) {
        this.beastReleaseDelaySeconds = seconds;
    }

    public void setMinRunners(int minRunners) {
        this.minRunners = minRunners;
    }

    public void setMaxRunners(int maxRunners) {
        this.maxRunners = maxRunners;
    }

    public Location getRunnerWallPos1() {
        return runnerWallPos1;
    }

    public Location getRunnerWallPos2() {
        return runnerWallPos2;
    }

    public Location getBeastWallPos1() {
        return beastWallPos1;
    }

    public Location getBeastWallPos2() {
        return beastWallPos2;
    }

    public Location getFinishButton() {
        return finishButton;
    }

    public Location getRunnerSpawn() {
        return runnerSpawn;
    }

    public Location getBeastSpawn() {
        return beastSpawn;
    }

    public Integer getRunnerWallDelaySeconds() {
        return runnerWallDelaySeconds;
    }

    public Integer getBeastReleaseDelaySeconds() {
        return beastReleaseDelaySeconds;
    }

    public Integer getMinRunners() {
        return minRunners;
    }

    public Integer getMaxRunners() {
        return maxRunners;
    }

    public boolean hasRunnerWallSelection() {
        return runnerWallPos1 != null && runnerWallPos2 != null;
    }

    public boolean hasBeastWallSelection() {
        return beastWallPos1 != null && beastWallPos2 != null;
    }

    public boolean hasFinishButtonSelection() {
        return finishButton != null;
    }

    public ArenaDefinition toArenaDefinition() {
        if (!isComplete()) {
            throw new IllegalStateException("Setup is not complete");
        }
        return ArenaDefinition.builder(arenaName)
                .runnerWall(Cuboid.fromCorners(runnerWallPos1, runnerWallPos2))
                .beastWall(Cuboid.fromCorners(beastWallPos1, beastWallPos2))
                .finishButton(finishButton)
                .runnerSpawn(runnerSpawn)
                .beastSpawn(beastSpawn)
                .runnerWallDelaySeconds(runnerWallDelaySeconds)
                .beastReleaseDelaySeconds(beastReleaseDelaySeconds)
        .minRunners(minRunners)
        .maxRunners(maxRunners)
                .build();
    }

    public boolean isComplete() {
        return hasRunnerWallSelection()
                && hasBeastWallSelection()
        && hasFinishButtonSelection()
                && runnerSpawn != null
                && beastSpawn != null
                && runnerWallDelaySeconds != null
        && beastReleaseDelaySeconds != null
        && minRunners != null
        && maxRunners != null;
    }
}
