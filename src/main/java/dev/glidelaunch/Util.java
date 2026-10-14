package dev.glidelaunch;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class Util {
    private Util() {}

    static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    static String duration(long ms) {
        long total = Math.max(0, ms) / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        if (h > 0) return h + "h " + String.format("%02dm", m);
        if (m > 0) return m + "m " + String.format("%02ds", s);
        return s + "s";
    }

    @SuppressWarnings("deprecation")
    static boolean grounded(Player p) {
        if (p.isOnGround()) return true;
        Block below = p.getLocation().clone().subtract(0, 0.08, 0).getBlock();
        return !below.isPassable();
    }
}
