package dev.glidelaunch;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Two-step title sequence shown after every join (small caps, gradient, sounds). */
final class JoinIntro {
    private JoinIntro() {}

    static void play(GlideLaunchPlugin plugin, Player p) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("join-intro.enabled", true)) return;
        long delay = c.getInt("join-intro.delay-ticks", 20);
        long gap = c.getInt("join-intro.gap-ticks", 70);
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> show(plugin, id, "title", "intro-title"), delay);
        Bukkit.getScheduler().runTaskLater(plugin, () -> show(plugin, id, "started", "intro-started"), delay + gap);
    }

    private static void show(GlideLaunchPlugin plugin, UUID id, String key, String sound) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;
        FileConfiguration c = plugin.getConfig();
        String base = "join-intro." + key + ".";
        String text = c.getString(base + "text", "");
        if (text == null || text.isBlank()) return;
        text = text.replace("\uFE0E", "").replace("\uFE0F", ""); // variation selectors render as boxes
        if (c.getBoolean("join-intro.small-caps", true)) text = Hud.smallCaps(text);
        List<String> colors = c.getStringList(base + "colors");
        if (colors.isEmpty()) colors = List.of("#FFFFFF");
        Hud.bigTitle(p, Hud.gradient(text, colors), c.getInt("join-intro.stay-ticks", 50));
        plugin.playSound(p, sound);
    }
}
