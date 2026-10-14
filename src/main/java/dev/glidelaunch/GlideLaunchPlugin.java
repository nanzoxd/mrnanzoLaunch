package dev.glidelaunch;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GlideLaunchPlugin extends JavaPlugin {
    public static final String ADMIN = "glidelaunch.admin";

    private ConfigManager configs;
    private Messages messages;
    private SpawnManager spawn;
    private GlideManager glide;
    private GlideMovementController movement;
    private GlideParticleManager particles;
    private LandingManager landing;
    private GraceStorage graceStorage;
    private GraceManager grace;
    private GUIManager gui;
    private AuthGate auth;

    @Override
    public void onEnable() {
        configs = new ConfigManager(this);
        messages = new Messages(this);
        particles = new GlideParticleManager(this);
        movement = new GlideMovementController();
        glide = new GlideManager(this);
        landing = new LandingManager(this);
        spawn = new SpawnManager(this);
        graceStorage = new GraceStorage(this);
        graceStorage.load();
        grace = new GraceManager(this, graceStorage);
        gui = new GUIManager(this);
        auth = new AuthGate(this);

        Bukkit.getPluginManager().registerEvents(landing, this);
        Bukkit.getPluginManager().registerEvents(new RespawnListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SessionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GraceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);

        CommandManager cm = new CommandManager(this);
        for (String name : new String[]{"glidelaunch", "setspawn", "glidetest", "launch"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) { pc.setExecutor(cm); pc.setTabCompleter(cm); }
        }

        grace.start();
        long interval = getConfig().getInt("performance.autosave-interval-seconds", 30) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> graceStorage.flush(true), interval, interval);
        grace.syncOnline();
    }

    @Override
    public void onDisable() {
        if (auth != null) auth.shutdown();
        if (glide != null) glide.shutdown();
        if (landing != null) landing.shutdown();
        if (grace != null) grace.shutdown();
        if (graceStorage != null) graceStorage.flush(false);
        if (configs != null) configs.saveNow();
    }

    /** Run on the main thread, ignoring the call if the plugin is already disabled. */
    public void sync(Runnable r) {
        if (isEnabled()) Bukkit.getScheduler().runTask(this, r);
    }

    /** Plays a configured sound (sounds.<key>.*) at the player's location. */
    public void playSound(org.bukkit.entity.Player p, String key) { playSound(p, key, 1f); }

    public void playSound(org.bukkit.entity.Player p, String key, float pitchMul) {
        org.bukkit.configuration.file.FileConfiguration c = getConfig();
        if (!c.getBoolean("sounds.enabled", true)) return;
        String s = c.getString("sounds." + key + ".sound", "");
        if (s == null || s.isBlank()) return;
        p.getWorld().playSound(p.getLocation(), s,
                (float) c.getDouble("sounds." + key + ".volume", 1.0),
                (float) c.getDouble("sounds." + key + ".pitch", 1.0) * pitchMul);
    }

    public void reloadAll() {
        configs.reload();
        grace.syncOnline();
    }

    public ConfigManager configs() { return configs; }
    public Messages messages() { return messages; }
    public SpawnManager spawn() { return spawn; }
    public GlideManager glide() { return glide; }
    public GlideMovementController movement() { return movement; }
    public GlideParticleManager particles() { return particles; }
    public LandingManager landing() { return landing; }
    public GraceManager grace() { return grace; }
    public GUIManager gui() { return gui; }
    public AuthGate auth() { return auth; }
    public GraceStorage storage() { return graceStorage; }
}
