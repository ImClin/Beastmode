package com.colin.beastmode.game;

import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encapsulates logic for determining role selection odds and choosing the Beast.
 */
final class RoleSelectionService {

    private final String vipPermission;
    private final String njogPermission;

    RoleSelectionService(String vipPermission, String njogPermission) {
        this.vipPermission = vipPermission;
        this.njogPermission = njogPermission;
    }

    boolean canChoosePreference(Player player) {
        return determineTier(player) != PlayerTier.NORMAL;
    }

    Player selectBeast(ActiveArena activeArena, List<Player> players, Player candidate) {
        if (candidate != null && players.contains(candidate)) {
            GameManager.RolePreference preference = activeArena.getPreference(candidate.getUniqueId());
            if (preference != GameManager.RolePreference.RUNNER) {
                return candidate;
            }
        }

        Map<Player, Double> weights = new LinkedHashMap<>();
        double total = 0.0d;

        for (Player player : players) {
            GameManager.RolePreference preference = activeArena.getPreference(player.getUniqueId());
            double weight = calculateBeastWeight(player, preference);
            if (weight <= 0.0d) {
                continue;
            }
            weights.put(player, weight);
            total += weight;
        }

        if (total <= 0.0d) {
            for (Player player : players) {
                double weight = fallbackBeastWeight(player);
                if (weight <= 0.0d) {
                    continue;
                }
                weights.put(player, weight);
                total += weight;
            }
        }

        if (total <= 0.0d) {
            if (players.size() == 1) {
                return null;
            }
            return players.get(ThreadLocalRandom.current().nextInt(players.size()));
        }

        double pick = ThreadLocalRandom.current().nextDouble(total);
        for (Map.Entry<Player, Double> entry : weights.entrySet()) {
            pick -= entry.getValue();
            if (pick <= 0.0d) {
                return entry.getKey();
            }
        }

        return weights.keySet().stream().reduce((first, second) -> second).orElseGet(() -> players.get(0));
    }

    private double calculateBeastWeight(Player player, GameManager.RolePreference preference) {
        PlayerTier tier = determineTier(player);
        return switch (tier) {
            case NJOG -> switch (preference) {
                case BEAST -> 1.6d;
                case RUNNER -> 0.4d;
                default -> 1.0d;
            };
            case VIP -> switch (preference) {
                case BEAST -> 1.4d;
                case RUNNER -> 0.6d;
                default -> 1.0d;
            };
            case NORMAL -> 1.0d;
        };
    }

    private double fallbackBeastWeight(Player player) {
        return switch (determineTier(player)) {
            case NJOG -> 1.6d;
            case VIP -> 1.4d;
            case NORMAL -> 1.0d;
        };
    }

    private PlayerTier determineTier(Player player) {
        if (player == null) {
            return PlayerTier.NORMAL;
        }
        if (player.hasPermission(njogPermission)) {
            return PlayerTier.NJOG;
        }
        if (player.hasPermission(vipPermission)) {
            return PlayerTier.VIP;
        }
        return PlayerTier.NORMAL;
    }

    private enum PlayerTier {
        NORMAL,
        VIP,
        NJOG
    }
}
