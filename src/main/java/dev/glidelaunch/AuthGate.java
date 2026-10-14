package dev.glidelaunch;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optional AuthMe support (no compile-time dependency; reflection only).
 * While a player has not logged in / registered, join work (launch, titles, grace) is held back.
 */
final class AuthGate {
    private final GlideLaunchPlugin plugin;
    private final Map<UUID, Runnable> waiting = new HashMap<>();
    private final Map<UUID, Long> deadline = new HashMap<>();
    private BukkitTask task;
    private Object api;
    private Method isAuthenticated;
    private boolean broken;

    AuthGate(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    boolean active() {
        if (!plugin.getConfig().getBoolean("authme.wait-for-login", true) || broken) return false;
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe == null || !authMe.isEnabled()) return false;
        if (api != null) return true;
        try {
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, authMe.getClass().getClassLoader());
            api = c.getMethod("getInstance").invoke(null);
            isAuthenticated = c.getMethod("isAuthenticated", Player.class);
            plugin.getLogger().info("AuthMe detected: new-player launch waits for login.");
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            broken = true;
            plugin.getLogger().warning("AuthMe found but its API could not be used; not waiting for login.");
            return false;
        }
    }

    private boolean authed(Player p) {
        try { return (Boolean) isAuthenticated.invoke(api, p); }
        catch (ReflectiveOperationException | RuntimeException ex) { broken = true; return true; }
    }

    /** Runs {@code action} once the player is authenticated (plus a short delay), or right away without AuthMe. */
    void await(Player p, Runnable action) {
        if (!active()) { action.run(); return; }
        UUID id = p.getUniqueId();
        waiting.put(id, action);
        deadline.put(id, System.currentTimeMillis() + plugin.getConfig().getInt("authme.timeout-seconds", 600) * 1000L);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    void remove(UUID id) { waiting.remove(id); deadline.remove(id); }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(waiting.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || now > deadline.getOrDefault(id, 0L)) { remove(id); continue; }
            if (authed(p)) {
                Runnable r = waiting.remove(id);
                deadline.remove(id);
                long delay = plugin.getConfig().getInt("authme.delay-ticks-after-login", 20);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null && pl.isOnline() && r != null) r.run();
                }, delay);
            }
        }
        if (waiting.isEmpty() && task != null) { task.cancel(); task = null; }
    }

    void shutdown() {
        waiting.clear();
        deadline.clear();
        if (task != null) { task.cancel(); task = null; }
    }
}
