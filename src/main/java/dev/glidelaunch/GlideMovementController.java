package dev.glidelaunch;

import dev.glidelaunch.ConfigManager.GlideParams;
import dev.glidelaunch.GlideManager.StopReason;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Native elytra gliding (same approach as the Glide plugin): the player is kept in the real
 * gliding pose with setGliding(true) so the client runs vanilla elytra physics and renders the
 * glide animation. We never force a landing: a glide only ends on touchdown, water, a vehicle,
 * a double-sneak, or an admin/teleport stop.
 */
public final class GlideMovementController {
    private static final int GROUND_GRACE_TICKS = 10;

    /** @return a stop reason if the glide must end this tick, otherwise null. */
    StopReason tick(Player p, GlideSession s, GlideParams g) {
        s.ticks++;
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return StopReason.DEATH;
        if (p.isInsideVehicle()) return StopReason.VEHICLE;

        Location loc = p.getLocation();
        if (s.ticks > GROUND_GRACE_TICKS) {
            if (Util.grounded(p)) return StopReason.GROUND;
            if (p.isInWater() || p.isInLava()) return StopReason.WATER;
        }
        // Real movement vector, used for the particle trail direction.
        if (s.lastLoc != null && s.lastLoc.getWorld() == loc.getWorld()) {
            s.velocity = loc.toVector().subtract(s.lastLoc.toVector());
        }
        s.lastLoc = loc;
        // Optional admin-set limit (0 = unlimited, the default).
        if (g.limitTicks() > 0 && s.ticks >= g.limitTicks()) return StopReason.LIMIT;

        // Keep the elytra pose (and sound) on; the server clears it for players without an elytra.
        if (!p.isGliding()) p.setGliding(true);
        p.setFallDistance(0f);
        return null;
    }
}
