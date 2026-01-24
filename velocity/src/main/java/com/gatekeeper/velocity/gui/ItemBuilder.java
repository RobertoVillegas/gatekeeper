package com.gatekeeper.velocity.gui;

import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.api.item.ItemStack;
import dev.simplix.protocolize.api.item.component.CustomNameComponent;
import dev.simplix.protocolize.api.item.component.EnchantmentGlintOverrideComponent;
import dev.simplix.protocolize.api.item.component.LoreComponent;
import dev.simplix.protocolize.data.ItemType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for building ItemStacks for GUI display.
 */
public class ItemBuilder {

    /**
     * Create an info item displaying a label and value.
     */
    public static ItemStack createInfoItem(ItemType type, String label, String value) {
        ItemStack item = new ItemStack(type);
        item.addComponent(CustomNameComponent.create(
            ChatElement.ofLegacyText("§f" + label)
        ));
        item.addComponent(LoreComponent.create(List.of(
            ChatElement.ofLegacyText("§7" + value)
        )));
        item.amount((byte) 1);
        return item;
    }

    /**
     * Create a server selection icon.
     */
    public static ItemStack createServerIcon(String serverName, String displayName, boolean selected) {
        ItemType type = getServerIconType(serverName);
        ItemStack item = new ItemStack(type);

        String prefix = selected ? "§a§l" : "§f";
        String status = selected ? "§a[Selected]" : "§7Click to select";

        item.addComponent(CustomNameComponent.create(
            ChatElement.ofLegacyText(prefix + displayName)
        ));
        item.addComponent(LoreComponent.create(List.of(
            ChatElement.ofLegacyText(""),
            ChatElement.ofLegacyText(status)
        )));

        if (selected) {
            item.addComponent(EnchantmentGlintOverrideComponent.create(true));
        }

        item.amount((byte) 1);
        return item;
    }

    /**
     * Create the submit button.
     */
    public static ItemStack createSubmitButton(boolean enabled) {
        ItemStack item = new ItemStack(enabled ? ItemType.EMERALD : ItemType.GRAY_DYE);

        String name = enabled ? "§a§lSubmit Application" : "§7§lSubmit Application";
        String lore = enabled ? "§7Click to submit" : "§cSelect at least one server";

        item.addComponent(CustomNameComponent.create(
            ChatElement.ofLegacyText(name)
        ));
        item.addComponent(LoreComponent.create(List.of(
            ChatElement.ofLegacyText(""),
            ChatElement.ofLegacyText(lore)
        )));

        item.amount((byte) 1);
        return item;
    }

    /**
     * Create the cancel button.
     */
    public static ItemStack createCancelButton() {
        ItemStack item = new ItemStack(ItemType.BARRIER);
        item.addComponent(CustomNameComponent.create(
            ChatElement.ofLegacyText("§c§lCancel")
        ));
        item.addComponent(LoreComponent.create(List.of(
            ChatElement.ofLegacyText(""),
            ChatElement.ofLegacyText("§7Click to cancel application")
        )));
        item.amount((byte) 1);
        return item;
    }

    /**
     * Create a filler/decoration item (gray glass pane).
     */
    public static ItemStack createFiller() {
        ItemStack item = new ItemStack(ItemType.GRAY_STAINED_GLASS_PANE);
        item.addComponent(CustomNameComponent.create(
            ChatElement.ofLegacyText(" ")
        ));
        item.amount((byte) 1);
        return item;
    }

    /**
     * Create a section divider item.
     */
    public static ItemStack createDivider() {
        ItemStack item = new ItemStack(ItemType.BLACK_STAINED_GLASS_PANE);
        item.addComponent(CustomNameComponent.create(
            ChatElement.ofLegacyText(" ")
        ));
        item.amount((byte) 1);
        return item;
    }

    /**
     * Create a title/header item.
     */
    public static ItemStack createTitle(String title) {
        ItemStack item = new ItemStack(ItemType.NETHER_STAR);
        item.addComponent(CustomNameComponent.create(
            ChatElement.ofLegacyText("§6§l" + title)
        ));
        item.amount((byte) 1);
        return item;
    }

    /**
     * Get an appropriate icon type for a server name.
     */
    private static ItemType getServerIconType(String serverName) {
        String lower = serverName.toLowerCase();
        if (lower.contains("survival")) {
            return ItemType.GRASS_BLOCK;
        } else if (lower.contains("creative")) {
            return ItemType.PAINTING;
        } else if (lower.contains("smp")) {
            return ItemType.CHEST;
        } else if (lower.contains("lobby")) {
            return ItemType.OAK_DOOR;
        } else if (lower.contains("skyblock")) {
            return ItemType.OAK_SAPLING;
        } else if (lower.contains("prison")) {
            return ItemType.IRON_BARS;
        } else if (lower.contains("minigame") || lower.contains("game")) {
            return ItemType.BOW;
        } else {
            return ItemType.PAPER;
        }
    }
}
