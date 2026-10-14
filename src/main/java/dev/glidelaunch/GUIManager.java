package dev.glidelaunch;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** All admin menus. Each screen is a filler function that can be re-run in place (no cursor reset). */
public final class GUIManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    @FunctionalInterface
    public interface Action { void run(Player p, ClickType click); }

    public static final class GuiHolder implements InventoryHolder {
        final Map<Integer, Action> actions = new HashMap<>();
        Inventory inventory;
        Runnable refresh = () -> { };
        int page;

        @Override public Inventory getInventory() { return inventory; }
        void reset() { inventory.clear(); actions.clear(); }
    }

    private enum Kind { BOOL, INT, DOUBLE, ENUM }

    private record Setting(String path, Material icon, String name, String desc, Kind kind,
                           double min, double max, double step, List<String> options, String unit) {}

    private record Entry(ItemStack item, Action action) {}

    private static final class Slots {
        int i;
        int next() { return SLOTS[i++]; }
    }

    private final GlideLaunchPlugin plugin;

    public GUIManager(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    // ---------- setting factories ----------
    private static Setting b(String path, Material m, String name, String desc) {
        return new Setting(path, m, name, desc, Kind.BOOL, 0, 0, 0, List.of(), "");
    }
    private static Setting n(String path, Material m, String name, String desc, double min, double max, double step, String unit) {
        return new Setting(path, m, name, desc, Kind.INT, min, max, step, List.of(), unit);
    }
    private static Setting d(String path, Material m, String name, String desc, double min, double max, double step, String unit) {
        return new Setting(path, m, name, desc, Kind.DOUBLE, min, max, step, List.of(), unit);
    }
    private static Setting e(String path, Material m, String name, String desc, List<String> options) {
        return new Setting(path, m, name, desc, Kind.ENUM, 0, 0, 0, options, "");
    }

    // ---------- infrastructure ----------
    public void click(Player p, GuiHolder h, int slot, ClickType t) {
        if (!p.hasPermission(GlideLaunchPlugin.ADMIN)) { p.closeInventory(); return; }
        Action a = h.actions.get(slot);
        if (a != null) a.run(p, t);
    }

    private void open(Player p, int size, String title, BiConsumer<Player, GuiHolder> filler) {
        if (!p.hasPermission(GlideLaunchPlugin.ADMIN)) { plugin.messages().send(p, "no-permission"); return; }
        GuiHolder h = new GuiHolder();
        h.inventory = Bukkit.createInventory(h, size, MM.deserialize(title));
        h.refresh = () -> { h.reset(); filler.accept(p, h); };
        h.refresh.run();
        p.openInventory(h.inventory);
    }

    private void decorate(ItemMeta m, String name, String... lore) {
        m.displayName(MM.deserialize("<!italic>" + name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(MM.deserialize("<!italic>" + s));
            m.lore(l);
        }
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        decorate(m, name, lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack head(OfflinePlayer op, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(op);
        decorate(m, name, lore);
        it.setItemMeta(m);
        return it;
    }

    private void put(GuiHolder h, int slot, ItemStack it, Action a) {
        h.inventory.setItem(slot, it);
        if (a != null) h.actions.put(slot, a);
    }

    private void back(GuiHolder h, Consumer<Player> target) {
        put(h, h.inventory.getSize() - 5, item(Material.ARROW, "<gray>« Back"), (p, c) -> target.accept(p));
    }

    private String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "");
    }

    // ---------- generic settings ----------
    private String valueText(Setting s) {
        FileConfiguration c = plugin.getConfig();
        return switch (s.kind()) {
            case BOOL -> c.getBoolean(s.path()) ? "<#2EFF2E>ENABLED" : "<#FF0000>DISABLED";
            case INT -> "<#FAFA33>" + c.getInt(s.path()) + s.unit();
            case DOUBLE -> "<#FAFA33>" + fmt(c.getDouble(s.path())) + s.unit();
            case ENUM -> "<#FAFA33>" + c.getString(s.path());
        };
    }

    private void place(GuiHolder h, Slots s, Setting st) {
        int slot = s.next();
        String hint1;
        String hint2 = "";
        switch (st.kind()) {
            case BOOL -> hint1 = "<#00CCFF>Click: <white>toggle";
            case ENUM -> hint1 = "<#00CCFF>Left/Right click: <white>next / previous";
            default -> { hint1 = "<#00CCFF>Left/Right click: <white>+ / - " + fmt(st.step()); hint2 = "<#00CCFF>Shift-click: <white>x10"; }
        }
        ItemStack it = hint2.isEmpty()
                ? item(st.icon(), "<#00CCFF>" + st.name(), "<gray>" + st.desc(), "", "<gray>Current: " + valueText(st), hint1)
                : item(st.icon(), "<#00CCFF>" + st.name(), "<gray>" + st.desc(), "", "<gray>Current: " + valueText(st), hint1, hint2);
        put(h, slot, it, (p, click) -> { applySetting(st, click); h.refresh.run(); });
    }

    private void applySetting(Setting s, ClickType click) {
        ConfigManager cm = plugin.configs();
        FileConfiguration c = plugin.getConfig();
        boolean left = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
        boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        if (!left && !right) return;
        int dir = left ? 1 : -1;
        double mult = click.isShiftClick() ? 10 : 1;
        switch (s.kind()) {
            case BOOL -> cm.set(s.path(), !c.getBoolean(s.path()));
            case INT -> {
                double v = c.getInt(s.path()) + dir * s.step() * mult;
                cm.set(s.path(), (int) Math.round(Util.clamp(v, s.min(), s.max())));
            }
            case DOUBLE -> {
                double v = Util.clamp(c.getDouble(s.path()) + dir * s.step() * mult, s.min(), s.max());
                cm.set(s.path(), Math.round(v * 1000.0) / 1000.0);
            }
            case ENUM -> {
                int idx = Math.max(0, s.options().indexOf(c.getString(s.path())));
                cm.set(s.path(), s.options().get((idx + dir + s.options().size()) % s.options().size()));
            }
        }
        if (s.path().startsWith("grace.")) plugin.grace().syncOnline();
    }

    private void confirm(Player p, String title, String detail, Consumer<Player> yes, Consumer<Player> no) {
        open(p, 27, "<dark_red>Confirm Action", (v, h) -> {
            put(h, 13, item(Material.PAPER, "<#FAFA33>" + title, "<gray>" + detail), null);
            put(h, 11, item(Material.LIME_CONCRETE, "<#2EFF2E>Confirm", "<gray>Click to proceed"), (pl, c) -> yes.accept(pl));
            put(h, 15, item(Material.RED_CONCRETE, "<#FF0000>Cancel", "<gray>Go back without changes"), (pl, c) -> no.accept(pl));
        });
    }

    private void fillPaged(GuiHolder h, List<Entry> entries, Consumer<Player> backTarget) {
        int per = 45;
        int pages = Math.max(1, (entries.size() + per - 1) / per);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        for (int i = 0; i < per; i++) {
            int idx = h.page * per + i;
            if (idx >= entries.size()) break;
            put(h, i, entries.get(idx).item(), entries.get(idx).action());
        }
        if (h.page > 0) put(h, 45, item(Material.ARROW, "<#FAFA33>Previous page", "<gray>Page " + h.page + "/" + pages),
                (p, c) -> { h.page--; h.refresh.run(); });
        if (h.page < pages - 1) put(h, 53, item(Material.ARROW, "<#FAFA33>Next page", "<gray>Page " + (h.page + 2) + "/" + pages),
                (p, c) -> { h.page++; h.refresh.run(); });
        back(h, backTarget);
    }

    private String graceText(UUID id) {
        long r = plugin.grace().remaining(id);
        return r > 0 ? "<#2EFF2E>" + Util.duration(r) : "<gray>none";
    }

    private String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    // ---------- MAIN ----------
    public void openMain(Player p) { open(p, 36, "<dark_gray>GlideLaunch <#00CCFF>Admin", this::fillMain); }

    private void fillMain(Player v, GuiHolder h) {
        put(h, 10, item(Material.COMPASS, "<#00CCFF>Spawn Settings", "<gray>Centre, radii, safe search."), (p, c) -> openSpawn(p));
        put(h, 11, item(Material.ELYTRA, "<#00CCFF>Glide Settings", "<gray>Speed, gravity, steering."), (p, c) -> openGlide(p));
        put(h, 12, item(Material.FIREWORK_ROCKET, "<#00CCFF>Particle Settings", "<gray>Trail type and density."), (p, c) -> openParticles(p));
        put(h, 13, item(Material.FEATHER, "<#00CCFF>Landing Settings", "<gray>Double-sneak and Slow Falling."), (p, c) -> openLanding(p));
        put(h, 14, item(Material.SHIELD, "<#00CCFF>Grace Settings", "<gray>Spawn protection (default: off).",
                "<gray>Status: " + (plugin.grace().enabled() ? "<#2EFF2E>ENABLED" : "<#FF0000>DISABLED")), (p, c) -> openGrace(p));
        put(h, 15, item(Material.PLAYER_HEAD, "<#00CCFF>Player Glide Management", "<gray>Glide / grace per player."), (p, c) -> openPlayers(p));
        put(h, 16, item(Material.BOOK, "<#00CCFF>Plugin Information", "<gray>Version, spawn and status."), (p, c) -> openInfo(p));
        put(h, 31, item(Material.REDSTONE, "<#FF0000>Reload Configuration", "<gray>Re-read config.yml from disk."),
                (p, c) -> confirm(p, "Reload configuration?", "Unsaved in-memory edits are replaced by config.yml.",
                        pl -> { plugin.configs().saveNow(); plugin.reloadAll(); plugin.messages().send(pl, "config-reloaded"); openMain(pl); },
                        this::openMain));
    }

    // ---------- SPAWN ----------
    public void openSpawn(Player p) { open(p, 45, "<dark_gray>Spawn Settings", this::fillSpawn); }

    private void fillSpawn(Player v, GuiHolder h) {
        Slots s = new Slots();
        FileConfiguration c = plugin.getConfig();
        String where = c.getBoolean("spawn.configured")
                ? c.getString("spawn.world") + " " + fmt(c.getDouble("spawn.x")) + ", " + fmt(c.getDouble("spawn.y")) + ", " + fmt(c.getDouble("spawn.z"))
                : "world spawn (not configured)";
        put(h, s.next(), item(Material.LODESTONE, "<#00CCFF>Set Spawn Centre", "<gray>Current: <#FAFA33>" + where, "",
                        "<#00CCFF>Click: <white>use your current location"),
                (p, cl) -> confirm(p, "Set spawn centre here?", "Overwrites the current spawn centre.", pl -> {
                    plugin.spawn().setCentre(pl.getLocation());
                    Location l = pl.getLocation();
                    plugin.messages().send(pl, "spawn-set", Placeholder.unparsed("world", l.getWorld().getName()),
                            Placeholder.unparsed("x", String.valueOf(l.getBlockX())),
                            Placeholder.unparsed("y", String.valueOf(l.getBlockY())),
                            Placeholder.unparsed("z", String.valueOf(l.getBlockZ())));
                    openSpawn(pl);
                }, this::openSpawn));
        place(h, s, n("spawn.min-radius", Material.MAP, "Minimum Launch Radius", "Landing zones stay at least this far from the centre.", 0, 100000, 50, " blocks"));
        place(h, s, n("spawn.max-radius", Material.FILLED_MAP, "Maximum Launch Radius", "Landing zones are within this horizontal distance.", 50, 100000, 100, " blocks"));
        place(h, s, b("new-player.enabled", Material.NETHER_STAR, "Glide for New Players", "Launch players on their first join."));
        place(h, s, b("post-death.enabled", Material.SKELETON_SKULL, "Glide After Death", "Launch respawns without a valid bed/anchor."));
        place(h, s, e("post-death.unusable-saved-respawn", Material.RED_BED, "Unusable Saved Respawn", "GLIDE: launch anyway. VANILLA: leave alone.", ConfigManager.UNUSABLE));
        place(h, s, n("safe-search.max-attempts", Material.SPYGLASS, "Safe Search Attempts", "Candidates tried before the fallback.", 1, 50, 1, ""));
        place(h, s, n("safe-search.launch-height", Material.LADDER, "Launch Height", "Blocks above the ground the glide starts.", 20, 300, 10, " blocks"));
        place(h, s, b("authme.wait-for-login", Material.IRON_DOOR, "Wait For AuthMe Login", "New-player launch starts after login/register."));
        place(h, s, e("safe-search.fallback", Material.BARRIER, "Search Fallback", "If nothing safe is found.", ConfigManager.FALLBACKS));
        back(h, this::openMain);
    }

    // ---------- GLIDE ----------
    public void openGlide(Player p) { open(p, 45, "<dark_gray>Glide Settings", this::fillGlide); }

    private void fillGlide(Player v, GuiHolder h) {
        Slots s = new Slots();
        place(h, s, d("glide.horizontal-speed", Material.SUGAR, "Horizontal Speed", "Cruise speed in blocks/tick.", 0.2, 3.0, 0.05, ""));
        place(h, s, d("glide.max-speed", Material.BLAZE_POWDER, "Max Speed", "Speed cap when diving.", 0.5, 4.0, 0.1, ""));
        place(h, s, d("glide.vertical-speed", Material.FEATHER, "Vertical Movement", "Max up/down speed in blocks/tick.", 0.1, 2.5, 0.05, ""));
        place(h, s, d("glide.gravity", Material.ANVIL, "Gravity Strength", "Higher = steeper sink.", 0.0, 0.2, 0.005, ""));
        place(h, s, d("glide.steering", Material.LEAD, "Steering Sensitivity", "How fast velocity follows the camera.", 0.01, 1.0, 0.01, ""));
        place(h, s, n("glide.duration-limit-seconds", Material.CLOCK, "Glide Duration Limit", "0 = unlimited.", 0, 3600, 10, " s"));
        place(h, s, n("glide.launch-boost-ticks", Material.FIREWORK_ROCKET, "Launch Boost", "Upward boost when starting from ground.", 0, 100, 5, " ticks"));
        place(h, s, b("new-player.enabled", Material.NETHER_STAR, "Auto Glide: New Players", "Same setting as in Spawn Settings."));
        place(h, s, b("post-death.enabled", Material.SKELETON_SKULL, "Auto Glide: After Death", "Same setting as in Spawn Settings."));
        place(h, s, b("glide.require-use-permission", Material.NAME_TAG, "Require glidelaunch.use", "Auto glide only for players with the permission."));
        place(h, s, b("glide.actionbar.enabled", Material.OAK_SIGN, "Glide Action Bar", "Double Shift To Land hint (pink + blue)."));
        place(h, s, b("join-intro.enabled", Material.NAME_TAG, "Join Title Intro", "Shows the two-step title after every join."));
        place(h, s, b("sounds.enabled", Material.NOTE_BLOCK, "Sound Effects", "First join, glide start, double-sneak and landing sounds."));
        back(h, this::openMain);
    }

    // ---------- PARTICLES ----------
    public void openParticles(Player p) { open(p, 36, "<dark_gray>Particle Settings", this::fillParticles); }

    private void fillParticles(Player v, GuiHolder h) {
        Slots s = new Slots();
        place(h, s, b("particles.enabled", Material.FIREWORK_STAR, "Glide Particles", "Show the boost trail."));
        place(h, s, e("particles.type", Material.GLOWSTONE_DUST, "Particle Type", "Visual style of the trail.", ConfigManager.PARTICLES));
        place(h, s, n("particles.interval-ticks", Material.REPEATER, "Frequency", "Emit every N ticks (lower = more).", 1, 20, 1, " ticks"));
        place(h, s, n("particles.count", Material.GUNPOWDER, "Quantity", "Particles per emission (capped by performance setting).", 1, 50, 1, ""));
        place(h, s, d("particles.back-offset", Material.STICK, "Back Offset", "Distance behind the player.", 0.5, 4.0, 0.1, " blocks"));
        put(h, s.next(), item(Material.ENDER_EYE, "<#2EFF2E>Preview Effect", "<gray>Plays the trail in front of you.", "", "<#00CCFF>Click: <white>preview"),
                (p, c) -> plugin.particles().preview(p));
        back(h, this::openMain);
    }

    // ---------- LANDING ----------
    public void openLanding(Player p) { open(p, 36, "<dark_gray>Landing Settings", this::fillLanding); }

    private void fillLanding(Player v, GuiHolder h) {
        Slots s = new Slots();
        place(h, s, b("landing.double-sneak.enabled", Material.LEATHER_BOOTS, "Double-Sneak Landing", "Sneak twice quickly to end the glide."));
        place(h, s, n("landing.double-sneak.window-ms", Material.CLOCK, "Detection Window", "Max time between sneaks.", 100, 2000, 50, " ms"));
        place(h, s, e("landing.mode", Material.PHANTOM_MEMBRANE, "Landing Mode", "SLOW_FALL or NORMAL_LANDING.", ConfigManager.LANDING_MODES));
        place(h, s, n("landing.slow-falling.duration-seconds", Material.FEATHER, "Slow Falling Duration", "Seconds per application.", 1, 120, 1, " s"));
        place(h, s, n("landing.fall-protection-ms", Material.TURTLE_HELMET, "Fall Protection", "Fall-damage immunity after landing.", 0, 15000, 500, " ms"));
        place(h, s, b("landing.countdown.enabled", Material.CLOCK, "Landing Countdown", "3-2-1 title while falling after a glide."));
        place(h, s, n("landing.countdown.seconds", Material.CLOCK, "Countdown Length", "Seconds counted down.", 1, 10, 1, " s"));
        place(h, s, b("test.return-after-landing", Material.ENDER_PEARL, "Return Testers", "/launch test returns the tester afterwards."));
        back(h, this::openMain);
    }

    // ---------- GRACE ----------
    public void openGrace(Player p) { open(p, 45, "<dark_gray>Grace Settings", this::fillGrace); }

    private void fillGrace(Player v, GuiHolder h) {
        Slots s = new Slots();
        place(h, s, b("grace.enabled", Material.SHIELD, "Grace Mode", "Master switch (default: off)."));
        place(h, s, n("grace.duration-minutes", Material.CLOCK, "Grace Duration", "Length of a grace period.", 1, 10080, 5, " min"));
        place(h, s, e("grace.grant-to", Material.NAME_TAG, "Who Receives Grace", "NEW_PLAYERS, RETURNING_PLAYERS or BOTH.", ConfigManager.GRANT_MODES));
        place(h, s, b("grace.grant-after-death", Material.TOTEM_OF_UNDYING, "Grace After Death", "Grant grace on respawn if none is active."));
        place(h, s, b("grace.effects.saturation.enabled", Material.COOKED_BEEF, "Saturation Effect", "Apply Saturation during grace."));
        place(h, s, n("grace.effects.saturation.level", Material.COOKED_BEEF, "Saturation Level", "Effect level.", 1, 10, 1, ""));
        place(h, s, n("grace.effects.saturation.duration-seconds", Material.COOKED_BEEF, "Saturation Duration", "0 = whole grace period.", 0, 86400, 60, " s"));
        place(h, s, b("grace.effects.resistance.enabled", Material.IRON_CHESTPLATE, "Resistance Effect", "Apply Resistance during grace."));
        place(h, s, n("grace.effects.resistance.level", Material.IRON_CHESTPLATE, "Resistance Level", "Effect level.", 1, 5, 1, ""));
        place(h, s, n("grace.effects.resistance.duration-seconds", Material.IRON_CHESTPLATE, "Resistance Duration", "0 = whole grace period.", 0, 86400, 60, " s"));
        place(h, s, b("grace.pvp.block-attacks-by-protected", Material.IRON_SWORD, "Mutual PvP Protection", "Protected players cannot attack others."));
        place(h, s, b("grace.pvp.block-damage-to-protected", Material.DIAMOND_CHESTPLATE, "Block Damage To Protected", "Other players cannot damage protected players."));
        place(h, s, b("grace.pvp.bypass-permission-enabled", Material.BARRIER, "Honour Bypass Permission", "glidelaunch.bypass.grace ignores PvP rules."));
        place(h, s, b("grace.display-remaining", Material.OAK_SIGN, "Show Remaining Time", "Action-bar countdown."));
        put(h, s.next(), item(Material.WRITABLE_BOOK, "<#2EFF2E>Active Grace Sessions", "<gray>Active: <#FAFA33>" + plugin.grace().activeRemaining().size(),
                "", "<#00CCFF>Click: <white>view"), (p, c) -> openSessions(p));
        back(h, this::openMain);
    }

    private void openSessions(Player p) { open(p, 54, "<dark_gray>Active Grace Sessions", this::fillSessions); }

    private void fillSessions(Player v, GuiHolder h) {
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<UUID, Long> en : plugin.grace().activeRemaining().entrySet()) {
            UUID id = en.getKey();
            list.add(new Entry(head(Bukkit.getOfflinePlayer(id), "<#00CCFF>" + name(id), "<gray>Remaining: <#2EFF2E>" + Util.duration(en.getValue()),
                            "", "<#00CCFF>Click: <white>remove grace"),
                    (p, c) -> confirm(p, "Remove grace from " + name(id) + "?", "Their protection and effects end immediately.",
                            pl -> { plugin.grace().revoke(id); openSessions(pl); }, this::openSessions)));
        }
        fillPaged(h, list, this::openGrace);
        if (!list.isEmpty()) put(h, 47, item(Material.TNT, "<#FF0000>Clear All Grace", "<gray>Removes every active grace session."),
                (p, c) -> confirm(p, "Remove ALL active grace?", "Applies to " + list.size() + " player(s).",
                        pl -> { plugin.grace().revokeAll(); openSessions(pl); }, this::openSessions));
    }

    // ---------- PLAYERS ----------
    public void openPlayers(Player p) { open(p, 54, "<dark_gray>Player Glide Management", this::fillPlayers); }

    private void fillPlayers(Player v, GuiHolder h) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort((a, b2) -> a.getName().compareToIgnoreCase(b2.getName()));
        List<Entry> list = new ArrayList<>();
        for (Player t : online) {
            UUID id = t.getUniqueId();
            list.add(new Entry(head(t, "<#00CCFF>" + t.getName(),
                            "<gray>Gliding: " + (plugin.glide().isGliding(id) ? "<#2EFF2E>yes" : "<#FF0000>no"),
                            "<gray>Grace: " + graceText(id), "", "<#00CCFF>Click: <white>manage"),
                    (p, c) -> openPlayerActions(p, id)));
        }
        fillPaged(h, list, this::openMain);
    }

    private void openPlayerActions(Player admin, UUID id) {
        open(admin, 36, "<dark_gray>Manage <#00CCFF>" + name(id), (v, h) -> {
            put(h, 10, item(Material.FIREWORK_ROCKET, "<#2EFF2E>Launch To Safe Location", "<gray>Teleports to a safe zone and glides."),
                    (p, c) -> withTarget(p, id, t -> { plugin.spawn().launch(t, false, p); }, h));
            put(h, 11, item(Material.ELYTRA, "<#2EFF2E>Glide In Place", "<gray>Starts a glide at their position."),
                    (p, c) -> withTarget(p, id, t -> {
                        if (!plugin.glide().start(t, false, null)) plugin.messages().raw(p, "<#FF0000>Could not start a glide for that player.");
                    }, h));
            put(h, 12, item(Material.RED_DYE, "<#FF0000>Stop Glide", "<gray>Ends an active glide (safe landing applies)."),
                    (p, c) -> withTarget(p, id, t -> {
                        if (plugin.glide().isGliding(id)) plugin.glide().stop(t, GlideManager.StopReason.ADMIN);
                        else plugin.messages().raw(p, "<#FAFA33>That player is not gliding.");
                    }, h));
            put(h, 13, item(Material.SHIELD, "<#2EFF2E>Apply Grace", "<gray>Duration: <#FAFA33>" + Util.duration(plugin.grace().defaultDurationMs()),
                            plugin.grace().enabled() ? "<gray>Grace mode is on." : "<#FF0000>Grace mode is OFF - enable it first."),
                    (p, c) -> {
                        plugin.grace().grant(id);
                        if (!plugin.grace().enabled()) plugin.messages().raw(p, "<#FAFA33>Grace stored, but grace mode is disabled (Grace Settings).");
                        else plugin.messages().raw(p, "<#2EFF2E>Grace applied to <white>" + name(id) + "</white>.");
                        h.refresh.run();
                    });
            put(h, 14, item(Material.BARRIER, "<#FF0000>Remove Grace", "<gray>Current: " + graceText(id)),
                    (p, c) -> confirm(p, "Remove grace from " + name(id) + "?", "Their protection and effects end immediately.",
                            pl -> { plugin.grace().revoke(id); openPlayerActions(pl, id); }, pl -> openPlayerActions(pl, id)));
            put(h, 15, item(Material.CLOCK, "<#8AE8FF>View Remaining Grace", "<gray>Remaining: " + graceText(id), "", "<#00CCFF>Click: <white>send to chat"),
                    (p, c) -> {
                        long r = plugin.grace().remaining(id);
                        plugin.messages().raw(p, "<gray>" + name(id) + " grace: " + (r > 0 ? "<#2EFF2E>" + Util.duration(r) : "<#FF0000>none"));
                    });
            back(h, this::openPlayers);
        });
    }

    private void withTarget(Player admin, UUID id, Consumer<Player> action, GuiHolder h) {
        Player t = Bukkit.getPlayer(id);
        if (t == null || !t.isOnline()) { plugin.messages().send(admin, "target-offline"); openPlayers(admin); return; }
        action.accept(t);
        h.refresh.run();
    }

    // ---------- INFO ----------
    public void openInfo(Player p) { open(p, 27, "<dark_gray>Plugin Information", this::fillInfo); }

    private void fillInfo(Player v, GuiHolder h) {
        FileConfiguration c = plugin.getConfig();
        World w = plugin.spawn().world();
        double[] ctr = plugin.spawn().centre(w);
        String[] lines = {
                "<gray>Version: <white>" + plugin.getPluginMeta().getVersion(),
                "<gray>Spawn: <white>" + w.getName() + " " + (int) ctr[0] + ", " + (int) ctr[1],
                "<gray>Radius: <white>" + c.getInt("spawn.min-radius") + " - " + c.getInt("spawn.max-radius"),
                "<gray>Active glides: <white>" + plugin.glide().activeCount(),
                "<gray>Active grace: <white>" + plugin.grace().activeRemaining().size(),
                "<gray>Grace mode: " + (plugin.grace().enabled() ? "<#2EFF2E>on" : "<#FF0000>off"), "",
                "<#00CCFF>Click: <white>send details to chat"};
        put(h, 13, item(Material.BOOK, "<#00CCFF>GlideLaunch", lines), (p, cl) -> {
            for (int i = 0; i < 6; i++) plugin.messages().raw(p, lines[i]);
        });
        back(h, this::openMain);
    }
}
