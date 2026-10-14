package dev.glidelaunch;

import dev.glidelaunch.ConfigManager.GlideParams;
import dev.glidelaunch.ConfigManager.ParticleParams;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns glide sessions and the single shared tick task. Main thread only. */
public final class GlideManager {
    public enum StopReason {
        DOUBLE_SNEAK, COLLISION, LIMIT, ADMIN, TELEPORT, GROUND, WATER, VEHICLE,
        QUIT, WORLD_CHANGE, DEATH, DISABLE
    }

    private final GlideLaunchPlugin plugin;
    private final Map<UUID, GlideSession> sessions = new HashMap<>();
    private final Map<UUID, Location> pendingReturn = new HashMap<>();
    private final List<UUID> tickBuffer = new ArrayList<>();
    private BukkitTask task;

    public GlideManager(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    public boolean isGliding(UUID id) { return sessions.containsKey(id); }
    GlideSession get(UUID id) { return sessions.get(id); }
    public Collection<UUID> activeIds() { return new ArrayList<>(sessions.keySet()); }
    public int activeCount() { return sessions.size(); }

    public boolean start(Player p, boolean test, Location returnTo) {
        UUID id = p.getUniqueId();
        if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || sessions.containsKey(id)) {
            return false;
        }
        plugin.landing().cancel(p);
        GlideParams g = plugin.configs().glide();
        boolean ground = Util.grounded(p);
        GlideSession s = new GlideSession(id, test, ground);
        s.speed = g.cruise();
        s.velocity = p.getEyeLocation().getDirection().multiply(g.cruise());
        if (ground && s.velocity.getY() < 0.2) s.velocity.setY(0.2);
        sessions.put(id, s);
        p.setGliding(true);
        p.setVelocity(s.velocity.clone()); // initial push only; vanilla elytra physics take over
        if (test && returnTo != null) pendingReturn.put(id, returnTo); else pendingReturn.remove(id);
        p.setFallDistance(0f);
        plugin.playSound(p, "glide-start");
        plugin.messages().send(p, "glide-start");
        ensureTask();
        return true;
    }

    public void stop(Player p, StopReason reason) {
        UUID id = p.getUniqueId();
        GlideSession s = sessions.remove(id);
        if (s == null) return;
        if (sessions.isEmpty()) cancelTask();
        if (p.isGliding()) p.setGliding(false);
        plugin.landing().onGlideEnded(p, s, reason);
        switch (reason) {
            case QUIT, WORLD_CHANGE, DEATH, DISABLE -> pendingReturn.remove(id);
            default -> { }
        }
        if (!plugin.landing().isLanding(id)) onLandingFinished(p);
    }

    /** Called when a glide (and any landing phase) is completely over. */
    void onLandingFinished(Player p) {
        UUID id = p.getUniqueId();
        Location back = pendingReturn.remove(id);
        if (back == null || !p.isOnline()) return;
        if (!plugin.getConfig().getBoolean("test.return-after-landing", true)) return;
        int secs = plugin.getConfig().getInt("test.return-delay-seconds", 3);
        plugin.messages().send(p, "test-returning", Placeholder.unparsed("seconds", String.valueOf(secs)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline() && !isGliding(id)) {
                pl.teleportAsync(back);
                plugin.messages().send(pl, "test-returned");
            }
        }, secs * 20L);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stop(p, StopReason.DISABLE); else sessions.remove(id);
        }
        pendingReturn.clear();
        cancelTask();
    }

    private void ensureTask() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void cancelTask() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        GlideParams g = plugin.configs().glide();
        ParticleParams pp = plugin.configs().particles();
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        boolean bar = cfg.getBoolean("glide.actionbar.enabled", true)
                && cfg.getBoolean("landing.double-sneak.enabled", true);
        String barText = cfg.getString("glide.actionbar.text", "<#A7C7E7>Double <#FFB6C1>Shift <#A7C7E7>To Land");
        net.kyori.adventure.text.Component barComp = bar ? Messages.parse(barText) : null;
        tickBuffer.clear();
        tickBuffer.addAll(sessions.keySet());
        for (UUID id : tickBuffer) {
            GlideSession s = sessions.get(id);
            if (s == null) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                sessions.remove(id);
                pendingReturn.remove(id);
                continue;
            }
            StopReason r = plugin.movement().tick(p, s, g);
            if (r != null) { stop(p, r); continue; }
            plugin.particles().emit(p, s, pp);
            if (bar && (s.ticks - 1) % 20 == 0) p.sendActionBar(barComp);
        }
        if (sessions.isEmpty()) cancelTask();
    }
}
