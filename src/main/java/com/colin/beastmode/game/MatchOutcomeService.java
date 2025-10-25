package com.colin.beastmode.game;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Oversees runner/beast victory handling and elimination announcements.
 */
final class MatchOutcomeService {

    private final String prefix;
    private final String defaultBeastName;
    private final PlayerSupportService playerSupport;
    private final PlayerTransitionService transitions;

    MatchOutcomeService(String prefix,
                        String defaultBeastName,
                        PlayerSupportService playerSupport,
                        PlayerTransitionService transitions) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.defaultBeastName = Objects.requireNonNull(defaultBeastName, "defaultBeastName");
        this.playerSupport = Objects.requireNonNull(playerSupport, "playerSupport");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    void handleRunnerVictory(ActiveArena activeArena,
                             Player finisher,
                             Supplier<List<Player>> participantsSupplier,
                             Runnable notifyStatus,
                             Runnable cleanup) {
        if (activeArena == null) {
            return;
        }

        List<Player> participants = safeParticipants(participantsSupplier);
        if (participants.isEmpty()) {
            run(cleanup);
            return;
        }

        UUID beastId = activeArena.getBeastId();
        if (finisher != null && finisher.isOnline() && !containsPlayer(participants, finisher)) {
            participants = new ArrayList<>(participants);
            participants.add(finisher);
        }

        if (finisher != null) {
            if (!activeArena.isRunner(finisher.getUniqueId())) {
                return;
            }

            if (activeArena.isFinalPhase()) {
                rewardAdditionalFinisher(activeArena, finisher, participants, beastId);
                return;
            }

            activeArena.setFinalPhase(true);
            announceFirstFinisher(finisher, participants, beastId);
            return;
        }

        boolean finalPhase = activeArena.isFinalPhase();
        activeArena.setMatchActive(false);
        run(notifyStatus);
        announceRunnerVictory(activeArena, participants, finalPhase);
        run(cleanup);
    }

    void handleBeastVictory(ActiveArena activeArena,
                            Player beast,
                            Supplier<List<Player>> participantsSupplier,
                            Runnable notifyStatus,
                            Runnable cleanup) {
        if (activeArena == null) {
            run(cleanup);
            return;
        }
        List<Player> participants = safeParticipants(participantsSupplier);
        handleBeastVictory(activeArena, beast, participants, notifyStatus, cleanup);
    }

    boolean handleRunnerElimination(ActiveArena activeArena,
                                    Player eliminated,
                                    Supplier<List<Player>> participantsSupplier,
                                    Runnable notifyStatus,
                                    Runnable cleanup) {
        if (activeArena == null) {
            run(cleanup);
            return true;
        }

        List<Player> participants = safeParticipants(participantsSupplier);
        if (participants.isEmpty()) {
            run(cleanup);
            return true;
        }

        announceRunnerElimination(activeArena, eliminated, participants);
        if (!activeArena.hasRunners()) {
            Player beast = resolveBeast(activeArena);
            handleBeastVictory(activeArena, beast, participants, notifyStatus, cleanup);
            return true;
        }
        return false;
    }

    private void handleBeastVictory(ActiveArena activeArena,
                                     Player beast,
                                     List<Player> participants,
                                     Runnable notifyStatus,
                                     Runnable cleanup) {
        activeArena.setMatchActive(false);
        run(notifyStatus);

        List<Player> audience = ensureBeastIncluded(participants, beast);
        String beastName = beast != null ? beast.getName() : defaultBeastName;
        String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Beast Victory!";
        String subtitle = ChatColor.RED + beastName + ChatColor.GRAY + " eliminated everyone.";

        for (Player participant : audience) {
            participant.sendTitle(title, subtitle, 10, 60, 10);
            participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            send(participant, ChatColor.DARK_RED + "" + ChatColor.BOLD + "The Beast prevailed! "
                    + ChatColor.RESET + ChatColor.RED + beastName + ChatColor.GRAY + " cleared the arena.");
            participant.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            playerSupport.resetLoadout(participant);
            transitions.sendPlayerToSpawn(activeArena, participant);
        }

        run(cleanup);
    }

