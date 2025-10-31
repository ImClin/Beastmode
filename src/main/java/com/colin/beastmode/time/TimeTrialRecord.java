package com.colin.beastmode.time;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a stored time-trial result for a single player.
 */
public final class TimeTrialRecord implements Comparable<TimeTrialRecord> {

    private final UUID playerId;
    private final String playerName;
    private final long timeMillis;
    private final long recordedAt;

    public TimeTrialRecord(UUID playerId, String playerName, long timeMillis, long recordedAt) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.timeMillis = timeMillis;
        this.recordedAt = recordedAt;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public long getRecordedAt() {
        return recordedAt;
    }

    public TimeTrialRecord withUpdatedName(String name) {
        return new TimeTrialRecord(playerId, name, timeMillis, recordedAt);
    }

    @Override
    public int compareTo(TimeTrialRecord other) {
        int cmp = Long.compare(timeMillis, other.timeMillis);
        if (cmp != 0) {
            return cmp;
        }
        // Break ties by earliest record time to keep leaderboard stable.
        cmp = Long.compare(recordedAt, other.recordedAt);
        if (cmp != 0) {
            return cmp;
        }
        return playerId.compareTo(other.playerId);
    }
}
