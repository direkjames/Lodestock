package io.github.direkjames.lodestock.paper.discord;

import io.github.direkjames.lodestock.api.TradeType;
import io.github.direkjames.lodestock.api.event.LodestockTradeEvent;
import io.github.direkjames.lodestock.core.discord.DiscordPayload;
import io.github.direkjames.lodestock.core.discord.DiscordPayload.Embed;
import io.github.direkjames.lodestock.core.discord.DiscordPayload.Field;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.discord.DiscordSender.Result;
import io.github.direkjames.lodestock.paper.log.TradeLog;
import io.github.direkjames.lodestock.paper.util.ItemNames;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Posts big trades, admin changes and the daily summary to a Discord webhook (see discord.yml). */
public final class DiscordService implements Listener {
    private static final int COLOR_SELL = 0x2ECC71;
    private static final int COLOR_BUY = 0x3498DB;
    private static final int COLOR_CRASH = 0xE74C3C;
    private static final int COLOR_ADMIN = 0xF1C40F;
    private static final int COLOR_RESET = 0xE67E22;
    private static final int COLOR_SUMMARY = 0x9B59B6;
    private static final int COLOR_TEST = 0x95A5A6;
    private static final int TOP_LINES = 5;

    private final LodestockPlugin plugin;
    private final DiscordSender sender = new DiscordSender();
    private volatile DiscordSettings settings;
    private BukkitTask summaryTask;
    private LocalDate lastSummaryDay;
    private long lastWarning;

    public DiscordService(LodestockPlugin plugin) {
        this.plugin = plugin;
        this.settings = DiscordSettings.load(new YamlConfiguration());
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Reads discord.yml again and restarts the daily timer. Never throws. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "discord.yml");
        try {
            settings = DiscordSettings.load(YamlConfiguration.loadConfiguration(file));
        } catch (RuntimeException e) {
            settings = DiscordSettings.load(new YamlConfiguration());
            plugin.getLogger().warning("Could not read discord.yml, the Discord webhook is off: " + e.getMessage());
        }
        for (String problem : settings.problems()) plugin.getLogger().warning("discord.yml: " + problem);

