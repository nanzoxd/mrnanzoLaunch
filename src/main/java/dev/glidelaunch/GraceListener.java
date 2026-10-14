package dev.glidelaunch;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Mutual PvP protection while grace is active. */
public final class GraceListener implements Listener {
    private final GlideLaunchPlugin plugin;

    public GraceListener(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    private static Player attackerOf(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim && blocked(attackerOf(e.getDamager()), victim)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent e) {
        if (e.getEntity() instanceof Player victim && blocked(attackerOf(e.getCombuster()), victim)) e.setCancelled(true);
    }

    private boolean blocked(Player attacker, Player victim) {
        if (attacker == null || attacker.equals(victim)) return false;
        GraceManager g = plugin.grace();
        if (!g.enabled()) return false;
        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("grace.pvp.bypass-permission-enabled", false)
                && attacker.hasPermission("glidelaunch.bypass.grace")) return false;
        if (c.getBoolean("grace.pvp.block-damage-to-protected", true) && g.isProtected(victim.getUniqueId())) {
            plugin.messages().actionbar(attacker, "pvp-victim-protected", Placeholder.unparsed("player", victim.getName()));
            return true;
        }
        if (c.getBoolean("grace.pvp.block-attacks-by-protected", true) && g.isProtected(attacker.getUniqueId())) {
            plugin.messages().actionbar(attacker, "pvp-attacker-protected");
            return true;
        }
        return false;
    }
}
