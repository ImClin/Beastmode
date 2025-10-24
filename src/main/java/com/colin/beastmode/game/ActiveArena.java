package com.colin.beastmode.game;

import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.game.GameManager.RolePreference;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ActiveArena {

    private final ArenaDefinition arena;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> runners = new HashSet<>();
    private final Set<BukkitTask> trackedTasks = new HashSet<>();
    private final Set<UUID> spectatingRunners = new HashSet<>();
    private final Map<UUID, RolePreference> preferences = new ConcurrentHashMap<>();
    private List<BlockState> runnerWallSnapshot;
    private List<BlockState> beastWallSnapshot;
    private boolean running;
    private boolean runnerWallOpened;
    private boolean beastWallOpened;
    private boolean selecting;
    private boolean matchActive;
    private boolean finalPhase;
    private boolean rewardSuppressed;
    private long invulnerabilityUntilMillis;
    private UUID beastId;

    ActiveArena(ArenaDefinition arena) {
        this.arena = arena;
    }

    ArenaDefinition getArena() {
        return arena;
    }

    boolean addPlayer(Player player) {
        return players.add(player.getUniqueId());
    }

    boolean contains(UUID uuid) {
        return players.contains(uuid);
    }

    Set<UUID> getPlayerIds() {
        return players;
    }

    void clearPlayers() {
        players.clear();
        preferences.clear();
        clearMatchState();
    }

    void registerTask(BukkitTask task) {
        if (task != null) {
            trackedTasks.add(task);
        }
    }

    void unregisterTask(BukkitTask task) {
        if (task != null) {
            trackedTasks.remove(task);
        }
    }

    void cancelTasks() {
        for (BukkitTask task : new HashSet<>(trackedTasks)) {
            task.cancel();
        }
        trackedTasks.clear();
    }

    boolean isRunning() {
        return running;
    }

    void setRunning(boolean running) {
        this.running = running;
    }

    boolean isSelecting() {
        return selecting;
    }

    void setSelecting(boolean selecting) {
        this.selecting = selecting;
    }

    boolean isMatchActive() {
        return matchActive;
    }

    void setMatchActive(boolean matchActive) {
        this.matchActive = matchActive;
    }

    boolean isFinalPhase() {
        return finalPhase;
    }

    void setFinalPhase(boolean finalPhase) {
        this.finalPhase = finalPhase;
    }

    boolean isRewardSuppressed() {
        return rewardSuppressed;
    }

    void setRewardSuppressed(boolean rewardSuppressed) {
        this.rewardSuppressed = rewardSuppressed;
    }

    void enableDamageProtection() {
        invulnerabilityUntilMillis = Long.MAX_VALUE;
    }

    void releaseDamageProtectionAfter(long delayMillis) {
        invulnerabilityUntilMillis = System.currentTimeMillis() + Math.max(delayMillis, 0L);
    }

    void clearDamageProtection() {
        invulnerabilityUntilMillis = 0L;
    }

    boolean isDamageProtectionActive() {
        if (invulnerabilityUntilMillis == 0L) {
            return false;
        }
        if (invulnerabilityUntilMillis == Long.MAX_VALUE) {
            return true;
        }
        if (System.currentTimeMillis() <= invulnerabilityUntilMillis) {
            return true;
        }
        invulnerabilityUntilMillis = 0L;
        return false;
    }

    UUID getBeastId() {
        return beastId;
    }

    void setBeastId(UUID beastId) {
        this.beastId = beastId;
    }

    void setRunners(Collection<UUID> runnerIds) {
        runners.clear();
        if (runnerIds != null) {
            runners.addAll(runnerIds);
        }
    }

    boolean isRunner(UUID uuid) {
        return runners.contains(uuid);
    }

    boolean removeRunner(UUID uuid) {
        return runners.remove(uuid);
    }

    boolean hasRunners() {
        return !runners.isEmpty();
    }

    int getRunnerCount() {
        return runners.size();
    }

    boolean removePlayer(UUID uuid) {
        boolean removed = players.remove(uuid);
        preferences.remove(uuid);
        runners.remove(uuid);
        if (beastId != null && beastId.equals(uuid)) {
            beastId = null;
        }
        spectatingRunners.remove(uuid);
        return removed;
    }

    void addSpectatingRunner(UUID uuid) {
        if (uuid != null) {
            spectatingRunners.add(uuid);
        }
    }

    void removeSpectatingRunner(UUID uuid) {
        if (uuid != null) {
            spectatingRunners.remove(uuid);
        }
    }

    boolean isSpectatingRunner(UUID uuid) {
        return uuid != null && spectatingRunners.contains(uuid);
    }

    boolean isRunnerWallOpened() {
        return runnerWallOpened;
    }

    void setRunnerWallOpened(boolean runnerWallOpened) {
        this.runnerWallOpened = runnerWallOpened;
    }

    boolean isBeastWallOpened() {
        return beastWallOpened;
    }

    void setBeastWallOpened(boolean beastWallOpened) {
        this.beastWallOpened = beastWallOpened;
    }

    void setPreference(UUID uuid, RolePreference preference) {
        if (preference == null || preference == RolePreference.ANY) {
            preferences.remove(uuid);
        } else {
            preferences.put(uuid, preference);
        }
    }

    RolePreference getPreference(UUID uuid) {
        return preferences.getOrDefault(uuid, RolePreference.ANY);
    }

    void removePreference(UUID uuid) {
        preferences.remove(uuid);
    }

    void clearMatchState() {
        selecting = false;
        matchActive = false;
        finalPhase = false;
        rewardSuppressed = false;
        clearDamageProtection();
        beastId = null;
        runners.clear();
        spectatingRunners.clear();
    }

    List<BlockState> getRunnerWallSnapshot() {
        return runnerWallSnapshot;
    }

    void setRunnerWallSnapshot(List<BlockState> runnerWallSnapshot) {
        this.runnerWallSnapshot = runnerWallSnapshot;
    }

    List<BlockState> getBeastWallSnapshot() {
        return beastWallSnapshot;
    }

    void setBeastWallSnapshot(List<BlockState> beastWallSnapshot) {
        this.beastWallSnapshot = beastWallSnapshot;
    }
}
