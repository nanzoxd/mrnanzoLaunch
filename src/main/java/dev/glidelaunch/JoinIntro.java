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

    /** "Successfully landed" title, shown once the player has touched down after a glide. */
    static void landed(GlideLaunchPlugin plugin, Player p) {
        if (!plugin.getConfig().getBoolean("landing.landed-title.enabled", true)) return;
        render(plugin, p, "landing.landed-title.", "landing.landed-title.stay-ticks");
    }

    private static void show(GlideLaunchPlugin plugin, UUID id, String key, String sound) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;
        if (render(plugin, p, "join-intro." + key + ".", "join-intro.stay-ticks")) plugin.playSound(p, sound);
    }

    private static boolean render(GlideLaunchPlugin plugin, Player p, String base, String stayPath) {
        FileConfiguration c = plugin.getConfig();
        String text = c.getString(base + "text", "");
        if (text == null || text.isBlank()) return false;
        text = text.replace("\uFE0E", "").replace("\uFE0F", ""); // variation selectors render as boxes
        if (c.getBoolean("join-intro.small-caps", true)) text = Hud.smallCaps(text);
        List<String> colors = c.getStringList(base + "colors");
        if (colors.isEmpty()) colors = List.of("#FFFFFF");
        Hud.bigTitle(p, Hud.gradient(text, colors), c.getInt(stayPath, 50));
        return true;
    }
}
