package com.colin.beastmode.game;

import com.colin.beastmode.model.ArenaDefinition;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the selection countdown and wheel flow before a hunt begins.
 */
final class MatchSelectionService {

    private final CountdownService countdowns;
    private final RoleSelectionService roleSelection;
    private final MatchSetupService matchSetup;
    private final ArenaMessagingService messaging;
    private final MatchFlowService matchFlow;
    private final ArenaWaitingService waitingService;
    private final ArenaLifecycleService lifecycle;
    private final Consumer<ActiveArena> statusNotifier;

    MatchSelectionService(CountdownService countdowns,
                          RoleSelectionService roleSelection,
                          MatchSetupService matchSetup,
                          ArenaMessagingService messaging,
                          MatchFlowService matchFlow,
                          ArenaWaitingService waitingService,
                          ArenaLifecycleService lifecycle,
                          Consumer<ActiveArena> statusNotifier) {
        this.countdowns = countdowns;
        this.roleSelection = roleSelection;
        this.matchSetup = matchSetup;
        this.messaging = messaging;
        this.matchFlow = matchFlow;
        this.waitingService = waitingService;
        this.lifecycle = lifecycle;
        this.statusNotifier = statusNotifier;
    }

    void maybeStartSelection(String key, ActiveArena activeArena) {
        List<Player> participants = lifecycle.collectParticipants(activeArena);
        if (participants.isEmpty()) {
            lifecycle.cleanupArena(key, activeArena);
            return;
        }

        ArenaDefinition arena = activeArena.getArena();
        GameModeType mode = activeArena.getMode();
        int required = waitingService.getRequiredParticipants(arena, mode);
        if (participants.size() < required) {
            activeArena.setSelecting(false);
            statusNotifier.accept(activeArena);
            waitingService.notifyWaitingForPlayers(activeArena, participants);
            return;
        }

        if (mode.isTimeTrial()) {
            startTimeTrial(key, activeArena, participants);
            return;
        }

        if (activeArena.isSelecting()) {
            return;
        }

        activeArena.setSelecting(true);
        statusNotifier.accept(activeArena);
        countdowns.startSelectionCountdown(activeArena, 10, 5,
                () -> lifecycle.collectParticipants(activeArena),
                () -> waitingService.getRequiredParticipants(activeArena.getArena(), activeArena.getMode()),
                players -> {
                    activeArena.setSelecting(false);
                    statusNotifier.accept(activeArena);
                    waitingService.notifyWaitingForPlayers(activeArena, players);
                },
                messaging::selectionCountdown,
                () -> startWheelSelection(key, activeArena, 5),
                () -> lifecycle.cleanupArena(key, activeArena));
    }

    private void startWheelSelection(String key, ActiveArena activeArena, int durationSeconds) {
        countdowns.startWheelSelection(activeArena, durationSeconds,
                () -> lifecycle.collectParticipants(activeArena),
                () -> waitingService.getRequiredParticipants(activeArena.getArena(), activeArena.getMode()),
                players -> {
                    activeArena.setSelecting(false);
                    statusNotifier.accept(activeArena);
                    waitingService.notifyWaitingForPlayers(activeArena, players);
                },
                participants -> roleSelection.selectBeast(activeArena, participants, null),
                messaging::wheelHighlight,
                messaging::wheelFinal,
                (participants, chosen) -> finalizeSelection(key, activeArena, participants, chosen),
                () -> lifecycle.cleanupArena(key, activeArena));
    }

    private void finalizeSelection(String key,
                                   ActiveArena activeArena,
                                   List<Player> participants,
                                   Player selectedBeast) {
        List<Player> current = matchSetup.resolveParticipants(activeArena, participants);
        if (current.isEmpty()) {
            lifecycle.cleanupArena(key, activeArena);
            return;
        }

        ArenaDefinition arena = activeArena.getArena();
        if (!matchSetup.validateSpawns(current, arena, activeArena.getMode())) {
            lifecycle.cleanupArena(key, activeArena);
            return;
        }

        Player beast = roleSelection.selectBeast(activeArena, current, selectedBeast);
        matchSetup.assignRoles(activeArena, current, beast);
        statusNotifier.accept(activeArena);
        messaging.announceBeast(current, beast);
        matchFlow.scheduleMatchStart(activeArena, arena, beast,
                () -> lifecycle.collectParticipants(activeArena),
                restoreWalls -> lifecycle.cleanupArena(key, activeArena, restoreWalls),
                () -> {
                    activeArena.setMatchActive(true);
                    statusNotifier.accept(activeArena);
                });
    }

    private void startTimeTrial(String key,
                                ActiveArena activeArena,
                                List<Player> participants) {
        List<Player> current = matchSetup.resolveParticipants(activeArena, participants);
        if (current.isEmpty()) {
            lifecycle.cleanupArena(key, activeArena);
            return;
        }

        ArenaDefinition arena = activeArena.getArena();
        if (!matchSetup.validateSpawns(current, arena, GameModeType.TIME_TRIAL)) {
            lifecycle.cleanupArena(key, activeArena);
            return;
        }

        matchSetup.assignRoles(activeArena, current, null);
        statusNotifier.accept(activeArena);
        messaging.announceBeast(current, null);
        matchFlow.scheduleMatchStart(activeArena, arena, null,
                () -> lifecycle.collectParticipants(activeArena),
                restoreWalls -> lifecycle.cleanupArena(key, activeArena, restoreWalls),
                () -> {
                    activeArena.setMatchActive(true);
                    statusNotifier.accept(activeArena);
                });
    }
}
