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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persists grace expiry timestamps (epoch ms) in grace-data.yml. Value 0 = "seen, no grace". */
public final class GraceStorage {
    private final GlideLaunchPlugin plugin;
    private final File file;
    private final Map<UUID, Long> data = new HashMap<>();
    private final java.util.Set<UUID> pendingFirst = new java.util.HashSet<>();
    private boolean dirty;

    public GraceStorage(GlideLaunchPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "grace-data.yml");
    }

    public void load() {
        data.clear();
        pendingFirst.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getStringList("pending-first")) {
            try { pendingFirst.add(UUID.fromString(k)); } catch (IllegalArgumentException ignored) { }
        }
        ConfigurationSection s = y.getConfigurationSection("grace");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try { data.put(UUID.fromString(k), s.getLong(k)); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("Ignoring invalid UUID in grace-data.yml: " + k); }
        }
    }

    /** Players who joined for the first time but have not been launched yet (e.g. still at the AuthMe login). */
    public boolean isPending(UUID id) { return pendingFirst.contains(id); }
    public void addPending(UUID id) { if (pendingFirst.add(id)) dirty = true; }
    public void removePending(UUID id) { if (pendingFirst.remove(id)) dirty = true; }

    public Long get(UUID id) { return data.get(id); }

    public void put(UUID id, long expiry) { data.put(id, expiry); dirty = true; }

    public Map<UUID, Long> snapshot() { return new HashMap<>(data); }

    /** Serialises on the calling (main) thread; optionally writes off-thread. */
    public void flush(boolean async) {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : data.entrySet()) y.set("grace." + e.getKey(), e.getValue());
        List<String> pend = new ArrayList<>();
        for (UUID u : pendingFirst) pend.add(u.toString());
        y.set("pending-first", pend);
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
