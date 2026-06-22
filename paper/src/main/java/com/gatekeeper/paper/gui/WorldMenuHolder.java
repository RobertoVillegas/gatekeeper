package com.gatekeeper.paper.gui;

import com.gatekeeper.paper.config.WorldEntry;
import com.gatekeeper.paper.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The world-selection menu: click a world to connect (if allowed) or start an
 * application (if locked and you have no access).
 */
public class WorldMenuHolder implements InventoryHolder {

    private final List<WorldEntry> worlds;
    private final List<String> currentAccess;
    private final Map<Integer, WorldEntry> slotToWorld = new HashMap<>();

    private Inventory inventory;

    public WorldMenuHolder(List<WorldEntry> worlds, List<String> currentAccess) {
        this.worlds = worlds;
        this.currentAccess = currentAccess;
    }

    public Inventory create() {
        int rows = Math.max(1, (int) Math.ceil(worlds.size() / 9.0));
        int size = rows * 9;
        inventory = Bukkit.createInventory(this, size, Text.of("&8Choose a world"));

        int slot = 0;
        for (WorldEntry world : worlds) {
            boolean unlocked = !world.restricted() || currentAccess.contains(world.logicalId());
            String status = unlocked ? "&aClick to join" : "&cLocked &7- click to request access";
            inventory.setItem(slot, Items.of(
                world.material(),
                world.displayName(),
                status
            ));
            slotToWorld.put(slot, world);
            slot++;
        }
        return inventory;
    }

    public WorldEntry worldAt(int slot) {
        return slotToWorld.get(slot);
    }

    public boolean hasAccess(WorldEntry world) {
        return !world.restricted() || currentAccess.contains(world.logicalId());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
