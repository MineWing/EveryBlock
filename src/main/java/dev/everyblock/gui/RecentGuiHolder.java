package dev.everyblock.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class RecentGuiHolder implements InventoryHolder {
    private final int page;
    private Inventory inventory;

    public RecentGuiHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
