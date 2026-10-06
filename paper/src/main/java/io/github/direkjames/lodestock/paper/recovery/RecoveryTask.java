package io.github.direkjames.lodestock.paper.recovery;

import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.RecoverySettings;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.storage.Database;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Runs price drift and stock regeneration on a timer. The time of the last step is saved in the
 * database, so a restart does not lose progress, and time the server spent offline is caught up
 * (up to {@code recovery.catch-up-hours}).
 */
public final class RecoveryTask {
    private static final String KEY = "last_recovery";

    private final LodestockPlugin plugin;
    private final Database db;
    private BukkitTask task;
    private long lastStep; // epoch millis of the last recovery step that was applied

    public RecoveryTask(LodestockPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Catches up on the time the server was offline, then starts the timer. */
    public void start() {
        long now = System.currentTimeMillis();
        long saved = db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT meta_value FROM meta WHERE meta_key = ?")) {
                ps.setString(1, KEY);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Long.parseLong(rs.getString(1)) : -1L;
                }
            } catch (NumberFormatException e) {
                return -1L;
            }
        }).join();

        Market market = plugin.market();
        RecoverySettings rs = market.recovery();
        long interval = interval(rs);
        if (saved < 0 || saved > now) {
            lastStep = now; // first run, or the clock moved back: nothing to catch up
        } else if (!rs.active() || rs.catchUpHours() == 0) {
            lastStep = now;
        } else {
            long elapsed = now - saved;
            long cap = rs.catchUpHours() * 3_600_000L;
            if (elapsed > cap) {
                market.applyRecovery((int) (cap / interval));
                lastStep = now;
            } else {
                int steps = (int) (elapsed / interval);
                market.applyRecovery(steps);
                lastStep = saved + steps * interval; // keep the leftover time, so frequent restarts still recover
            }
        }
        saveLastStep();
        schedule();
    }

    /** Applies new interval settings after a reload. */
    public void reschedule() {
        schedule();
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void schedule() {
        stop();
        RecoverySettings rs = plugin.market().recovery();
        if (!rs.active()) return;
        long interval = interval(rs);
        long wait = Math.max(1_000L, interval - (System.currentTimeMillis() - lastStep));
        long delayTicks = Math.max(20L, wait / 50L);
        long periodTicks = Math.max(20L, interval / 50L);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, delayTicks, periodTicks);
    }

    private void tick() {
        Market market = plugin.market();
        long interval = interval(market.recovery());
        long now = System.currentTimeMillis();
        if (now < lastStep) lastStep = now; // the clock moved back
        int steps = (int) Math.min(Integer.MAX_VALUE, (now - lastStep) / interval);
        if (steps < 1) return;
        market.applyRecovery(steps);
        lastStep += steps * interval;
        saveLastStep();
    }

    private void saveLastStep() {
        long value = lastStep;
        db.write("save last recovery time", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO meta (meta_key, meta_value) VALUES (?, ?) "
                            + "ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value")) {
                ps.setString(1, KEY);
                ps.setString(2, Long.toString(value));
                ps.executeUpdate();
            }
        });
    }

    private static long interval(RecoverySettings rs) {
        return rs.intervalMinutes() * 60_000L;
    }
}
