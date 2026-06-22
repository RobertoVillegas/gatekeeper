package com.gatekeeper.paper.gui;

import com.gatekeeper.paper.config.WorldEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles clicks inside the Gatekeeper GUIs. Clicks are always cancelled so players
 * can never take items out of the menus.
 */
public class GuiListener implements Listener {

    private final GuiService service;

    public GuiListener(GuiService service) {
        this.service = service;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ApplyGuiHolder || holder instanceof WorldMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof ApplyGuiHolder applyGui) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            // Ignore clicks in the player's own inventory area.
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != applyGui) {
                return;
            }
            int slot = event.getSlot();
            if (applyGui.toggleIfServerSlot(slot)) {
                return;
            }
            if (applyGui.isSubmitSlot(slot)) {
                service.submit(player, applyGui);
            } else if (applyGui.isCancelSlot(slot)) {
                player.closeInventory();
                service.msg(player, "&7Application cancelled.");
            }
            return;
        }

        if (holder instanceof WorldMenuHolder worldMenu) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != worldMenu) {
                return;
            }
            WorldEntry world = worldMenu.worldAt(event.getSlot());
            if (world == null) {
                return;
            }
            player.closeInventory();
            if (worldMenu.hasAccess(world)) {
                service.connect(player, world);
            } else {
                service.msg(player, "&eYou don't have access to that world yet.");
                service.msg(player, "&7Request it with &f/apply \"YourName\" \"WhoInvitedYou\"");
            }
        }
    }
}
