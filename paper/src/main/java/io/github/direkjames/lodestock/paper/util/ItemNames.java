package io.github.direkjames.lodestock.paper.util;

public final class ItemNames {
    private ItemNames() {}

    /** "minecraft:iron_ingot" becomes "Iron ingot". */
    public static String pretty(String id) {
        String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return name.isEmpty() ? id : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}