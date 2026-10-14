package dev.glidelaunch;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persists grace expiry timestamps (epoch ms) in grace-data.yml. Value 0 = "seen, no grace". */
public final class GraceStorage {
    private final GlideLaunchPlugin plugin;
    private final File file;
    private final Map<UUID, Long> data = new HashMap<>();
    private boolean dirty;

    public GraceStorage(GlideLaunchPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "grace-data.yml");
    }

    public void load() {
        data.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("grace");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try { data.put(UUID.fromString(k), s.getLong(k)); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("Ignoring invalid UUID in grace-data.yml: " + k); }
        }
    }

    public Long get(UUID id) { return data.get(id); }

    public void put(UUID id, long expiry) { data.put(id, expiry); dirty = true; }

    public Map<UUID, Long> snapshot() { return new HashMap<>(data); }

    /** Serialises on the calling (main) thread; optionally writes off-thread. */
    public void flush(boolean async) {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : data.entrySet()) y.set("grace." + e.getKey(), e.getValue());
        String text = y.saveToString();
        if (async && plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(text));
        else write(text);
    }

    private synchronized void write(String text) {
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "grace-data.yml.tmp");
            Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save grace-data.yml: " + ex.getMessage());
        }
    }
}
