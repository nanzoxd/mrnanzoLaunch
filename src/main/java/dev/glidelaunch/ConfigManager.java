package dev.glidelaunch;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;

/** Loads, validates, caches and saves config.yml. Main thread only. */
public final class ConfigManager {
    public static final List<String> PARTICLES = List.of("FIREWORK", "FLAME", "END_ROD", "CLOUD", "SMOKE",
            "CRIT", "ELECTRIC_SPARK", "SOUL_FIRE_FLAME", "HAPPY_VILLAGER", "GLOW", "SNOWFLAKE", "WAX_ON");
    public static final List<String> LANDING_MODES = List.of("SLOW_FALL", "NORMAL_LANDING");
    public static final List<String> GRANT_MODES = List.of("NEW_PLAYERS", "RETURNING_PLAYERS", "BOTH");
    public static final List<String> FALLBACKS = List.of("CENTER_AIR", "NO_LAUNCH");
    public static final List<String> UNUSABLE = List.of("GLIDE", "VANILLA");

    public record GlideParams(double cruise, double max, double vertical, double gravity, double steering,
                              int limitTicks, int boostTicks) {}

    public record ParticleParams(boolean enabled, String type, int interval, int count, double back) {}

    private final GlideLaunchPlugin plugin;
    private volatile GlideParams glide;
    private volatile ParticleParams particles;
    private BukkitTask saveTask;
    private boolean dirty;
    private boolean changed;

    public ConfigManager(GlideLaunchPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public GlideParams glide() { return glide; }
    public ParticleParams particles() { return particles; }
    private FileConfiguration c() { return plugin.getConfig(); }

    public void reload() {
        plugin.reloadConfig();
        validate();
        refresh();
    }

    public void set(String path, Object value) {
        c().set(path, value);
        validate();
        refresh();
        markDirty();
    }

    public void markDirty() {
        dirty = true;
        if (saveTask == null && plugin.isEnabled()) {
            saveTask = Bukkit.getScheduler().runTaskLater(plugin, this::saveNow, 40L);
        }
    }

    public void saveNow() {
        if (saveTask != null) { saveTask.cancel(); saveTask = null; }
        if (!dirty) return;
        dirty = false;
        plugin.saveConfig();
    }

    private void refresh() {
        FileConfiguration c = c();
        glide = new GlideParams(
                c.getDouble("glide.horizontal-speed"), c.getDouble("glide.max-speed"),
                c.getDouble("glide.vertical-speed"), c.getDouble("glide.gravity"),
                c.getDouble("glide.steering"),
                c.getInt("glide.duration-limit-seconds") * 20,
                c.getInt("glide.launch-boost-ticks"));
        int cap = c.getInt("performance.max-particles-per-emit");
        particles = new ParticleParams(c.getBoolean("particles.enabled"),
                c.getString("particles.type", "FIREWORK"),
                c.getInt("particles.interval-ticks"),
                Math.min(c.getInt("particles.count"), cap),
                c.getDouble("particles.back-offset"));
    }

    private void validate() {
        changed = false;
        clampI("spawn.max-radius", 50, 100000);
        clampI("spawn.min-radius", 0, 100000);
        int max = c().getInt("spawn.max-radius");
        if (c().getInt("spawn.min-radius") >= max) {
            c().set("spawn.min-radius", Math.max(0, max - 10));
            changed = true;
        }
        clampI("safe-search.max-attempts", 1, 50);
        clampI("safe-search.launch-height", 20, 300);
        enumS("safe-search.fallback", FALLBACKS);
        enumS("post-death.unusable-saved-respawn", UNUSABLE);

        clampD("glide.horizontal-speed", 0.2, 3.0);
        clampD("glide.max-speed", 0.5, 4.0);
        if (c().getDouble("glide.max-speed") < c().getDouble("glide.horizontal-speed")) {
            c().set("glide.max-speed", c().getDouble("glide.horizontal-speed"));
            changed = true;
        }
        clampD("glide.vertical-speed", 0.1, 2.5);
        clampD("glide.gravity", 0.0, 0.2);
        clampD("glide.steering", 0.01, 1.0);
        clampI("glide.duration-limit-seconds", 0, 3600);
        clampI("glide.launch-boost-ticks", 0, 100);

        enumS("particles.type", PARTICLES);
        clampI("particles.interval-ticks", 1, 20);
        clampI("particles.count", 1, 50);
        clampD("particles.back-offset", 0.5, 4.0);
        clampI("performance.max-particles-per-emit", 1, 50);
        clampI("performance.autosave-interval-seconds", 5, 600);

        clampI("landing.double-sneak.window-ms", 100, 2000);
        enumS("landing.mode", LANDING_MODES);
        clampI("landing.slow-falling.duration-seconds", 1, 120);
        clampI("landing.fall-protection-ms", 0, 15000);
        clampI("landing.countdown.seconds", 1, 10);
        clampI("join-intro.delay-ticks", 0, 600);
        clampI("join-intro.gap-ticks", 20, 400);
        clampI("join-intro.stay-ticks", 10, 200);
        clampI("sounds.falling.interval-ticks", 5, 100);
        clampI("test.return-delay-seconds", 0, 60);

        clampI("grace.duration-minutes", 1, 10080);
        enumS("grace.grant-to", GRANT_MODES);
        clampI("grace.effects.saturation.level", 1, 10);
        clampI("grace.effects.saturation.duration-seconds", 0, 86400);
        clampI("grace.effects.resistance.level", 1, 5);
        clampI("grace.effects.resistance.duration-seconds", 0, 86400);

        if (changed) markDirty();
    }

    private void clampD(String path, double min, double max) {
        double v = c().getDouble(path);
        double n = (Double.isNaN(v) || Double.isInfinite(v)) ? c().getDefaults().getDouble(path)
                : Math.max(min, Math.min(max, v));
        if (n != v) { c().set(path, n); changed = true; }
    }

    private void clampI(String path, int min, int max) {
        int v = c().getInt(path);
        int n = Math.max(min, Math.min(max, v));
        if (n != v || !c().isInt(path)) { c().set(path, n); changed = true; }
    }

    private void enumS(String path, List<String> options) {
        String v = c().getString(path, "");
        String up = v.toUpperCase(Locale.ROOT);
        if (!options.contains(up)) {
            up = c().getDefaults().getString(path, options.get(0));
        }
        if (!up.equals(v)) { c().set(path, up); changed = true; }
    }
}
