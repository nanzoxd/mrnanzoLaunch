package dev.glidelaunch;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandManager implements CommandExecutor, TabCompleter {
    private final GlideLaunchPlugin plugin;

    public CommandManager(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission(GlideLaunchPlugin.ADMIN)) { plugin.messages().send(s, "no-permission"); return true; }
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("launch")) return launch(s, args);

        if (!(s instanceof Player p)) { plugin.messages().send(s, "player-only"); return true; }
        switch (name) {
            case "glidelaunch" -> plugin.gui().openMain(p);
            case "setspawn" -> {
                Location l = p.getLocation();
                plugin.spawn().setCentre(l);
                plugin.messages().send(p, "spawn-set",
                        Placeholder.unparsed("world", l.getWorld().getName()),
                        Placeholder.unparsed("x", String.valueOf(l.getBlockX())),
                        Placeholder.unparsed("y", String.valueOf(l.getBlockY())),
                        Placeholder.unparsed("z", String.valueOf(l.getBlockZ())));
            }
            case "glidetest" -> {
                if (plugin.glide().isGliding(p.getUniqueId())) {
                    plugin.messages().send(p, "already-gliding", Placeholder.unparsed("player", p.getName()));
                } else {
                    plugin.glide().start(p, true, null); // test session: no data is modified
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private boolean launch(CommandSender s, String[] args) {
        if (args.length == 0) {
            if (s instanceof Player p) plugin.gui().openPlayers(p); // GUI alternative: pick a test player
            else plugin.messages().send(s, "usage-launch");
            return true;
        }
        if (!args[0].equalsIgnoreCase("test")) { plugin.messages().send(s, "usage-launch"); return true; }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { plugin.messages().send(s, "target-offline"); return true; }
        } else if (s instanceof Player p) {
            target = p;
        } else {
            plugin.messages().send(s, "usage-launch");
            return true;
        }
        plugin.spawn().launch(target, true, s);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission(GlideLaunchPlugin.ADMIN) || !cmd.getName().equalsIgnoreCase("launch")) return out;
        if (args.length == 1) {
            if ("test".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("test");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