        if (summaryTask != null) summaryTask.cancel();
        summaryTask = null;
        if (settings.ready() && settings.summary()) {
            // A time that has already passed today is not sent late: the first summary is tomorrow.
            ZonedDateTime now = ZonedDateTime.now(settings.zone());
            lastSummaryDay = now.toLocalTime().isBefore(settings.summaryTime()) ? null : now.toLocalDate();
            summaryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkSummary, 600L, 600L);
        }
    }

    public void shutdown() {
        if (summaryTask != null) summaryTask.cancel();
        sender.shutdown();
    }

    public boolean ready() {
        return settings.ready();
    }

    /** Why nothing is being sent, in a few words. Empty when it works. */
    public String problem() {
        DiscordSettings s = settings;
        if (!s.enabled()) return "the webhook is off (enabled: false in discord.yml)";
        if (!s.problems().isEmpty() && !s.ready()) return s.problems().get(0);
        return "";
    }

    // ---------- what gets sent ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTrade(LodestockTradeEvent event) {
        DiscordSettings s = settings;
        if (!s.ready() || !s.bigTrades() || event.getTotal() < s.minTotal()) return;
        boolean sell = event.getType() == TradeType.SELL;
        String item = ItemNames.pretty(event.getItemId());
        String money = plugin.economy().format(event.getTotal());
        String text = "**" + DiscordPayload.escapeMarkdown(event.getPlayer().getName()) + "** "
                + (sell ? "sold " : "bought ") + "**" + event.getAmount() + "× " + item + "** for **" + money + "**";
        post(s, new Embed(sell ? "Big sale" : "Big purchase", text, sell ? COLOR_SELL : COLOR_BUY,
                List.of(new Field("Price now", plugin.economy().format(event.getNewPrice()), true),
                        new Field("Stock now", String.valueOf(event.getNewStock()), true)),
                "Lodestock", Instant.now()));
    }

    /** Called after every hand-made market change (command, API). */
    public void adminAction(String source, String action, String details) {
        DiscordSettings s = settings;
        if (!s.ready() || !s.adminActions()) return;
        String title;
        int color;
        switch (action) {
            case "CRASH" -> { title = "Market crash"; color = COLOR_CRASH; }
            case "SURGE" -> { title = "Market surge"; color = COLOR_SELL; }
            case "RESET" -> { title = "Market reset"; color = COLOR_RESET; }
            case "SETPRICE" -> { title = "Price set by hand"; color = COLOR_ADMIN; }
            case "SETSTOCK" -> { title = "Stock set by hand"; color = COLOR_ADMIN; }
            default -> { title = "Market change"; color = COLOR_ADMIN; }
        }
        post(s, new Embed(title, null, color,
                List.of(new Field("By", DiscordPayload.escapeMarkdown(source), true),
                        new Field("Details", "`" + details.replace('`', '\'') + "`", true)),
                "Lodestock", Instant.now()));
    }

    /** A message to check that the webhook works. */
    public CompletableFuture<Result> test() {
        DiscordSettings s = settings;
        if (!s.ready()) return CompletableFuture.completedFuture(Result.failed(problem()));
        Embed embed = new Embed("Lodestock webhook test", "If you can read this, the webhook works.", COLOR_TEST,
                List.of(new Field("Server", plugin.getServer().getName(), true),
                        new Field("Lodestock", plugin.getPluginMeta().getVersion(), true)),
                "Lodestock", Instant.now());
        return post(s, embed);
    }

    /** Sends the last 24 hours now. A day with no trades is only skipped when {@code force} is false. */
    public CompletableFuture<Result> summary(boolean force) {
        DiscordSettings s = settings;
        if (!s.ready()) return CompletableFuture.completedFuture(Result.failed(problem()));
        long since = System.currentTimeMillis() - 24L * 3_600_000L;
        CompletableFuture<Result> done = new CompletableFuture<>();
        plugin.tradeLog().summaryOrFail(since, Long.MAX_VALUE, TOP_LINES).whenComplete((summary, error) -> {
            if (error != null || summary == null) {
                done.complete(Result.failed("could not read the trade history"));
                return;
            }
            if (summary.trades() == 0 && !force) {
                done.complete(Result.OK);
                return;
            }
            // Money is formatted on the main thread, as the economy plugin expects.
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Embed embed = summaryEmbed(summary);
                    post(s, embed).thenAccept(done::complete);
                });
            } catch (RuntimeException e) { // the plugin was disabled in the meantime
                done.complete(Result.failed("the plugin is shutting down"));
            }
        });
        return done;
    }

    private void checkSummary() {
        DiscordSettings s = settings;
        if (!s.ready() || !s.summary()) return;
        ZonedDateTime now = ZonedDateTime.now(s.zone());
        if (now.toLocalDate().equals(lastSummaryDay) || now.toLocalTime().isBefore(s.summaryTime())) return;
        lastSummaryDay = now.toLocalDate();
        summary(false).thenAccept(result -> {
            if (!result.ok()) warn("the daily summary failed: " + result.error());
        });
    }

    private Embed summaryEmbed(TradeLog.Summary s) {
        var eco = plugin.economy();
        double created = s.created();
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("Trades", s.trades() + " by " + s.players() + " player(s)", true));
        fields.add(new Field("Players spent", eco.format(s.paidIn()), true));
        fields.add(new Field("Market paid out", eco.format(s.paidOut()), true));
        fields.add(new Field(created >= 0 ? "Money created" : "Money removed", eco.format(Math.abs(created)), true));
        if (!s.topEarners().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int rank = 1;
            for (TradeLog.PlayerNet p : s.topEarners()) {
                sb.append(rank++).append(". ").append(DiscordPayload.escapeMarkdown(p.player())).append(" — ")
                        .append(p.net() >= 0 ? "+" : "-").append(eco.format(Math.abs(p.net()))).append('\n');
            }
            fields.add(new Field("Top net earners", sb.toString().stripTrailing(), false));
        }
        if (!s.topItems().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int rank = 1;
            for (TradeLog.ItemPayout p : s.topItems()) {
                sb.append(rank++).append(". ").append(ItemNames.pretty(p.item())).append(" — ")
                        .append(eco.format(p.paidOut())).append('\n');
            }
            fields.add(new Field("Paid out the most", sb.toString().stripTrailing(), false));
        }
        return new Embed("Daily market summary", "The last 24 hours.", COLOR_SUMMARY, fields, "Lodestock", Instant.now());
    }

    private CompletableFuture<Result> post(DiscordSettings s, Embed embed) {
        String json = DiscordPayload.json(s.username(), s.avatarUrl(), List.of(embed));
        return sender.send(s.url(), json).whenComplete((result, error) -> {
            if (error != null) warn("could not send a message: " + error.getMessage());
            else if (!result.ok()) warn("could not send a message: " + result.error());
        });
    }

    /** At most one warning a minute, so a broken webhook cannot fill the console. */
    private void warn(String text) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastWarning < 60_000) return;
            lastWarning = now;
        }
        plugin.getLogger().warning("Discord: " + text);
    }
}
