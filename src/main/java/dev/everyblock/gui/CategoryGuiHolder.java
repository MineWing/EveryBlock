package dev.everyblock.gui;

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class CategoryGuiHolder implements EveryBlockMenu {
    private Inventory inventory;

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
