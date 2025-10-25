package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Coordinates timed countdowns for arena events while ensuring tasks are tracked
 * and arenas clear themselves if all participants leave mid-timer.
 */
final class CountdownService {

    private final Beastmode plugin;

    CountdownService(Beastmode plugin) {
        this.plugin = plugin;
    }

    void startCountdown(ActiveArena activeArena,
                        int seconds,
                        Supplier<List<Player>> participantsSupplier,
                        BiConsumer<List<Player>, Integer> announcer,
                        Runnable onAbandoned,
                        Runnable completion) {
        if (seconds <= 0) {
            if (completion != null) {
                completion.run();
            }
            return;
        }

        CountdownRunnable runnable = new CountdownRunnable(activeArena, seconds,
                participantsSupplier, announcer, onAbandoned, completion);
        runnable.start();
    }

    void startSelectionCountdown(ActiveArena activeArena,
                                 int totalSeconds,
                                 int wheelSeconds,
                                 Supplier<List<Player>> participantsSupplier,
                                 IntSupplier requiredParticipantsSupplier,
                                 Consumer<List<Player>> onInsufficientParticipants,
                                 BiConsumer<List<Player>, Integer> announcer,
                                 Runnable onStartWheel,
                                 Runnable onAbandoned) {
        if (totalSeconds <= wheelSeconds) {
            if (onStartWheel != null) {
                onStartWheel.run();
            }
            return;
        }

        SelectionCountdownRunnable runnable = new SelectionCountdownRunnable(activeArena,
                totalSeconds, wheelSeconds, participantsSupplier, requiredParticipantsSupplier,
                onInsufficientParticipants, announcer, onStartWheel, onAbandoned);
        runnable.start();
    }

    void startWheelSelection(ActiveArena activeArena,
                             int durationSeconds,
                             Supplier<List<Player>> participantsSupplier,
                             IntSupplier requiredParticipantsSupplier,
                             Consumer<List<Player>> onInsufficientParticipants,
                             Function<List<Player>, Player> finalSelector,
                             BiConsumer<List<Player>, Player> highlightAnnouncer,
                             BiConsumer<List<Player>, Player> finalPreviewAnnouncer,
                             BiConsumer<List<Player>, Player> finalizeAction,
                             Runnable onAbandoned) {
        if (durationSeconds <= 0) {
            List<Player> participants = participantsSupplier != null ? participantsSupplier.get() : Collections.emptyList();
            if (participants == null || participants.isEmpty()) {
                if (onAbandoned != null) {
                    onAbandoned.run();
                }
                return;
            }
            Player chosen = finalSelector != null ? finalSelector.apply(participants) : null;
            if (finalPreviewAnnouncer != null) {
                finalPreviewAnnouncer.accept(participants, chosen);
            }
            if (finalizeAction != null) {
                finalizeAction.accept(participants, chosen);
            }
            return;
        }

        WheelSelectionRunnable runnable = new WheelSelectionRunnable(activeArena, durationSeconds,
                participantsSupplier, requiredParticipantsSupplier, onInsufficientParticipants,
                finalSelector, highlightAnnouncer, finalPreviewAnnouncer, finalizeAction, onAbandoned);
        runnable.start();
    }

    private final class CountdownRunnable extends BukkitRunnable {
        private final ActiveArena activeArena;
        private final Supplier<List<Player>> participantsSupplier;
        private final BiConsumer<List<Player>, Integer> announcer;
        private final Runnable onAbandoned;
        private final Runnable completion;
        private int remaining;
        private BukkitTask handle;

        private CountdownRunnable(ActiveArena activeArena,
                                  int seconds,
                                  Supplier<List<Player>> participantsSupplier,
                                  BiConsumer<List<Player>, Integer> announcer,
                                  Runnable onAbandoned,
                                  Runnable completion) {
            this.activeArena = activeArena;
            this.remaining = seconds;
            this.participantsSupplier = participantsSupplier != null
                    ? participantsSupplier
                    : Collections::emptyList;
            this.announcer = announcer;
            this.onAbandoned = onAbandoned;
            this.completion = completion;
        }

        private void start() {
            handle = runTaskTimer(plugin, 0L, 20L);
            activeArena.registerTask(handle);
        }

        @Override
        public void run() {
            List<Player> participants = participantsSupplier.get();
            if (participants == null || participants.isEmpty()) {
                cancel();
                if (onAbandoned != null) {
                    onAbandoned.run();
                }
                return;
            }

            if (remaining <= 0) {
                cancel();
                if (completion != null) {
                    completion.run();
                }
                return;
            }

            if (announcer != null) {
                announcer.accept(participants, remaining);
            }
            remaining--;
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            if (handle != null) {
                activeArena.unregisterTask(handle);
            }
        }
    }

    private final class SelectionCountdownRunnable extends BukkitRunnable {
        private final ActiveArena activeArena;
        private final int wheelSeconds;
        private final Supplier<List<Player>> participantsSupplier;
        private final IntSupplier requiredParticipantsSupplier;
        private final Consumer<List<Player>> onInsufficientParticipants;
        private final BiConsumer<List<Player>, Integer> announcer;
        private final Runnable onStartWheel;
        private final Runnable onAbandoned;
        private int remaining;
        private BukkitTask handle;

