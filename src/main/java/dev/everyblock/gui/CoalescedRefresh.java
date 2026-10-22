package dev.everyblock.gui;

import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Runs a refresh at most once per interval, folding any requests made while one is pending into it. */
final class CoalescedRefresh {
    static final long INTERVAL_TICKS = 20;
    private static final long NANOS_PER_TICK = 50_000_000L;

    private final Plugin plugin;
    private final Runnable action;
    private final LongSupplier clock;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile long lastRun;

    CoalescedRefresh(Plugin plugin, Runnable action) {
        this(plugin, action, System::nanoTime);
    }

    CoalescedRefresh(Plugin plugin, Runnable action, LongSupplier clock) {
        this.plugin = plugin;
        this.action = action;
        this.clock = clock;
        this.lastRun = clock.getAsLong() - INTERVAL_TICKS * NANOS_PER_TICK;
    }

    void request() {
        if (!pending.compareAndSet(false, true)) {
            return;
        }
        long elapsedTicks = (clock.getAsLong() - lastRun) / NANOS_PER_TICK;
        long delay = Math.clamp(INTERVAL_TICKS - elapsedTicks, 1, INTERVAL_TICKS);
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::run, delay);
        } catch (IllegalPluginAccessException disabled) {
            pending.set(false);
        }
    }

    private void run() {
        pending.set(false);
        lastRun = clock.getAsLong();
        action.run();
    }
}
