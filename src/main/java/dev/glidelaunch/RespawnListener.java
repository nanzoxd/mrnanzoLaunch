package dev.glidelaunch;

import dev.glidelaunch.GlideManager.StopReason;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.lang.reflect.Method;

/** First-join and post-death launches. Valid bed / anchor respawns are never overridden. */
public final class RespawnListener implements Listener {
    private final GlideLaunchPlugin plugin;

    public RespawnListener(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean first = !p.hasPlayedBefore();
        plugin.grace().onJoin(p, first);
        JoinIntro.play(plugin, p);
        if (first && plugin.getConfig().getBoolean("new-player.enabled", true) && plugin.spawn().canAutoGlide(p)) {
            plugin.spawn().scheduleAutoLaunch(p, 10L, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        plugin.glide().stop(p, StopReason.DEATH);
        plugin.landing().cancel(p);
        plugin.grace().onDeath(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) return;
        Player p = e.getPlayer();
        FileConfiguration c = plugin.getConfig();
        plugin.grace().onRespawn(p);
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;           // valid saved spawn: vanilla behaviour
        if (!c.getBoolean("post-death.enabled", true) || !plugin.spawn().canAutoGlide(p)) return;
        if (hasSavedRespawn(p)
                && "VANILLA".equalsIgnoreCase(c.getString("post-death.unusable-saved-respawn", "GLIDE"))) return;
        plugin.spawn().scheduleAutoLaunch(p, 3L);
    }

    /** Reflection keeps this compiling regardless of which accessor the API version exposes. */
    private boolean hasSavedRespawn(Player p) {
        for (String name : new String[]{"getRespawnLocation", "getBedSpawnLocation"}) {
            try {
                Method m = Player.class.getMethod(name);
                return m.invoke(p) instanceof Location;
            } catch (ReflectiveOperationException ignored) { }
        }
        return false;
    }
}
