package com.colin.beastmode.placeholder;

import com.colin.beastmode.game.GameManager;
import com.colin.beastmode.game.TimeTrialService;
import com.colin.beastmode.time.TimeTrialRecord;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * Provides PlaceholderAPI values for time-trial leaderboards.
 */
public final class TimeTrialPlaceholder extends PlaceholderExpansion {

    private final GameManager gameManager;

    public TimeTrialPlaceholder(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public String getIdentifier() {
        return "beastmode";
    }

    @Override
    public String getAuthor() {
        return "ImClin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean register() {
        return super.register();
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        TimeTrialService trials = gameManager.getTimeTrials();
        if (trials == null) {
            return "";
        }

        String[] parts = params.split("_");
        if (parts.length == 0) {
            return "";
        }

        String keyword = parts[0].toLowerCase(Locale.ENGLISH);
        return switch (keyword) {
            case "trial" -> handleLeaderboardPlaceholder(parts, trials);
            case "trialbest" -> handlePersonalBestPlaceholder(player, parts, trials);
            default -> "";
        };
    }

    private String handleLeaderboardPlaceholder(String[] parts, TimeTrialService trials) {
        if (parts.length < 4) {
            return "";
        }
        String arena = parts[1];
        int slot;
        try {
            slot = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ex) {
            return "";
        }
        if (slot < 1) {
            return "";
        }

        String field = parts[3].toLowerCase(Locale.ENGLISH);
        List<TimeTrialRecord> top = trials.getTopRecords(arena, slot);
        if (slot > top.size()) {
            return "";
        }
        TimeTrialRecord record = top.get(slot - 1);
        return switch (field) {
            case "name" -> ChatColor.stripColor(record.getPlayerName());
            case "time" -> trials.formatDuration(record.getTimeMillis());
            case "raw" -> Long.toString(record.getTimeMillis());
            case "display" -> ChatColor.GOLD + record.getPlayerName() + ChatColor.GRAY + ": "
                    + ChatColor.AQUA + trials.formatDuration(record.getTimeMillis());
            default -> "";
        };
    }

    private String handlePersonalBestPlaceholder(OfflinePlayer player,
                                                 String[] parts,
                                                 TimeTrialService trials) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        if (parts.length < 3) {
            return "";
        }
        String arena = parts[1];
        String field = parts[2].toLowerCase(Locale.ENGLISH);
        long best = trials.getPersonalBest(arena, player.getUniqueId());
        if (best < 0L) {
            return switch (field) {
                case "raw" -> "-1";
                case "time", "display" -> "";
                default -> "";
            };
        }

        return switch (field) {
            case "time" -> trials.formatDuration(best);
            case "raw" -> Long.toString(best);
            case "display" -> ChatColor.AQUA + trials.formatDuration(best);
            default -> "";
        };
    }
}
