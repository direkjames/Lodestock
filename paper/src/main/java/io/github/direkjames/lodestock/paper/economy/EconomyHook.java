package io.github.direkjames.lodestock.paper.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Talks to whatever economy plugin is registered with Vault (such as EssentialsX). */
public final class EconomyHook {
    private final JavaPlugin plugin;

    public EconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Looked up every time on purpose, so it still works if the economy plugin loads late or reloads.
    private Economy provider() {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public boolean available() {
        return provider() != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        Economy economy = provider();
        return economy != null && economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy economy = provider();
        if (economy == null) return false;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Withdraw from " + player.getName() + " failed: " + response.errorMessage);
        }
        return response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        Economy economy = provider();
        if (economy == null) return false;
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Deposit to " + player.getName() + " failed: " + response.errorMessage);
        }
        return response.transactionSuccess();
    }

    public String format(double amount) {
        Economy economy = provider();
        return economy == null ? String.format(Locale.ROOT, "%.2f", amount) : economy.format(amount);
    }
}