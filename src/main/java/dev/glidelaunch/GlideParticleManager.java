package dev.glidelaunch;

import dev.glidelaunch.ConfigManager.ParticleParams;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Lightweight directional particle trail (no entities). */
public final class GlideParticleManager {
    private final GlideLaunchPlugin plugin;
    private String cachedName;
    private Particle cached;

    public GlideParticleManager(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    private Particle resolve(String name) {
        if (name.equals(cachedName)) return cached;
        Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if (p == null) p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft("firework"));
        cachedName = name;
        cached = p;
        return p;
    }

    void emit(Player p, GlideSession s, ParticleParams pp) {
        if (!pp.enabled() || s.ticks % pp.interval() != 0) return;
        Vector d = s.velocity.clone();
        if (d.lengthSquared() < 1e-4) d = p.getEyeLocation().getDirection();
        d.normalize();
        Location base = p.getLocation().add(0, 0.9, 0).subtract(d.clone().multiply(pp.back()));
        burst(base, d, pp);
    }

    /** Spawns particles at base that fly opposite to the movement direction. */
    private void burst(Location base, Vector moveDir, ParticleParams pp) {
        Particle particle = resolve(pp.type());
        World w = base.getWorld();
        if (particle == null || w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < pp.count(); i++) {
            double ox = -moveDir.getX() + r.nextDouble(-0.15, 0.15);
            double oy = -moveDir.getY() + r.nextDouble(-0.15, 0.15);
            double oz = -moveDir.getZ() + r.nextDouble(-0.15, 0.15);
            // count = 0 -> offsets are treated as direction, extra as speed.
            w.spawnParticle(particle, base, 0, ox, oy, oz, 0.12);
        }
    }

    /** Short preview in front of the admin, trail pointing back at them. */
    public void preview(Player admin) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!admin.isOnline() || t > 30) { cancel(); return; }
                ParticleParams pp = plugin.configs().particles();
                Vector d = admin.getEyeLocation().getDirection().normalize();
                Location base = admin.getEyeLocation().add(d.clone().multiply(2.0 + t * 0.25)).subtract(0, 0.4, 0);
                burst(base, d, pp);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