        private SelectionCountdownRunnable(ActiveArena activeArena,
                                           int totalSeconds,
                                           int wheelSeconds,
                                           Supplier<List<Player>> participantsSupplier,
                                           IntSupplier requiredParticipantsSupplier,
                                           Consumer<List<Player>> onInsufficientParticipants,
                                           BiConsumer<List<Player>, Integer> announcer,
                                           Runnable onStartWheel,
                                           Runnable onAbandoned) {
            this.activeArena = activeArena;
            this.remaining = totalSeconds;
            this.wheelSeconds = Math.max(wheelSeconds, 0);
            this.participantsSupplier = participantsSupplier != null ? participantsSupplier : Collections::emptyList;
            this.requiredParticipantsSupplier = requiredParticipantsSupplier != null ? requiredParticipantsSupplier : () -> 0;
            this.onInsufficientParticipants = onInsufficientParticipants;
            this.announcer = announcer;
            this.onStartWheel = onStartWheel;
            this.onAbandoned = onAbandoned;
        }

        private void start() {
            List<Player> initialParticipants = safeParticipants();
            if (initialParticipants.isEmpty()) {
                abandonArena();
                return;
            }

            if (announcer != null) {
                announcer.accept(initialParticipants, remaining);
            }

            handle = runTaskTimer(plugin, 20L, 20L);
            activeArena.registerTask(handle);
        }

        @Override
        public void run() {
            List<Player> participants = safeParticipants();
            if (participants.isEmpty()) {
                cancel();
                abandonArena();
                return;
            }

            int required = Math.max(requiredParticipantsSupplier.getAsInt(), 0);
            if (participants.size() < required) {
                cancel();
                if (onInsufficientParticipants != null) {
                    onInsufficientParticipants.accept(participants);
                }
                return;
            }

            remaining--;
            if (remaining > wheelSeconds) {
                if (announcer != null) {
                    announcer.accept(participants, remaining);
                }
                return;
            }

            cancel();
            if (onStartWheel != null) {
                onStartWheel.run();
            }
        }

        private List<Player> safeParticipants() {
            List<Player> participants = participantsSupplier.get();
            if (participants == null || participants.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(participants);
        }

        private void abandonArena() {
            if (onAbandoned != null) {
                onAbandoned.run();
            }
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            if (handle != null) {
                activeArena.unregisterTask(handle);
            }
        }
    }

    private final class WheelSelectionRunnable extends BukkitRunnable {
        private static final int PERIOD_TICKS = 4;

        private final ActiveArena activeArena;
        private final Supplier<List<Player>> participantsSupplier;
        private final IntSupplier requiredParticipantsSupplier;
        private final Consumer<List<Player>> onInsufficientParticipants;
        private final Function<List<Player>, Player> finalSelector;
        private final BiConsumer<List<Player>, Player> highlightAnnouncer;
        private final BiConsumer<List<Player>, Player> finalPreviewAnnouncer;
        private final BiConsumer<List<Player>, Player> finalizeAction;
        private final Runnable onAbandoned;
        private int ticksRemaining;
        private int index;
        private boolean finalized;
        private BukkitTask handle;

        private WheelSelectionRunnable(ActiveArena activeArena,
                                       int durationSeconds,
                                       Supplier<List<Player>> participantsSupplier,
                                       IntSupplier requiredParticipantsSupplier,
                                       Consumer<List<Player>> onInsufficientParticipants,
                                       Function<List<Player>, Player> finalSelector,
                                       BiConsumer<List<Player>, Player> highlightAnnouncer,
                                       BiConsumer<List<Player>, Player> finalPreviewAnnouncer,
                                       BiConsumer<List<Player>, Player> finalizeAction,
                                       Runnable onAbandoned) {
            this.activeArena = activeArena;
            this.participantsSupplier = participantsSupplier != null ? participantsSupplier : Collections::emptyList;
            this.requiredParticipantsSupplier = requiredParticipantsSupplier != null ? requiredParticipantsSupplier : () -> 0;
            this.onInsufficientParticipants = onInsufficientParticipants;
            this.finalSelector = finalSelector;
            this.highlightAnnouncer = highlightAnnouncer;
            this.finalPreviewAnnouncer = finalPreviewAnnouncer;
            this.finalizeAction = finalizeAction;
            this.onAbandoned = onAbandoned;
            this.ticksRemaining = Math.max(durationSeconds, 0) * 20;
            this.index = ThreadLocalRandom.current().nextInt(Math.max(activeArena.getPlayerIds().size(), 1));
        }

        private void start() {
            handle = runTaskTimer(plugin, 0L, PERIOD_TICKS);
            activeArena.registerTask(handle);
        }

        @Override
        public void run() {
            List<Player> participants = safeParticipants();
            if (participants.isEmpty()) {
                cancel();
                abandonArena();
                return;
            }

            int required = Math.max(requiredParticipantsSupplier.getAsInt(), 0);
            if (participants.size() < required) {
                cancel();
                if (onInsufficientParticipants != null) {
                    onInsufficientParticipants.accept(participants);
                }
                return;
            }

            if (ticksRemaining <= 20 && !finalized) {
                Player chosen = finalSelector != null ? finalSelector.apply(participants) : null;
                if (finalPreviewAnnouncer != null) {
                    finalPreviewAnnouncer.accept(participants, chosen);
                }
                if (finalizeAction != null) {
                    finalizeAction.accept(participants, chosen);
                }
                finalized = true;
                cancel();
                return;
            }

            Player highlighted = selectCurrentPlayer(participants);
            if (highlighted != null && highlightAnnouncer != null) {
                highlightAnnouncer.accept(participants, highlighted);
            }
            index++;
            ticksRemaining -= PERIOD_TICKS;
        }

        private Player selectCurrentPlayer(List<Player> participants) {
            if (participants.isEmpty()) {
                return null;
            }
            return participants.get(index % participants.size());
        }

        private List<Player> safeParticipants() {
            List<Player> participants = participantsSupplier.get();
            if (participants == null || participants.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(participants);
        }

        private void abandonArena() {
            if (onAbandoned != null) {
                onAbandoned.run();
            }
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            if (handle != null) {
                activeArena.unregisterTask(handle);
            }
        }
    }
}
