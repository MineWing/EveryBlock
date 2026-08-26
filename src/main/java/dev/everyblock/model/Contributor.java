package dev.everyblock.model;

import java.util.UUID;

public record Contributor(UUID playerId, String playerName, int amount) {
}
