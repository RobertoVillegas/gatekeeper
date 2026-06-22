package com.gatekeeper.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Small helper to turn legacy '&'-coded strings into Adventure Components,
 * with item-friendly defaults (no italics).
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    public static Component of(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    /** For item names/lore: disable the default italic styling Minecraft applies. */
    public static Component item(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }
}
