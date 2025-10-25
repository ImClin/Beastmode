package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Aggregates service construction for {@link GameManager} to keep wiring centralized.
 */
final class GameServices {

    final ActiveArenaDirectory arenaDirectory;
    final ArenaStatusService statusService;
    final PlayerSupportService playerSupport;
    final ArenaWaitingService waitingService;
    final ArenaBarrierService barrierService;
    final ArenaLifecycleService arenaLifecycle;
    final CountdownService countdowns;
    final RoleSelectionService roleSelection;
    final MatchSetupService matchSetup;
    final ArenaMessagingService messaging;
    final PlayerTransitionService transitions;
    final MatchOutcomeService matchOutcome;
    final MatchFlowService matchFlow;
    final MatchSelectionService selectionService;
    final PlayerPreferenceService preferenceService;
    final ArenaDepartureService departureService;
    final MatchCompletionService completionService;
    final MatchEliminationService eliminationService;
    final MatchOrchestrationService orchestration;
    final ArenaQueueService queueService;

    private GameServices(ActiveArenaDirectory arenaDirectory,
                         ArenaStatusService statusService,
                         PlayerSupportService playerSupport,
                         ArenaWaitingService waitingService,
                         ArenaBarrierService barrierService,
                         ArenaLifecycleService arenaLifecycle,
                         CountdownService countdowns,
                         RoleSelectionService roleSelection,
                         MatchSetupService matchSetup,
                         ArenaMessagingService messaging,
                         PlayerTransitionService transitions,
                         MatchOutcomeService matchOutcome,
                         MatchFlowService matchFlow,
                         MatchSelectionService selectionService,
                         PlayerPreferenceService preferenceService,
                         ArenaDepartureService departureService,
                         MatchCompletionService completionService,
                         MatchEliminationService eliminationService,
                         MatchOrchestrationService orchestration,
                         ArenaQueueService queueService) {
        this.arenaDirectory = arenaDirectory;
        this.statusService = statusService;
        this.playerSupport = playerSupport;
        this.waitingService = waitingService;
        this.barrierService = barrierService;
        this.arenaLifecycle = arenaLifecycle;
        this.countdowns = countdowns;
        this.roleSelection = roleSelection;
        this.matchSetup = matchSetup;
        this.messaging = messaging;
        this.transitions = transitions;
        this.matchOutcome = matchOutcome;
        this.matchFlow = matchFlow;
        this.selectionService = selectionService;
        this.preferenceService = preferenceService;
        this.departureService = departureService;
        this.completionService = completionService;
        this.eliminationService = eliminationService;
        this.orchestration = orchestration;
        this.queueService = queueService;
    }

    static GameServices create(Beastmode plugin,
                               ArenaStorage arenaStorage,
                               String prefix,
                               NamespacedKey exitTokenKey,
                               NamespacedKey preferenceKey,
                               ItemStack exitTokenTemplate,
                               int longEffectDurationTicks,
                               String defaultBeastName,
                               String vipPermission,
                               String njogPermission) {
        ActiveArenaDirectory directory = new ActiveArenaDirectory();
        ArenaStatusService statusService = new ArenaStatusService();
        PlayerSupportService playerSupport = new PlayerSupportService(plugin, prefix, longEffectDurationTicks,
            exitTokenKey, preferenceKey, exitTokenTemplate);
        ArenaWaitingService waitingService = new ArenaWaitingService(playerSupport, prefix);
        ArenaBarrierService barrierService = new ArenaBarrierService();
        ArenaLifecycleService arenaLifecycle = new ArenaLifecycleService(directory, barrierService, statusService::notifyArenaName);
        CountdownService countdowns = new CountdownService(plugin);
    RoleSelectionService roleSelection = new RoleSelectionService(vipPermission, njogPermission);
        MatchSetupService matchSetup = new MatchSetupService(playerSupport, prefix);
        ArenaMessagingService messaging = new ArenaMessagingService(prefix, defaultBeastName);
        PlayerTransitionService transitions = new PlayerTransitionService(plugin, playerSupport);
        MatchOutcomeService matchOutcome = new MatchOutcomeService(prefix, defaultBeastName, playerSupport, transitions);
        MatchFlowService matchFlow = new MatchFlowService(plugin, countdowns, barrierService, playerSupport,
            messaging, prefix, longEffectDurationTicks);
        MatchSelectionService selectionService = new MatchSelectionService(countdowns, roleSelection, matchSetup,
            messaging, matchFlow, waitingService, arenaLifecycle, statusService::notifyArenaStatus);
        PlayerPreferenceService preferenceService = new PlayerPreferenceService(directory, playerSupport, roleSelection, prefix);
        ArenaDepartureService departureService = new ArenaDepartureService(prefix, playerSupport, transitions,
            waitingService, arenaLifecycle, matchOutcome, statusService::notifyArenaStatus);
        MatchCompletionService completionService = new MatchCompletionService(directory, departureService);
        MatchEliminationService eliminationService = new MatchEliminationService(directory, playerSupport, transitions, departureService);
        MatchOrchestrationService orchestration = new MatchOrchestrationService(directory, arenaStorage, arenaLifecycle,
            waitingService, selectionService, departureService, statusService, prefix);
        ArenaQueueService queueService = new ArenaQueueService(arenaStorage, directory, playerSupport,
            roleSelection, waitingService, orchestration, statusService, prefix);

        return new GameServices(directory, statusService, playerSupport, waitingService, barrierService,
            arenaLifecycle, countdowns, roleSelection, matchSetup, messaging, transitions, matchOutcome,
            matchFlow, selectionService, preferenceService, departureService, completionService,
            eliminationService, orchestration, queueService);
    }
}
