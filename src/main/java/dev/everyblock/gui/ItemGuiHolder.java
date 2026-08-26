package dev.everyblock.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ItemGuiHolder implements InventoryHolder {
    private final int page;
    private final BlockFilter filter;
    private Inventory inventory;

    public ItemGuiHolder(int page, BlockFilter filter) {
        this.page = page;
        this.filter = filter;
    }

    public int page() {
        return page;
    }

    public BlockFilter filter() {
        return filter;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
