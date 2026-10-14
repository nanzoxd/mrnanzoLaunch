package dev.glidelaunch;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Spawn centre, safe launch location search and the launch (teleport + glide) flow. */
public final class SpawnManager {
    private static final Set<Material> UNSAFE = EnumSet.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE, Material.WITHER_ROSE, Material.LAVA,
            Material.WATER, Material.BUBBLE_COLUMN, Material.COBWEB);

    private final GlideLaunchPlugin plugin;
    private final Set<UUID> pending = new HashSet<>();
    private final Set<UUID> scheduled = new HashSet<>();
    private final Map<UUID, Long> lastAuto = new HashMap<>();

    public SpawnManager(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    // ---------- centre ----------
    public World world() {
        String n = plugin.getConfig().getString("spawn.world", "");
        World w = (n == null || n.isBlank()) ? null : Bukkit.getWorld(n);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    /** @return {x, z} of the spawn centre. */
    public double[] centre(World w) {
        if (plugin.getConfig().getBoolean("spawn.configured", false)) {
            return new double[]{plugin.getConfig().getDouble("spawn.x"), plugin.getConfig().getDouble("spawn.z")};
        }
        Location s = w.getSpawnLocation();
        return new double[]{s.getBlockX() + 0.5, s.getBlockZ() + 0.5};
    }

    public void setCentre(Location l) {
        ConfigManager c = plugin.configs();
        c.set("spawn.world", l.getWorld().getName());
        c.set("spawn.x", Math.round(l.getX() * 10) / 10.0);
        c.set("spawn.y", Math.round(l.getY() * 10) / 10.0);
        c.set("spawn.z", Math.round(l.getZ() * 10) / 10.0);
        c.set("spawn.configured", true);
    }

    public boolean canAutoGlide(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        return !plugin.getConfig().getBoolean("glide.require-use-permission", false)
                || p.hasPermission("glidelaunch.use");
    }

    // ---------- launch flow ----------
    public void scheduleAutoLaunch(Player p, long delayTicks) { scheduleAutoLaunch(p, delayTicks, false); }

    public void scheduleAutoLaunch(Player p, long delayTicks, boolean firstJoin) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastAuto.get(id);
        if (pending.contains(id) || scheduled.contains(id) || (last != null && now - last < 5000)
                || plugin.glide().isGliding(id)) return;
        scheduled.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduled.remove(id);
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline() || pl.isDead()) return;
            lastAuto.put(id, System.currentTimeMillis());
            launch(pl, false, null, firstJoin);
        }, delayTicks);
    }

    public void onQuit(Player p) {
        lastAuto.remove(p.getUniqueId());
        pending.remove(p.getUniqueId());
    }

    public void launch(Player p, boolean test, CommandSender initiator) { launch(p, test, initiator, false); }

    public void launch(Player p, boolean test, CommandSender initiator, boolean firstJoin) {
        UUID id = p.getUniqueId();
        if (!p.isOnline() || p.getGameMode() == GameMode.SPECTATOR) { fail(p, initiator); return; }
        if (plugin.glide().isGliding(id) || pending.contains(id)) {
            if (initiator != null) plugin.messages().send(initiator, "already-gliding",
                    Placeholder.unparsed("player", p.getName()));
            return;
        }
        pending.add(id);
        Location origin = p.getLocation().clone();
        findLaunchLocation().whenComplete((loc, err) -> plugin.sync(() -> {
            if (err != null || loc == null || !p.isOnline()) { pending.remove(id); fail(p, initiator); return; }
            if (p.isInsideVehicle()) p.leaveVehicle();
            loc.setYaw(p.getLocation().getYaw());
            loc.setPitch(12f);
            p.teleportAsync(loc, TeleportCause.PLUGIN).whenComplete((ok, e2) -> plugin.sync(() -> {
                pending.remove(id);
                if (e2 != null || ok == null || !ok || !p.isOnline()) { fail(p, initiator); return; }
                if (plugin.glide().start(p, test, test ? origin : null)) {
                    plugin.messages().send(p, "launch-started");
                    if (firstJoin) { plugin.playSound(p, "first-join"); plugin.playSound(p, "first-join-beacon"); }
                    if (initiator != null && initiator != p) plugin.messages().send(initiator, "launch-admin-ok",
                            Placeholder.unparsed("player", p.getName()));
                }
            }));
        }));
    }

    private void fail(Player p, CommandSender initiator) {
        if (p.isOnline()) plugin.messages().send(p, "launch-failed");
        if (initiator != null && initiator != p) plugin.messages().send(initiator, "launch-failed");
    }

    // ---------- safe location search ----------
    private CompletableFuture<Location> findLaunchLocation() {
        CompletableFuture<Location> result = new CompletableFuture<>();
        World w = world();
        if (w.getEnvironment() != World.Environment.NORMAL) { result.complete(null); return result; }
        attempt(w, 0, result);
        return result;
    }

    private void attempt(World w, int n, CompletableFuture<Location> result) {
        int max = plugin.getConfig().getInt("safe-search.max-attempts", 12);
        if (n >= max) { fallback(w, result); return; }
        double[] c = centre(w);
        double min = plugin.getConfig().getInt("spawn.min-radius");
        double mx = plugin.getConfig().getInt("spawn.max-radius");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double dist = Math.sqrt(r.nextDouble() * (mx * mx - min * min) + min * min);
        double ang = r.nextDouble(0, Math.PI * 2);
        int x = (int) Math.floor(c[0] + Math.cos(ang) * dist);
        int z = (int) Math.floor(c[1] + Math.sin(ang) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) -> plugin.sync(() -> {
            Location l = (err == null && chunk != null) ? evaluate(w, x, z) : null;
            if (l != null) result.complete(l); else attempt(w, n + 1, result);
        }));
    }

    private void fallback(World w, CompletableFuture<Location> result) {
        if ("NO_LAUNCH".equalsIgnoreCase(plugin.getConfig().getString("safe-search.fallback"))) {
            result.complete(null);
            return;
        }
        double[] c = centre(w);
        int x = (int) Math.floor(c[0]), z = (int) Math.floor(c[1]);
        w.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) -> plugin.sync(() -> {
            if (err != null || chunk == null) { result.complete(null); return; }
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            int h = plugin.getConfig().getInt("safe-search.launch-height", 110);
            result.complete(new Location(w, x + 0.5, Math.min(w.getMaxHeight() - 4, y + h), z + 0.5));
        }));
    }

    /** Must run on the main thread with the chunk loaded. */
    private Location evaluate(World w, int x, int z) {
        if (!w.getWorldBorder().isInside(new Location(w, x + 0.5, 64, z + 0.5))) return null;
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (y <= w.getMinHeight() + 1) return null;
        Block b = w.getBlockAt(x, y, z);
        Material t = b.getType();
        if (b.isLiquid() || UNSAFE.contains(t) || Tag.LEAVES.isTagged(t) || !t.isSolid()) return null;
        int h = plugin.getConfig().getInt("safe-search.launch-height", 110);
        int ly = Math.min(w.getMaxHeight() - 4, y + h);
        if (ly - y < 20) return null;
        return new Location(w, x + 0.5, ly, z + 0.5);
    }
}
