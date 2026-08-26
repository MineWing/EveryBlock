package dev.everyblock.model;

import org.bukkit.Material;

import java.time.Instant;
import java.util.UUID;

public record Discovery(Material material, UUID playerId, String playerName, Instant discoveredAt) {
}
