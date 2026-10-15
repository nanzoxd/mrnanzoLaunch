package dev.glidelaunch;

import dev.glidelaunch.GlideManager.StopReason;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Double-sneak detection, landing modes and safe Slow Falling handling. */
public final class LandingManager implements Listener {
    private static final class State {
        final boolean owns;
        final long start;
        final long until;
        boolean countdown;
        int shown;
        long lastFall;
        State(boolean owns, long start, long until) { this.owns = owns; this.start = start; this.until = until; }
    }

    private final GlideLaunchPlugin plugin;
    private final Map<UUID, State> landing = new HashMap<>();
    private final Map<UUID, Long> protectUntil = new HashMap<>();
    private BukkitTask task;

    public LandingManager(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    public boolean isLanding(UUID id) { return landing.containsKey(id); }

    // ---------- double sneak ----------
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        GlideSession s = plugin.glide().get(p.getUniqueId());
        if (s == null || !plugin.getConfig().getBoolean("landing.double-sneak.enabled", true)) return;
        if (s.ticks < 10) return;
        long now = System.currentTimeMillis();
        long window = plugin.getConfig().getInt("landing.double-sneak.window-ms", 400);
        if (s.lastSneak > 0 && now - s.lastSneak <= window) {
            s.lastSneak = 0;
            plugin.playSound(p, "double-sneak");
            plugin.glide().stop(p, StopReason.DOUBLE_SNEAK);
        } else {
            s.lastSneak = now;
        }
    }

    /** Keeps the elytra pose alive mid-air (the server would otherwise clear it: no elytra worn). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.isGliding()) return;
        GlideSession s = plugin.glide().get(p.getUniqueId());
        if (s == null) return;
        if (p.isInWater() || p.isInLava() || p.isInsideVehicle() || p.isDead()) return;
        if (s.ticks > 10 && Util.grounded(p)) return; // touched down: let it end naturally
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL
                && e.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;
        UUID id = p.getUniqueId();
        if (plugin.glide().isGliding(id)) { e.setCancelled(true); return; }
        Long until = protectUntil.get(id);
        if (until != null && until > System.currentTimeMillis()) e.setCancelled(true);
    }

    // ---------- glide end handling ----------
    void onGlideEnded(Player p, GlideSession s, StopReason r) {
        long protect = plugin.getConfig().getInt("landing.fall-protection-ms", 3000);
        switch (r) {
            case DOUBLE_SNEAK, COLLISION, LIMIT, ADMIN, TELEPORT -> {
                boolean slow = "SLOW_FALL".equalsIgnoreCase(plugin.getConfig().getString("landing.mode", "SLOW_FALL"));
                // The landing timer is only shown after the player double-sneaks.
                boolean countdown = r == StopReason.DOUBLE_SNEAK
                        && plugin.getConfig().getBoolean("landing.countdown.enabled", true);
                begin(p, slow, slow ? protect : 0, countdown);
            }
            case GROUND, WATER, VEHICLE -> begin(p, false, protect, false);
            case QUIT, DISABLE -> safeLogout(p);
            default -> { }
        }
    }

    private int slowTicks() {
        return plugin.getConfig().getInt("landing.slow-falling.duration-seconds", 20) * 20;
    }

    private void begin(Player p, boolean applySlow, long protectMs, boolean countdown) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        boolean owns = false;
        if (applySlow && p.getPotionEffect(PotionEffectType.SLOW_FALLING) == null) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, slowTicks(), 0, false, false, true));
            owns = true;
        }
        State ns = new State(owns, now, now + 300_000L);
        ns.countdown = countdown;
        ns.lastFall = now;
        landing.put(id, ns);
        if (protectMs > 0) protectUntil.merge(id, now + protectMs, Math::max);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private void safeLogout(Player p) {
        if (!Util.grounded(p) && p.getPotionEffect(PotionEffectType.SLOW_FALLING) == null) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, slowTicks(), 0, false, false, true));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean refresh = plugin.getConfig().getBoolean("landing.slow-falling.refresh-until-landed", true);
        for (UUID id : new ArrayList<>(landing.keySet())) {
            State st = landing.get(id);
            Player p = Bukkit.getPlayer(id);
            if (st == null) continue;
            if (p == null || !p.isOnline()) { landing.remove(id); continue; }
            boolean down = Util.grounded(p) || p.isInWater() || p.isInLava() || p.isInsideVehicle() || p.isDead();
            if (now - st.start > 250 && (down || now > st.until)) { finish(p, st); continue; }
            if (!down) {
                if (st.countdown) {
                    int secs = plugin.getConfig().getInt("landing.countdown.seconds", 3);
                    if (st.shown < secs && now - st.start >= st.shown * 1000L) {
                        Hud.countdown(p, secs - st.shown, secs);
                        plugin.playSound(p, "countdown", 1f + 0.25f * st.shown);
                        st.shown++;
                    }
                }
                long iv = plugin.getConfig().getInt("sounds.falling.interval-ticks", 20) * 50L;
                if (now - st.lastFall >= iv) {
                    plugin.playSound(p, "falling");
                    st.lastFall = now;
                }
            }
            if (st.owns && refresh) {
                PotionEffect cur = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
                if (cur == null || (cur.getDuration() >= 0 && cur.getDuration() < 60)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, slowTicks(), 0, false, false, true));
                }
            }
        }
        long t = System.currentTimeMillis();
        protectUntil.values().removeIf(v -> v < t);
        if (landing.isEmpty()) { task.cancel(); task = null; }
    }

    /** Stops the looping falling/elytra sound so it cannot keep playing after touchdown. */
    private void stopFalling(Player p) {
        String s = plugin.getConfig().getString("sounds.falling.sound");
        if (s != null && !s.isBlank()) p.stopSound(s);
        p.stopSound("item.elytra.flying");
    }

    private void finish(Player p, State st) {
        UUID id = p.getUniqueId();
        landing.remove(id);
        stopFalling(p);
        if (st.owns) removeOwned(p);
        if (st.countdown) p.clearTitle();
        if (!p.isDead()) {
            plugin.playSound(p, "landing");
            JoinIntro.landed(plugin, p);
        }
        protectUntil.merge(id, System.currentTimeMillis() + 2000, Math::max);
        plugin.glide().onLandingFinished(p);
    }

    private void removeOwned(Player p) {
        PotionEffect cur = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
        if (cur != null && cur.getAmplifier() == 0) p.removePotionEffect(PotionEffectType.SLOW_FALLING);
    }

    /** Abort landing tracking (new glide, death). Removes our own Slow Falling only. */
    public void cancel(Player p) {
        State st = landing.remove(p.getUniqueId());
        if (st != null) stopFalling(p);
        if (st != null && st.owns) removeOwned(p);
        protectUntil.remove(p.getUniqueId());
    }

    /** Quit: drop state; keep our effect if still airborne so they rejoin safely. */
    public void cleanup(Player p) {
        State st = landing.remove(p.getUniqueId());
        protectUntil.remove(p.getUniqueId());
        if (st != null && st.owns && Util.grounded(p)) removeOwned(p);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(landing.keySet())) {
            Player p = Bukkit.getPlayer(id);
            State st = landing.remove(id);
            if (p != null && st != null && st.owns && Util.grounded(p)) removeOwned(p);
        }
        protectUntil.clear();
        if (task != null) { task.cancel(); task = null; }
    }
}
