package io.github.direkjames.lodestock.paper.permission;

import io.github.direkjames.lodestock.core.market.MarketItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every item in items.yml automatically gets a permission, lodestock.ore.&lt;item&gt;, that everyone
 * has by default. Server owners take it away from a group or player in their permissions plugin.
 * lodestock.ore.* covers every item.
 */
public final class OrePermissions {
    public static final String PREFIX = "lodestock.ore.";

    private final Set<String> registered = new HashSet<>();

    public static String node(String itemId) {
        return PREFIX + itemId;
    }

    public static boolean can(Player player, String itemId) {
        return player.hasPermission(node(itemId));
    }

    /** Registers the permissions for the current item list and removes the ones of items that are gone. */
    public void sync(Collection<MarketItem> items) {
        clear();
        PluginManager pm = Bukkit.getPluginManager();
        Map<String, Boolean> children = new LinkedHashMap<>();
        for (MarketItem item : items) {
            String name = node(item.id());
            pm.removePermission(name);
            pm.addPermission(new Permission(name, "Trade " + item.id() + " in the Lodestock market", PermissionDefault.TRUE));
            registered.add(name);
            children.put(name, true);
        }
        String all = PREFIX + "*";
        pm.removePermission(all);
        pm.addPermission(new Permission(all, "Trade every item in the Lodestock market", PermissionDefault.TRUE, children));
        registered.add(all);
    }

    public void clear() {
        PluginManager pm = Bukkit.getPluginManager();
        for (String name : registered) pm.removePermission(name);
        registered.clear();
    }
}
