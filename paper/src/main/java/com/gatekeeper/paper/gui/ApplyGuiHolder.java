package com.gatekeeper.paper.gui;

import com.gatekeeper.paper.api.ApplicationData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gatekeeper.paper.util.Text;

/**
 * The server-selection + submit GUI, rendered as a backend Bukkit inventory
 * (protocol-robust; Geyser translates it to a Bedrock form for Bedrock players).
 */
public class ApplyGuiHolder implements InventoryHolder {

    private static final int SIZE = 36;
    private static final int SLOT_NAME = 10;
    private static final int SLOT_INVITER = 12;
    private static final int SLOT_DISCORD = 14;
    private static final int SLOT_NOTES = 16;
    private static final int[] SERVER_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int SLOT_SUBMIT = 30;
    private static final int SLOT_CANCEL = 32;

    private final ApplicationData data;
    private final List<String> availableServers;
    private final Set<String> selected = new HashSet<>();
    private final Map<Integer, String> slotToServer = new HashMap<>();

    private Inventory inventory;

    public ApplyGuiHolder(ApplicationData data, List<String> availableServers, List<String> defaultServers) {
        this.data = data;
        this.availableServers = availableServers;
        // Pre-select defaults that are actually available.
        for (String def : defaultServers) {
            if (availableServers.contains(def)) {
                selected.add(def);
            }
        }
    }

    /** Build the inventory and render the initial state. */
    public Inventory create() {
        inventory = Bukkit.createInventory(this, SIZE, Text.of("&8Server Access Application"));
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, Items.filler());
        }
        inventory.setItem(SLOT_NAME, Items.of(Material.NAME_TAG, "&eName", "&7" + safe(data.realName())));
        inventory.setItem(SLOT_INVITER, Items.of(Material.PLAYER_HEAD, "&eInvited by", "&7" + safe(data.inviter())));
        inventory.setItem(SLOT_DISCORD, Items.of(Material.BOOK, "&eDiscord", "&7" + safe(data.discord())));
        inventory.setItem(SLOT_NOTES, Items.of(Material.PAPER, "&eNotes", "&7" + safe(data.notes())));
        render();
        return inventory;
    }

    private void render() {
        slotToServer.clear();
        int idx = 0;
        for (String server : availableServers) {
            if (idx >= SERVER_SLOTS.length) {
                break;
            }
            int slot = SERVER_SLOTS[idx++];
            boolean on = selected.contains(server);
            inventory.setItem(slot, Items.of(
                on ? Material.LIME_DYE : Material.GRAY_DYE,
                (on ? "&a" : "&7") + capitalize(server),
                on ? "&aSelected" : "&7Click to request",
                "&8Click to toggle"
            ));
            slotToServer.put(slot, server);
        }

        boolean canSubmit = !selected.isEmpty();
        inventory.setItem(SLOT_SUBMIT, Items.of(
            canSubmit ? Material.EMERALD_BLOCK : Material.BARRIER,
            canSubmit ? "&aSubmit application" : "&cSelect at least one server",
            "&7Requesting: &f" + (selected.isEmpty() ? "none" : String.join(", ", selected))
        ));
        inventory.setItem(SLOT_CANCEL, Items.of(Material.RED_STAINED_GLASS_PANE, "&cCancel"));
    }

    /** Toggle a server selection and re-render. Returns true if the slot was a server slot. */
    public boolean toggleIfServerSlot(int slot) {
        String server = slotToServer.get(slot);
        if (server == null) {
            return false;
        }
        if (!selected.remove(server)) {
            selected.add(server);
        }
        render();
        return true;
    }

    public boolean isSubmitSlot(int slot) {
        return slot == SLOT_SUBMIT && !selected.isEmpty();
    }

    public boolean isCancelSlot(int slot) {
        return slot == SLOT_CANCEL;
    }

    public ApplicationData data() {
        return data;
    }

    public List<String> selectedServers() {
        return List.copyOf(selected);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "Not provided" : s;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
