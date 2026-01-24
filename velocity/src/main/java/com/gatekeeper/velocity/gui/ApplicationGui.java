package com.gatekeeper.velocity.gui;

import dev.simplix.protocolize.api.Protocolize;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.api.inventory.Inventory;
import dev.simplix.protocolize.api.inventory.InventoryClick;
import dev.simplix.protocolize.api.inventory.InventoryClose;
import dev.simplix.protocolize.api.player.ProtocolizePlayer;
import dev.simplix.protocolize.data.inventory.InventoryType;

import java.util.*;
import java.util.function.Consumer;

/**
 * GUI for displaying application summary and server selection.
 *
 * Layout (9x4 = 36 slots):
 * Row 0: [filler] [filler] [filler] [filler] [TITLE] [filler] [filler] [filler] [filler]
 * Row 1: [filler] [NAME] [filler] [DISCORD] [filler] [INVITER] [filler] [NOTES] [filler]
 * Row 2: [filler] [SERVER1] [filler] [SERVER2] [filler] [SERVER3] [filler] [SERVER4] [filler]
 * Row 3: [filler] [filler] [filler] [SUBMIT] [filler] [CANCEL] [filler] [filler] [filler]
 */
public class ApplicationGui {

    // Slot positions
    private static final int SLOT_TITLE = 4;
    private static final int SLOT_NAME = 10;
    private static final int SLOT_DISCORD = 12;
    private static final int SLOT_INVITER = 14;
    private static final int SLOT_NOTES = 16;
    private static final int SLOT_SUBMIT = 30;
    private static final int SLOT_CANCEL = 32;

    // Server slots (row 2)
    private static final int[] SERVER_SLOTS = {19, 21, 23, 25};

    private final UUID playerUuid;
    private final ApplicationData applicationData;
    private final List<String> availableServers;
    private final Set<String> selectedServers;
    private final Consumer<ApplicationResult> onComplete;

    private Inventory inventory;
    private Map<Integer, String> slotToServer = new HashMap<>();

    public ApplicationGui(
        UUID playerUuid,
        ApplicationData applicationData,
        List<String> availableServers,
        List<String> defaultServers,
        Consumer<ApplicationResult> onComplete
    ) {
        this.playerUuid = playerUuid;
        this.applicationData = applicationData;
        this.availableServers = new ArrayList<>(availableServers);
        this.selectedServers = new HashSet<>(defaultServers);
        this.onComplete = onComplete;

        buildInventory();
    }

    private void buildInventory() {
        inventory = new Inventory(InventoryType.GENERIC_9X4);
        inventory.title(ChatElement.ofLegacyText("§8Server Access Application"));

        // Fill with glass panes
        for (int i = 0; i < 36; i++) {
            inventory.item(i, ItemBuilder.createFiller());
        }

        // Title
        inventory.item(SLOT_TITLE, ItemBuilder.createTitle("Application Summary"));

        // Info items
        inventory.item(SLOT_NAME, ItemBuilder.createInfoItem(
            dev.simplix.protocolize.data.ItemType.NAME_TAG,
            "Real Name",
            applicationData.realName() != null ? applicationData.realName() : "Not provided"
        ));

        inventory.item(SLOT_DISCORD, ItemBuilder.createInfoItem(
            dev.simplix.protocolize.data.ItemType.BOOK,
            "Discord",
            applicationData.discord() != null ? applicationData.discord() : "Not provided"
        ));

        inventory.item(SLOT_INVITER, ItemBuilder.createInfoItem(
            dev.simplix.protocolize.data.ItemType.PLAYER_HEAD,
            "Invited By",
            applicationData.inviter() != null ? applicationData.inviter() : "Not provided"
        ));

        inventory.item(SLOT_NOTES, ItemBuilder.createInfoItem(
            dev.simplix.protocolize.data.ItemType.PAPER,
            "Notes",
            applicationData.notes() != null ? truncate(applicationData.notes(), 30) : "None"
        ));

        // Server selection icons
        updateServerIcons();

        // Action buttons
        updateButtons();

        // Register click handler
        inventory.onClick(this::handleClick);

        // Register close handler
        inventory.onClose(this::handleClose);
    }

    private void updateServerIcons() {
        slotToServer.clear();

        int serverIndex = 0;
        for (String server : availableServers) {
            if (serverIndex >= SERVER_SLOTS.length) break;

            int slot = SERVER_SLOTS[serverIndex];
            boolean selected = selectedServers.contains(server);

            inventory.item(slot, ItemBuilder.createServerIcon(
                server,
                capitalize(server),
                selected
            ));

            slotToServer.put(slot, server);
            serverIndex++;
        }
    }

    private void updateButtons() {
        boolean canSubmit = !selectedServers.isEmpty();
        inventory.item(SLOT_SUBMIT, ItemBuilder.createSubmitButton(canSubmit));
        inventory.item(SLOT_CANCEL, ItemBuilder.createCancelButton());
    }

    private void handleClick(InventoryClick click) {
        // Always cancel the click to prevent item pickup
        click.cancelled(true);

        int slot = click.slot();

        // Check if it's a server slot
        if (slotToServer.containsKey(slot)) {
            String server = slotToServer.get(slot);
            toggleServer(server);
            return;
        }

        // Check if it's submit button
        if (slot == SLOT_SUBMIT && !selectedServers.isEmpty()) {
            close();
            onComplete.accept(new ApplicationResult(
                true,
                applicationData,
                new ArrayList<>(selectedServers)
            ));
            return;
        }

        // Check if it's cancel button
        if (slot == SLOT_CANCEL) {
            close();
            onComplete.accept(new ApplicationResult(false, null, null));
            return;
        }
    }

    private void handleClose(InventoryClose close) {
        // If closed without submitting, treat as cancel
        // The GuiManager will handle cleanup
    }

    private void toggleServer(String server) {
        if (selectedServers.contains(server)) {
            selectedServers.remove(server);
        } else {
            selectedServers.add(server);
        }

        // Refresh the display
        updateServerIcons();
        updateButtons();

        // Send updated inventory to player
        ProtocolizePlayer player = Protocolize.playerProvider().player(playerUuid);
        if (player != null) {
            player.openInventory(inventory);
        }
    }

    /**
     * Open the GUI for the player.
     */
    public void open() {
        ProtocolizePlayer player = Protocolize.playerProvider().player(playerUuid);
        if (player != null) {
            player.openInventory(inventory);
        }
    }

    /**
     * Close the GUI for the player.
     */
    public void close() {
        ProtocolizePlayer player = Protocolize.playerProvider().player(playerUuid);
        if (player != null) {
            player.closeInventory();
        }
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Result of the application GUI interaction.
     */
    public record ApplicationResult(
        boolean submitted,
        ApplicationData data,
        List<String> selectedServers
    ) {}
}
