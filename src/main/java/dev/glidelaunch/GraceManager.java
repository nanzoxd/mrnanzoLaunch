package dev.glidelaunch;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Grace timers, potion effects and the "is protected" query used by PvP rules. */
public final class GraceManager {
    private static final class Applied {
        final PotionEffect prior; final long at; final int amp;
        Applied(PotionEffect prior, long at, int amp) { this.prior = prior; this.at = at; this.amp = amp; }
    }

    private record Spec(PotionEffectType type, String key) {}

    private static final List<Spec> SPECS = List.of(
            new Spec(PotionEffectType.SATURATION, "saturation"),
            new Spec(PotionEffectType.RESISTANCE, "resistance"));

    private final GlideLaunchPlugin plugin;
    private final GraceStorage storage;
    private final Map<UUID, Map<PotionEffectType, Applied>> applied = new HashMap<>();
    private final Set<UUID> tracked = new HashSet<>();

    public GraceManager(GlideLaunchPlugin plugin, GraceStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); }

    public boolean enabled() { return plugin.getConfig().getBoolean("grace.enabled", false); }

    public long remaining(UUID id) {
        Long e = storage.get(id);
        return e == null ? 0 : Math.max(0, e - System.currentTimeMillis());
    }

    public boolean isProtected(UUID id) { return enabled() && remaining(id) > 0; }

    public long defaultDurationMs() {
        return plugin.getConfig().getInt("grace.duration-minutes", 30) * 60_000L;
    }

    /** Active entries sorted by remaining time (longest first). */
    public Map<UUID, Long> activeRemaining() {
        long now = System.currentTimeMillis();
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : storage.snapshot().entrySet()) {
            if (e.getValue() > now) list.add(Map.entry(e.getKey(), e.getValue() - now));
        }
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> e : list) out.put(e.getKey(), e.getValue());
        return out;
    }

    // ---------- grant / revoke ----------
    public void grant(UUID id) { grant(id, defaultDurationMs()); }

    public void grant(UUID id, long durationMs) {
        storage.put(id, System.currentTimeMillis() + durationMs);
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline() && enabled()) {
            tracked.add(id);
            applyEffects(p);
            plugin.messages().send(p, "grace-granted", Placeholder.unparsed("time", Util.duration(durationMs)));
        }
    }

    public boolean revoke(UUID id) {
        boolean was = remaining(id) > 0;
        storage.put(id, 0L);
        tracked.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            clearEffects(p);
            if (was) plugin.messages().send(p, "grace-removed");
        }
        return was;
    }

    public int revokeAll() {
        int n = 0;
        for (UUID id : new ArrayList<>(activeRemaining().keySet())) { revoke(id); n++; }
        return n;
    }

    // ---------- events ----------
    public void onJoin(Player p, boolean first) {
        if (!enabled()) return;
        UUID id = p.getUniqueId();
        Long exp = storage.get(id);
        if (exp == null) {
            String mode = plugin.getConfig().getString("grace.grant-to", "NEW_PLAYERS");
            boolean grant = first ? (mode.equals("NEW_PLAYERS") || mode.equals("BOTH"))
                                  : (mode.equals("RETURNING_PLAYERS") || mode.equals("BOTH"));
            if (grant) grant(id); else storage.put(id, 0L);
            return;
        }
        if (exp > System.currentTimeMillis()) {
            tracked.add(id);
            applyEffects(p);
        }
    }

    public void onDeath(Player p) { applied.remove(p.getUniqueId()); }

    public void onRespawn(Player p) {
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !enabled()) return;
            if (remaining(id) > 0) { tracked.add(id); applyEffects(pl); }
            else if (plugin.getConfig().getBoolean("grace.grant-after-death", false)) grant(id);
        }, 2L);
    }

    public void onQuit(Player p) {
        clearEffects(p);
        tracked.remove(p.getUniqueId());
    }

    /** Re-evaluate all online players (after enabling/disabling/reload). */
    public void syncOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (isProtected(id)) { tracked.add(id); applyEffects(p); }
            else if (tracked.remove(id)) clearEffects(p);
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(tracked)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clearEffects(p);
        }
        tracked.clear();
    }

    // ---------- ticking ----------
    private void tick() {
        if (tracked.isEmpty()) return;
        boolean on = enabled();
        boolean display = plugin.getConfig().getBoolean("grace.display-remaining", true);
        for (UUID id : new ArrayList<>(tracked)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) { tracked.remove(id); applied.remove(id); continue; }
            if (!on) { clearEffects(p); tracked.remove(id); continue; }
            long rem = remaining(id);
            if (rem <= 0) {
                clearEffects(p);
                tracked.remove(id);
                plugin.messages().send(p, "grace-ended");
                continue;
            }
            applyEffects(p);
            if (display) plugin.messages().actionbar(p, "grace-actionbar",
                    Placeholder.unparsed("time", Util.duration(rem)));
        }
    }

    // ---------- potion effects ----------
    private void applyEffects(Player p) {
        UUID id = p.getUniqueId();
        long remMs = remaining(id);
        if (remMs <= 0) return;
        FileConfiguration c = plugin.getConfig();
        Map<PotionEffectType, Applied> map = applied.computeIfAbsent(id, k -> new HashMap<>());
        for (Spec sp : SPECS) {
            String base = "grace.effects." + sp.key() + ".";
            PotionEffectType type = sp.type();
            if (!c.getBoolean(base + "enabled", true)) { restore(p, map, type); continue; }
            int amp = Math.max(0, c.getInt(base + "level", 1) - 1);
            int secs = c.getInt(base + "duration-seconds", 0);
            long ticks = remMs / 50;
            if (secs > 0) ticks = Math.min(ticks, secs * 20L);
            int dur = (int) Math.max(20, Math.min(ticks, Integer.MAX_VALUE - 1));
            Applied a = map.get(type);
            PotionEffect cur = p.getPotionEffect(type);
            if (a != null && a.amp == amp && cur != null && cur.getAmplifier() == amp && cur.getDuration() > 30) continue;
            if (a == null) {
                if (cur != null && (cur.getAmplifier() > amp
                        || (cur.getAmplifier() == amp && (cur.getDuration() == -1 || cur.getDuration() >= dur)))) {
                    continue; // player's own effect is at least as good; never touch it
                }
                a = new Applied(cur, System.currentTimeMillis(), amp);
            } else if (a.amp != amp) {
                a = new Applied(a.prior, a.at, amp);
            }
            map.put(type, a);
            p.addPotionEffect(new PotionEffect(type, dur, amp, false, true, true));
        }
    }

    private void restore(Player p, Map<PotionEffectType, Applied> map, PotionEffectType type) {
        Applied a = map.remove(type);
        if (a == null) return;
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null) {
            if (cur.getAmplifier() != a.amp) return; // replaced by something else; leave it
            p.removePotionEffect(type);
        }
        PotionEffect prior = a.prior;
        if (prior == null) return;
        int d = prior.getDuration();
        if (d == -1) {
            p.addPotionEffect(prior);
        } else {
            long left = d - (System.currentTimeMillis() - a.at) / 50;
            if (left > 0) p.addPotionEffect(new PotionEffect(type, (int) left, prior.getAmplifier(),
                    prior.isAmbient(), prior.hasParticles(), prior.hasIcon()));
        }
    }

    private void clearEffects(Player p) {
        Map<PotionEffectType, Applied> m = applied.remove(p.getUniqueId());
        if (m == null) return;
        for (PotionEffectType t : new ArrayList<>(m.keySet())) restore(p, m, t);
    }
}
