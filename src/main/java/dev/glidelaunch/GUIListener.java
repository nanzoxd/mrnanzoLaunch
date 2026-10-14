package dev.glidelaunch;

import dev.glidelaunch.GUIManager.GuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Locks down GlideLaunch GUIs only; unrelated inventories are never touched. */
public final class GUIListener implements Listener {
    private final GlideLaunchPlugin plugin;

    public GUIListener(GlideLaunchPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof GuiHolder h)) return;
        e.setCancelled(true); // blocks take/move/shift-click/hotbar-swap/double-click collect
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission(GlideLaunchPlugin.ADMIN)) {
            p.closeInventory();
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (e.getClickedInventory() != top) return;
        ClickType t = e.getClick();
        if (t != ClickType.LEFT && t != ClickType.RIGHT && t != ClickType.SHIFT_LEFT && t != ClickType.SHIFT_RIGHT) return;
        plugin.gui().click(p, h, e.getSlot(), t);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof GuiHolder) e.setCancelled(true);
    }
}
