package com.gatekeeper.paper.gui;

import com.gatekeeper.paper.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for building GUI items from legacy-coded strings.
 */
public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            if (lore.length > 0) {
                List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Text.item(line));
                }
                meta.lore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, "&r");
    }
}
