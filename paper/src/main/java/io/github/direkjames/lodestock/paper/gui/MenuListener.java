package io.github.direkjames.lodestock.paper.gui;

import io.github.direkjames.lodestock.paper.LodestockPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuListener implements Listener {
    private static final long TRADE_COOLDOWN_MS = 250;

    private final LodestockPlugin plugin;
    private final Map<UUID, Long> lastTrade = new HashMap<>();

    public MenuListener(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MarketMenu menu)) return; // not our menu

        event.setCancelled(true); // nothing can be moved in or out, whatever the click type
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != top) return; // clicks in the player's own inventory do nothing

        MarketMenu.Button button = menu.button(event.getSlot());
        if (button == null) return;

        switch (button.kind()) {
            case CATEGORY -> later(() -> plugin.menus().openCategory(player, button.target()));
            case BACK -> later(() -> plugin.menus().openMain(player));
            case CLOSE -> later(player::closeInventory);
            case PREV -> {
                menu.setPage(menu.page() - 1);
                plugin.menus().refresh(menu);
            }
            case NEXT -> {
                menu.setPage(menu.page() + 1);
                plugin.menus().refresh(menu);
            }
            case ITEM -> trade(player, menu, button.target(), event.getClick());
        }
    }

    private void trade(Player player, MarketMenu menu, String itemId, ClickType click) {
        long now = System.currentTimeMillis();
        Long before = lastTrade.get(player.getUniqueId());
        if (before != null && now - before < TRADE_COOLDOWN_MS) return;

        int bulk = Math.max(2, Math.min(64, plugin.getConfig().getInt("gui.bulk-amount", 16)));
        switch (click) {
            case LEFT, SHIFT_LEFT -> {
                if (!player.hasPermission("lodestock.buy")) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                lastTrade.put(player.getUniqueId(), now);
                plugin.trades().buy(player, itemId, click == ClickType.LEFT ? 1 : bulk);
            }
            case RIGHT, SHIFT_RIGHT -> {
                if (!player.hasPermission("lodestock.sell")) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                lastTrade.put(player.getUniqueId(), now);
                plugin.trades().sell(player, itemId, click == ClickType.RIGHT ? 1 : -1);
            }
            default -> {
                return;
            }
        }
        plugin.menus().refresh(menu); // show the new prices and stock
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastTrade.remove(event.getPlayer().getUniqueId());
    }

    // Opening or closing an inventory from inside a click event is safer one tick later.
    private void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}