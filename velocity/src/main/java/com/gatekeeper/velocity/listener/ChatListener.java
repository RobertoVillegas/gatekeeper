package com.gatekeeper.velocity.listener;

import com.gatekeeper.velocity.wizard.WizardManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;

public class ChatListener {
    private final WizardManager wizardManager;

    public ChatListener(WizardManager wizardManager) {
        this.wizardManager = wizardManager;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check if player is in a wizard session
        if (wizardManager.hasActiveSession(player.getUniqueId())) {
            // Handle the input
            boolean consumed = wizardManager.handleInput(player, message);

            if (consumed) {
                // Cancel the chat event so message isn't broadcast
                event.setResult(PlayerChatEvent.ChatResult.denied());
            }
        }
    }
}
