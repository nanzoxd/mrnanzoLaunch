package dev.glidelaunch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final GlideLaunchPlugin plugin;

    public Messages(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    public static Component parse(String text, TagResolver... r) { return MM.deserialize(text, r); }

    /** Messages are sent without any plugin prefix. */
    private String prefix() { return ""; }

    public void send(CommandSender to, String key, TagResolver... r) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null || raw.isBlank()) return;
        to.sendMessage(parse(prefix() + raw, r));
    }

    public void raw(CommandSender to, String mini, TagResolver... r) {
        to.sendMessage(parse(prefix() + mini, r));
    }

    public void actionbar(Player p, String key, TagResolver... r) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null || raw.isBlank()) return;
        p.sendActionBar(parse(raw, r));
    }
}
