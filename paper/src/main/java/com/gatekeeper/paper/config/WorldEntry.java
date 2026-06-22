package com.gatekeeper.paper.config;

import org.bukkit.Material;

/**
 * A world/server shown in the menu.
 *
 * @param logicalId   logical server id (matches the Velocity config)
 * @param velocityName Velocity server name used by /server
 * @param displayName legacy-coded display name
 * @param material    icon material
 * @param restricted  whether access requires an entitlement
 */
public record WorldEntry(
    String logicalId,
    String velocityName,
    String displayName,
    Material material,
    boolean restricted
) {
}
