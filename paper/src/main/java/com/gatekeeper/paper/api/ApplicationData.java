package com.gatekeeper.paper.api;

/**
 * The text fields a player provides via the /apply command, carried through the GUI.
 */
public record ApplicationData(String realName, String inviter, String discord, String notes) {
}
