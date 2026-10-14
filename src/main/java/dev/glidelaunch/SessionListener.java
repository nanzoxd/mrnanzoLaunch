package dev.glidelaunch;

import dev.glidelaunch.GlideManager.StopReason;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Cleans up sessions on quit, world change, foreign teleports and spectator mode. */
public final class SessionListener implements Listener {
    private final GlideLaunchPlugin plugin;

    public SessionListener(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.glide().stop(p, StopReason.QUIT);
        plugin.landing().cleanup(p);
        plugin.grace().onQuit(p);
        plugin.spawn().onQuit(p);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        plugin.glide().stop(e.getPlayer(), StopReason.WORLD_CHANGE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN && plugin.glide().isGliding(p.getUniqueId())) {
            plugin.glide().stop(p, StopReason.TELEPORT);
        }
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() == GameMode.SPECTATOR) plugin.glide().stop(e.getPlayer(), StopReason.ADMIN);
    }
}