    private void announceFirstFinisher(Player finisher,
                                       List<Player> participants,
                                       UUID beastId) {
        String finisherName = finisher.getName();
        String title = ChatColor.GOLD + "" + ChatColor.BOLD + "Parkour Complete!";
        String subtitle = ChatColor.AQUA + finisherName + ChatColor.YELLOW + " finished the parkour and is ready to slay the Beast!";

        for (Player participant : participants) {
            boolean isFinisher = participant.getUniqueId().equals(finisher.getUniqueId());
            boolean isBeast = beastId != null && beastId.equals(participant.getUniqueId());

            participant.sendTitle(title, subtitle, 10, 60, 10);
            participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            if (isFinisher) {
                send(participant, ChatColor.GOLD + "" + ChatColor.BOLD + finisherName + ChatColor.RESET
                        + ChatColor.YELLOW + " finished the parkour and is ready to slay the Beast!");
                playerSupport.applyFireResistance(participant);
                playerSupport.scheduleRunnerReward(participant);
                continue;
            }

            if (isBeast) {
                send(participant, ChatColor.DARK_RED + "" + ChatColor.BOLD + finisherName
                        + ChatColor.RED + " raided the weapon cache! Stop them before they strike back.");
                continue;
            }

            send(participant, ChatColor.YELLOW + "Keep moving! "
                    + ChatColor.AQUA + finisherName + ChatColor.YELLOW + " found the armory and is gearing up.");
        }
    }

    private void rewardAdditionalFinisher(ActiveArena activeArena,
                                          Player finisher,
                                          List<Player> participants,
                                          UUID beastId) {
        if (participants.isEmpty()) {
            return;
        }

        String finisherName = finisher.getName();
        String title = ChatColor.GOLD + "" + ChatColor.BOLD + "Parkour Complete!";
        String subtitle = ChatColor.AQUA + finisherName + ChatColor.YELLOW + " stocked up for the fight!";

        for (Player participant : participants) {
            boolean isFinisher = participant.getUniqueId().equals(finisher.getUniqueId());
            boolean isBeast = beastId != null && beastId.equals(participant.getUniqueId());

            if (isFinisher) {
                participant.sendTitle(title, subtitle, 10, 60, 10);
                participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                send(participant, ChatColor.GOLD + "" + ChatColor.BOLD + "Locked and loaded! "
                        + ChatColor.RESET + ChatColor.GOLD + "Help slay the Beast.");
                playerSupport.applyFireResistance(participant);
                playerSupport.scheduleRunnerReward(participant);
                continue;
            }

            if (isBeast) {
                send(participant, ChatColor.DARK_RED + "" + ChatColor.BOLD + finisherName
                        + ChatColor.RED + " armed up as well. Keep the pressure on!");
                continue;
            }

            send(participant, ChatColor.YELLOW + finisherName + " has stocked the armory. Reinforcements are coming!");
        }
    }

    private void announceRunnerVictory(ActiveArena activeArena,
                                       List<Player> participants,
                                       boolean finalPhase) {
        String title = ChatColor.GREEN + "" + ChatColor.BOLD + "Runner Victory!";
        String subtitle = finalPhase
                ? ChatColor.AQUA + "The Beast has been defeated."
                : ChatColor.AQUA + "The Beast never made it out.";

        for (Player participant : participants) {
            boolean isRunner = activeArena.isRunner(participant.getUniqueId());
            participant.sendTitle(title, subtitle, 10, 60, 10);
            participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            if (isRunner && !finalPhase && !activeArena.isRewardSuppressed()) {
                playerSupport.scheduleRunnerReward(participant);
            }

            participant.removePotionEffect(PotionEffectType.SPEED);
            participant.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            playerSupport.resetLoadout(participant);
            transitions.sendPlayerToSpawn(activeArena, participant);
        }
    }

    private void announceRunnerElimination(ActiveArena activeArena,
                                           Player eliminated,
                                           List<Player> participants) {
        String eliminatedName = eliminated != null ? eliminated.getName() : "A runner";
        int remaining = activeArena.getRunnerCount();
        String remainingText;
        if (remaining <= 0) {
            remainingText = "No runners remain.";
        } else if (remaining == 1) {
            remainingText = "1 runner remains.";
        } else {
            remainingText = remaining + " runners remain.";
        }

        for (Player participant : participants) {
            send(participant, ChatColor.RED + eliminatedName + ChatColor.GRAY + " has been eliminated. "
                    + ChatColor.GOLD + remainingText);
        }
    }

    private List<Player> ensureBeastIncluded(List<Player> participants, Player beast) {
        List<Player> audience = participants != null ? new ArrayList<>(participants) : new ArrayList<>();
        if (beast != null && beast.isOnline() && !containsPlayer(audience, beast)) {
            audience.add(beast);
        }
        return audience;
    }

    private boolean containsPlayer(List<Player> players, Player target) {
        UUID uuid = target.getUniqueId();
        for (Player player : players) {
            if (player.getUniqueId().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private Player resolveBeast(ActiveArena activeArena) {
        UUID beastId = activeArena.getBeastId();
        return beastId != null ? Bukkit.getPlayer(beastId) : null;
    }

    private List<Player> safeParticipants(Supplier<List<Player>> supplier) {
        List<Player> participants = supplier != null ? supplier.get() : null;
        if (participants == null || participants.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(participants);
    }

    private void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    private void send(Player player, String message) {
        if (player != null) {
            player.sendMessage(prefix + message);
        }
    }
}
