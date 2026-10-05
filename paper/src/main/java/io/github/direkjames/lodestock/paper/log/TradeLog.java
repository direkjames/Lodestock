package io.github.direkjames.lodestock.paper.log;

import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Trade history per player, plus an admin action log. Replaced by the database in Phase 2. */
public final class TradeLog {
    public record Entry(long time, String player, String action, String item, int amount, double money) {}

    private final File dir;
    private final Logger log;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Lodestock-Log");
        thread.setDaemon(true);
        return thread;
    });

    public TradeLog(File dataFolder, Logger log) {
        this.dir = new File(dataFolder, "history");
        this.log = log;
        if (!dir.exists() && !dir.mkdirs()) log.warning("Could not create the history folder.");
    }

    public void trade(Player player, String action, String itemId, int amount, double money) {
        String line = System.currentTimeMillis() + "|" + clean(player.getName()) + "|" + action + "|" + clean(itemId)
                + "|" + amount + "|" + String.format(Locale.ROOT, "%.2f", money);
        append(new File(dir, player.getUniqueId() + ".log"), line);
    }

    public void admin(String who, String action, String details) {
        append(new File(dir, "admin.log"),
                System.currentTimeMillis() + "|" + clean(who) + "|" + clean(action) + "|" + clean(details));
    }

    /** Newest first. Runs on the log thread, after every earlier write has finished. */
    public CompletableFuture<List<Entry>> readAsync(UUID uuid) {
        try {
            return CompletableFuture.supplyAsync(() -> read(uuid), writer);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /** Removes lines older than the given number of days. 0 or less keeps everything. */
    public void prune(int keepDays) {
        if (keepDays <= 0) return;
        long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
        try {
            writer.execute(() -> pruneFiles(cutoff));
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    /** Waits briefly so queued lines reach the disk. */
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) log.warning("Some history lines may not have been saved.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void append(File file, String line) {
        try {
            writer.execute(() -> {
                try {
                    Files.writeString(file.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    log.warning("Could not write to " + file.getName() + ": " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private List<Entry> read(UUID uuid) {
        File file = new File(dir, uuid + ".log");
        if (!file.exists()) return List.of();
        List<Entry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                Entry entry = parse(line);
                if (entry != null) entries.add(entry);
            }
        } catch (IOException e) {
            log.warning("Could not read " + file.getName() + ": " + e.getMessage());
        }
        Collections.reverse(entries);
        return entries;
    }

    private static Entry parse(String line) {
        String[] p = line.split("\\|", 6);
        if (p.length < 6) return null;
        try {
            return new Entry(Long.parseLong(p[0]), p[1], p[2], p[3], Integer.parseInt(p[4]), Double.parseDouble(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void pruneFiles(long cutoff) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null) return;
        for (File file : files) {
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                List<String> kept = new ArrayList<>();
                for (String line : lines) {
                    int bar = line.indexOf('|');
                    if (bar <= 0) continue;
                    try {
                        if (Long.parseLong(line.substring(0, bar)) >= cutoff) kept.add(line);
                    } catch (NumberFormatException ignored) {
                        // damaged line, dropped
                    }
                }
                if (kept.size() == lines.size()) continue;
                if (kept.isEmpty()) {
                    Files.deleteIfExists(file.toPath());
                    continue;
                }
                File tmp = new File(dir, file.getName() + ".tmp");
                Files.write(tmp.toPath(), kept, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warning("Could not clean up " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static String clean(String value) {
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }
}