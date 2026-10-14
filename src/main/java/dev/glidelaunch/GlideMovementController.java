package dev.glidelaunch;

import dev.glidelaunch.ConfigManager.GlideParams;
import dev.glidelaunch.GlideManager.StopReason;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Velocity-based elytra-style physics. The player is never teleported; each tick a target
 * velocity is derived from the camera direction, speed (energy) and sink, and the real
 * velocity eases toward it. Terrain collisions are handled by vanilla; we detect them by
 * comparing expected and actual movement.
 */
public final class GlideMovementController {
    private static final int GROUND_GRACE_TICKS = 10;
    private static final int STUCK_TICKS = 4;

    /** @return a stop reason if the glide must end this tick, otherwise null. */
    StopReason tick(Player p, GlideSession s, GlideParams g) {
        s.ticks++;
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return StopReason.DEATH;
        if (p.isInsideVehicle()) return StopReason.VEHICLE;

        Location loc = p.getLocation();
        if (s.ticks > GROUND_GRACE_TICKS) {
            if (Util.grounded(p)) return StopReason.GROUND;
            if (p.isInWater() || p.isInLava()) return StopReason.WATER;
            if (s.lastLoc != null && s.lastLoc.getWorld() == loc.getWorld()) {
                double expected = s.velocity.length();
                double moved = s.lastLoc.distance(loc);
                if (expected > 0.3 && moved < expected * 0.3) {
                    if (++s.stuck >= STUCK_TICKS) return StopReason.COLLISION;
                } else {
                    s.stuck = 0;
                }
            }
        }
        s.lastLoc = loc;
        if (g.limitTicks() > 0 && s.ticks >= g.limitTicks()) return StopReason.LIMIT;

        Vector dir = p.getEyeLocation().getDirection();
        double lookY = dir.getY();
        double cruise = g.cruise();
        Input in = p.getCurrentInput();

        // Energy exchange: dive to gain speed, climb to lose it.
        double speed = s.speed;
        if (lookY < 0) speed += -lookY * g.gravity() * 3.0;
        else speed -= lookY * g.gravity() * 4.0;
        speed += (cruise - speed) * 0.02;
        if (in.isForward()) speed += 0.01;
        if (in.isBackward()) speed -= 0.03;
        speed = Util.clamp(speed, cruise * 0.35, g.max());
        s.speed = speed;

        // Lift fades below stall speed, so endless climbing is impossible.
        double stall = cruise * 0.5;
        double lift = Util.clamp((speed - stall) / Math.max(0.01, cruise - stall), 0, 1);
        double sink = g.gravity() * (3.0 - 2.0 * lift);

        double tx = dir.getX() * speed;
        double tz = dir.getZ() * speed;
        double ty = lookY * speed - sink;

        // Strafe with A/D.
        int lat = (in.isRight() ? 1 : 0) - (in.isLeft() ? 1 : 0);
        if (lat != 0) {
            double len = Math.hypot(dir.getX(), dir.getZ());
            if (len > 1e-4) {
                tx += (-dir.getZ() / len) * lat * speed * 0.35;
                tz += (dir.getX() / len) * lat * speed * 0.35;
            }
        }
        if (s.groundStart && g.boostTicks() > 0 && s.ticks <= g.boostTicks()) {
            ty = Math.max(ty, 0.85 * (1.0 - (double) s.ticks / g.boostTicks()));
        }
        ty = Util.clamp(ty, -g.vertical(), g.vertical());
        double hl = Math.hypot(tx, tz);
        if (hl > g.max()) { tx *= g.max() / hl; tz *= g.max() / hl; }

        double k = g.steering();
        Vector v = s.velocity;
        v.setX(v.getX() + (tx - v.getX()) * k);
        v.setY(v.getY() + (ty - v.getY()) * k);
        v.setZ(v.getZ() + (tz - v.getZ()) * k);
        p.setVelocity(v.clone());
        p.setFallDistance(0f);
        return null;
    }
}
