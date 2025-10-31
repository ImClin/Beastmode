package com.colin.beastmode.command;

import com.colin.beastmode.game.GameManager;
import com.colin.beastmode.game.GameModeType;
import com.colin.beastmode.gui.ArenaMenu;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.setup.SetupSessionManager;
import com.colin.beastmode.setup.SetupSpawnType;
import com.colin.beastmode.storage.ArenaStorage;
import com.colin.beastmode.time.TimeTrialRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BeastmodeCommand implements CommandExecutor, TabCompleter {

    private static final String SUB_CREATE = "create";
    private static final String SUB_SETSPAWN = "setspawn";
    private static final String SUB_SETWAITING = "setwaiting";
    private static final String SUB_CANCEL = "cancel";
    private static final String SUB_DELETE = "delete";
    private static final String SUB_JOIN = "join";
    private static final String SUB_TRIAL = "trial";
    private static final String SUB_TRIAL_CLEAR = "trialclear";
    private static final String SUB_EDIT = "edit";
    private static final String ROLE_RUNNER = "runner";
    private static final String ROLE_BEAST = "beast";
    private static final String ROLE_ANY = "any";

    private final ArenaStorage arenaStorage;
    private final SetupSessionManager sessionManager;
    private final ArenaMenu arenaMenu;
    private final GameManager gameManager;

    public BeastmodeCommand(ArenaStorage arenaStorage,
                            SetupSessionManager sessionManager, ArenaMenu arenaMenu,
                            GameManager gameManager) {
        this.arenaStorage = arenaStorage;
        this.sessionManager = sessionManager;
        this.arenaMenu = arenaMenu;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        if (args.length == 0) {
            sessionManager.sendPrefixed(player, ChatColor.YELLOW + "Try /beastmode join <arena> or /beastmode edit.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ENGLISH);
        switch (sub) {
            case SUB_CREATE:
                handleCreate(player, args);
                return true;
            case SUB_SETSPAWN:
                handleSetSpawn(player, args);
                return true;
            case SUB_SETWAITING:
                handleSetWaiting(player, args);
                return true;
            case SUB_JOIN:
                handleJoin(player, args);
                return true;
            case SUB_TRIAL:
                handleTrial(player, args);
                return true;
            case SUB_TRIAL_CLEAR:
                handleTrialClear(player, args);
                return true;
            case SUB_CANCEL:
                handleCancel(player, args);
                return true;
            case SUB_DELETE:
                handleDelete(player, args);
                return true;
            case SUB_EDIT:
                handleEdit(player, args);
                return true;
            default:
                sessionManager.sendPrefixed(player, ChatColor.RED + "Unknown subcommand. Try /beastmode create, /beastmode setspawn, /beastmode setwaiting, /beastmode join, /beastmode cancel, /beastmode delete, or /beastmode edit.");
                return false;
        }
    }

    private void handleEdit(Player player, String[] args) {
        if (args.length == 1) {
            arenaMenu.open(player);
            return;
        }

        String arenaName = args[1];
        arenaMenu.openEditor(player, arenaName);
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Usage: /beastmode delete <arenaName>");
            return;
        }

        String arenaName = args[1];
        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Arena '" + arenaName + "' was not found.");
            return;
        }

        if (gameManager.hasActiveArena(arena.getName())) {
            gameManager.cancelArena(player, arena.getName());
        }

        boolean deleted = arenaStorage.deleteArena(arena.getName());
        if (deleted) {
            sessionManager.sendPrefixed(player, ChatColor.GREEN + "Arena '" + arena.getName() + "' has been deleted.");
            return;
        }

        sessionManager.sendPrefixed(player, ChatColor.RED + "Failed to delete arena '" + arena.getName() + "'.");
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length >= 2) {
            String arenaName = args[1];
            gameManager.cancelArena(player, arenaName);
            return;
        }
        sessionManager.cancelSession(player);
        sessionManager.sendPrefixed(player, ChatColor.YELLOW + "If you cancelled by accident, run /beastmode create <name> again.");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Usage: /beastmode create <arenaName> [trial|hunt]");
            return;
        }
        String arenaName = args[1];
        GameModeType mode = GameModeType.HUNT;
        if (args.length >= 3) {
            String modeArg = args[2].toLowerCase(Locale.ENGLISH);
            if (modeArg.equals("trial") || modeArg.equals("timetrial") || modeArg.equals("time") || modeArg.equals("practice")) {
                mode = GameModeType.TIME_TRIAL;
            } else if (!modeArg.equals("hunt") && !modeArg.equals("normal")) {
                sessionManager.sendPrefixed(player, ChatColor.RED + "Unknown mode '" + modeArg + "'. Use 'trial' or 'hunt'.");
                return;
            }
        }
        sessionManager.startSession(player, arenaName, mode);
    }

    private void handleSetSpawn(Player player, String[] args) {
        if (args.length < 3) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Usage: /beastmode setspawn <arenaName> <runner|beast>");
            return;
        }
        String arenaName = args[1];
        SetupSpawnType.fromInput(args[2]).ifPresentOrElse(role -> sessionManager.handleSpawnSelection(player, arenaName, role),
                () -> sessionManager.sendPrefixed(player, ChatColor.RED + "Spawn type must be 'runner' or 'beast'."));
    }

    private void handleSetWaiting(Player player, String[] args) {
        if (args.length < 2) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Usage: /beastmode setwaiting <arenaName>");
            return;
        }

        String arenaName = args[1];
        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Arena '" + arenaName + "' was not found.");
            return;
        }

        arenaStorage.updateWaitingSpawn(arena.getName(), player.getLocation().clone());
        sessionManager.sendPrefixed(player, ChatColor.GREEN + "Waiting spawn for arena '" + arena.getName() + "' set to your current location.");
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Usage: /beastmode join <arenaName> [runner|beast|any]");
            return;
        }
        String arenaName = args[1];
        GameManager.RolePreference preference = GameManager.RolePreference.ANY;
        if (args.length >= 3) {
            preference = parsePreference(args[2]);
            if (preference == null) {
                sessionManager.sendPrefixed(player, ChatColor.RED + "Role must be '" + ROLE_RUNNER + "', '" + ROLE_BEAST + "', or '" + ROLE_ANY + "'.");
                return;
            }
            if (preference != GameManager.RolePreference.ANY && !gameManager.canChoosePreference(player)) {
                sessionManager.sendPrefixed(player, ChatColor.RED + "Only VIP or NJOG players can choose a role preference. Joining with random role.");
                preference = GameManager.RolePreference.ANY;
            }
        }
        gameManager.joinArena(player, arenaName, preference);
    }

    private void handleTrial(Player player, String[] args) {
        if (args.length < 2) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Usage: /beastmode trial <arenaName>");
            return;
        }

        String arenaName = args[1];
        gameManager.joinTimeTrial(player, arenaName);
    }

    private void handleTrialClear(Player player, String[] args) {
        if (!player.hasPermission("beastmode.command")) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "You do not have permission to manage time-trial records.");
            return;
        }
        if (args.length < 3) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Usage: /beastmode trialclear <arenaName> <player|uuid>");
            return;
        }

        String arenaName = args[1];
        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Arena '" + arenaName + "' was not found.");
            return;
        }
        if (!arena.isTimeTrial()) {
            sessionManager.sendPrefixed(player, ChatColor.RED + "Arena '" + arena.getName() + "' does not store time-trial records.");
            return;
        }

        String targetArg = args[2];
        boolean removed = false;
        UUID targetId = null;
        try {
            targetId = UUID.fromString(targetArg);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, fall through to name lookup.
        }

        if (targetId != null) {
            removed = gameManager.getTimeTrials().deleteRecord(arena.getName(), targetId);
        }

        if (!removed) {
            OfflinePlayer offline = Bukkit.getPlayerExact(targetArg);
            if (offline == null) {
                @SuppressWarnings("deprecation")
                OfflinePlayer fetched = Bukkit.getOfflinePlayer(targetArg);
                offline = fetched;
            }
            if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                removed = gameManager.getTimeTrials().deleteRecord(arena.getName(), offline.getUniqueId());
            }
        }

        if (!removed) {
            removed = gameManager.getTimeTrials().deleteRecordByName(arena.getName(), targetArg);
        }

        if (removed) {
            sessionManager.sendPrefixed(player, ChatColor.GREEN + "Cleared best time for "
                    + ChatColor.AQUA + targetArg + ChatColor.GREEN + " on arena "
                    + ChatColor.AQUA + arena.getName() + ChatColor.GREEN + ".");
        } else {
            sessionManager.sendPrefixed(player, ChatColor.RED + "No time-trial record found for "
                    + ChatColor.AQUA + targetArg + ChatColor.RED + " on arena "
                    + ChatColor.AQUA + arena.getName() + ChatColor.RED + ".");
        }
    }

    private GameManager.RolePreference parsePreference(String input) {
        if (input == null) {
            return null;
        }
        switch (input.toLowerCase(Locale.ENGLISH)) {
            case ROLE_RUNNER:
                return GameManager.RolePreference.RUNNER;
            case ROLE_BEAST:
                return GameManager.RolePreference.BEAST;
            case ROLE_ANY:
                return GameManager.RolePreference.ANY;
            default:
                return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = List.of(SUB_CREATE, SUB_SETSPAWN, SUB_SETWAITING, SUB_JOIN, SUB_TRIAL, SUB_TRIAL_CLEAR, SUB_CANCEL, SUB_DELETE, SUB_EDIT);
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }

        String sub = args[0].toLowerCase(Locale.ENGLISH);
    if (args.length == 2 && (sub.equals(SUB_SETSPAWN)
        || sub.equals(SUB_SETWAITING)
        || sub.equals(SUB_JOIN)
        || sub.equals(SUB_TRIAL)
        || sub.equals(SUB_TRIAL_CLEAR)
        || sub.equals(SUB_CANCEL)
        || sub.equals(SUB_DELETE)
        || sub.equals(SUB_EDIT))) {
            return arenaStorage.getArenas().stream()
                    .map(ArenaDefinition::getName)
                    .filter(name -> StringUtil.startsWithIgnoreCase(name, args[1]))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 3 && sub.equals(SUB_CREATE)) {
            List<String> options = List.of("trial", "hunt");
            return StringUtil.copyPartialMatches(args[2], options, new ArrayList<>());
        }

        if (args.length == 3 && sub.equals(SUB_TRIAL_CLEAR)) {
            ArenaDefinition arena = arenaStorage.getArena(args[1]);
            if (arena == null || !arena.isTimeTrial()) {
                return Collections.emptyList();
            }
            List<TimeTrialRecord> records = gameManager.getTimeTrials().getTopRecords(arena.getName(), 20);
            if (records.isEmpty()) {
                return Collections.emptyList();
            }
            java.util.Set<String> suggestions = new java.util.LinkedHashSet<>();
            for (TimeTrialRecord record : records) {
                if (record == null) {
                    continue;
                }
                if (record.getPlayerName() != null && !record.getPlayerName().isBlank()) {
                    suggestions.add(record.getPlayerName());
                }
                suggestions.add(record.getPlayerId().toString());
            }
            return StringUtil.copyPartialMatches(args[2], suggestions, new ArrayList<>());
        }

        if (args.length == 3 && sub.equals(SUB_SETSPAWN)) {
            List<String> options = List.of(ROLE_RUNNER, ROLE_BEAST);
            return StringUtil.copyPartialMatches(args[2], options, new ArrayList<>());
        }

        if (args.length == 3 && sub.equals(SUB_JOIN)) {
            Player player = (Player) sender;
            List<String> options;
            if (gameManager.canChoosePreference(player)) {
                options = List.of(ROLE_RUNNER, ROLE_BEAST, ROLE_ANY);
            } else {
                options = List.of(ROLE_ANY);
            }
            return StringUtil.copyPartialMatches(args[2], options, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
