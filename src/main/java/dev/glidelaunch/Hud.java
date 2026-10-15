package dev.glidelaunch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Action bar / title helpers using the plugin colour palette. */
final class Hud {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Hud() {}

    private static final String SMALL_CAPS_SRC = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL_CAPS_DST = "\u1d00\u0299\u1d04\u1d05\u1d07\ua730\u0262\u029c\u026a\u1d0a\u1d0b\u029f\u1d0d\u0274\u1d0f\u1d18\u01eb\u0280\ua731\u1d1b\u1d1c\u1d20\u1d21x\u028f\u1d22";

    /** Converts a-z to Unicode small caps; everything else is left untouched. */
    static String smallCaps(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            int i = SMALL_CAPS_SRC.indexOf(Character.toLowerCase(ch));
            sb.append(i >= 0 ? SMALL_CAPS_DST.charAt(i) : ch);
        }
        return sb.toString();
    }

    /** Multi-stop gradient text component. */
    static Component gradient(String text, java.util.List<String> colors) {
        String stops = String.join(":", colors);
        return MM.deserialize("<gradient:" + stops + ">" + text + "</gradient>");
    }

    static void bigTitle(Player p, Component main, int stayTicks) {
        p.showTitle(Title.title(main, Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(500))));
    }

    /** Big number title for the landing countdown (red -> yellow -> green as it nears zero). */
    static void countdown(Player p, int n, int total) {
        String color = n <= 1 ? "#2EFF2E" : (n == 2 ? "#FAFA33" : "#FF0000");
        Title t = Title.title(
                MM.deserialize("<" + color + ">" + n),
                MM.deserialize("<#8AE8FF>Prepare to land"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
        p.showTitle(t);
    }
}
