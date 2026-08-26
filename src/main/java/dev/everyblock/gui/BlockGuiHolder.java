package dev.everyblock.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class BlockGuiHolder implements InventoryHolder {
    private final int page;
    private final BlockFilter filter;
    private final String query;
    private final BlockCategory category;
    private Inventory inventory;

    public BlockGuiHolder(int page, BlockFilter filter, String query, BlockCategory category) {
        this.page = page;
        this.filter = filter;
        this.query = query;
        this.category = category;
    }

    public int page() {
        return page;
    }

    public BlockFilter filter() {
        return filter;
    }

    public String query() {
        return query;
    }

    public BlockCategory category() {
        return category;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
