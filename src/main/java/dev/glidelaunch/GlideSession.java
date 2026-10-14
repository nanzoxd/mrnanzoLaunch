package dev.glidelaunch;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

/** Mutable per-player glide state. Main thread only. */
final class GlideSession {
    final UUID id;
    final boolean test;
    final boolean groundStart;
    int ticks;
    int stuck;
    double speed;
    Vector velocity = new Vector();
    Location lastLoc;
    long lastSneak;

    GlideSession(UUID id, boolean test, boolean groundStart) {
        this.id = id;
        this.test = test;
        this.groundStart = groundStart;
    }
}
