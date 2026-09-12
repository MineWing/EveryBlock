package dev.everyblock.gui;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.listener.InventoryTracker;
import dev.everyblock.model.Discovery;
import dev.everyblock.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class GuiService {
    private static final int PAGE_SIZE = 45;
    private static final int[] CATEGORY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24
    };

    private final EveryBlockPlugin plugin;

    public GuiService(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean open(Player player, int requestedPage, BlockFilter filter, String query) {
        return open(player, requestedPage, filter, query, BlockCategory.ALL, true);
    }

    public void openCategories(Player player) {
        openCategories(player, true);
    }

    public void openCategory(Player player, BlockCategory category) {
        open(player, 0, BlockFilter.ALL, "", category, true);
    }

    private void openCategories(Player player, boolean playOpenEffect) {
        CategoryGuiHolder holder = new CategoryGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().text("gui.block-menu.categories-title"));
        holder.inventory(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler());
        }
        BlockCategory[] categories = BlockCategory.values();
        for (int index = 0; index < Math.min(categories.length, CATEGORY_SLOTS.length); index++) {
            inventory.setItem(CATEGORY_SLOTS[index], categoryItem(categories[index]));
        }
        inventory.setItem(46, button(Material.CLOCK, "gui.buttons.recent-discoveries"));
        inventory.setItem(48, progressItem(BlockCategory.ALL));
        inventory.setItem(50, button(Material.NAME_TAG, "gui.buttons.search-blocks"));
        inventory.setItem(52, button(Material.BARRIER, "gui.buttons.close"));
        player.openInventory(inventory);
        if (playOpenEffect) {
            plugin.messages().send(player, "gui-open");
        }
    }

    public void openRecent(Player player, int requestedPage) {
        openRecent(player, requestedPage, true);
    }

    private void openRecent(Player player, int requestedPage, boolean playOpenEffect) {
        List<Discovery> recent = plugin.progress().recent();
        int pages = Math.max(1, (recent.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        RecentGuiHolder holder = new RecentGuiHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                plugin.messages().text("gui.block-menu.recent-title", pageReplacements(page, pages)));
        holder.inventory(inventory);

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(recent.size(), start + PAGE_SIZE); index++) {
            inventory.setItem(index - start, blockItem(recent.get(index).material()));
        }
        if (recent.isEmpty()) {
            inventory.setItem(22, emptyRecentButton());
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler());
        }
        if (page > 0) {
            inventory.setItem(45, pageButton(Material.ARROW, "gui.buttons.previous", page));
        }
        inventory.setItem(48, backButton("gui.buttons.back-to-categories.recent-lore"));
        inventory.setItem(50, button(Material.BARRIER, "gui.buttons.close"));
        if (page + 1 < pages) {
            inventory.setItem(53, pageButton(Material.ARROW, "gui.buttons.next", page + 2));
        }
        player.openInventory(inventory);
        if (playOpenEffect) {
            plugin.messages().send(player, "gui-open");
        }
    }

    private boolean open(Player player, int requestedPage, BlockFilter filter, String query,
                         BlockCategory category, boolean playOpenEffect) {
        List<Material> visible = visibleBlocks(filter, query, category);
        if (playOpenEffect && visible.isEmpty() && query != null && !query.isBlank()) {
            plugin.messages().send(player, "search-empty", Map.of("query", Text.escape(query)));
        }
        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        String title = plugin.messages().applyPlaceholders(plugin.settings().guiTitle(), Map.of(
                "category", Text.escape(category.displayName().toUpperCase(Locale.ROOT)),
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pages)));
        BlockGuiHolder holder = new BlockGuiHolder(page, filter, query == null ? "" : query, category);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(title));
        holder.inventory(inventory);

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(visible.size(), start + PAGE_SIZE); index++) {
            inventory.setItem(index - start, blockItem(visible.get(index)));
        }
        inventory.setItem(45, backButton("gui.buttons.back-to-categories.block-lore"));
        inventory.setItem(46, page > 0 ? pageButton(Material.ARROW, "gui.buttons.previous", page) : filler());
        inventory.setItem(47, filterToggle(filter));
        inventory.setItem(48, filler());
        inventory.setItem(49, progressItem(category));
        inventory.setItem(50, filler());
        inventory.setItem(51, button(Material.BARRIER, "gui.buttons.close"));
        inventory.setItem(52, filler());
        inventory.setItem(53, page + 1 < pages ? pageButton(Material.ARROW, "gui.buttons.next", page + 2) : filler());
        player.openInventory(inventory);
        if (playOpenEffect) {
            plugin.messages().send(player, "gui-open");
        }
        return true;
    }

    public void handleClick(Player player, int rawSlot, BlockGuiHolder holder) {
        switch (rawSlot) {
            case 45 -> openCategories(player);
            case 46 -> {
                if (isArrow(player, rawSlot)) {
                    open(player, holder.page() - 1, holder.filter(), holder.query(), holder.category(), true);
                }
            }
            case 47 -> open(player, 0, holder.filter().next(), holder.query(), holder.category(), true);
            case 51 -> player.closeInventory();
            case 53 -> {
                if (isArrow(player, rawSlot)
                        && open(player, holder.page() + 1, holder.filter(), holder.query(), holder.category(), false)) {
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            default -> {
                if (rawSlot < PAGE_SIZE) {
                    openRecipe(player, rawSlot);
                }
            }
        }
    }

    private void openRecipe(Player player, int rawSlot) {
        ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(rawSlot);
        if (clicked == null) {
            return;
        }
        openRecipe(player, clicked.getType());
    }

    public void openRecipe(Player player, Material material) {
        Recipe recipe = Bukkit.getRecipesFor(new ItemStack(material)).stream()
                .filter(CraftingRecipe.class::isInstance)
                .findFirst()
                .orElse(null);
        if (recipe == null) {
            plugin.messages().send(player, "no-recipe",
                    Map.of("block", InventoryTracker.display(material)));
            return;
        }
        RecipeGuiHolder holder = new RecipeGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.WORKBENCH,
                plugin.messages().text("gui.block-menu.recipe-title",
                        Map.of("block", Text.escape(Text.pretty(material.name())))));
        holder.inventory(inventory);
        // A custom holder always backs the inventory with a generic container, so it
        // never implements CraftingInventory even for InventoryType.WORKBENCH - the
        // matrix (slots 1-9) and result (slot 0) have to be set by raw slot instead.
        ItemStack[] grid = recipeGrid(recipe);
        for (int index = 0; index < grid.length; index++) {
            if (grid[index] != null) {
                inventory.setItem(index + 1, grid[index]);
            }
        }
        inventory.setItem(0, recipe.getResult().clone());
        player.openInventory(inventory);
        plugin.messages().send(player, "recipe-open");
    }

    private ItemStack[] recipeGrid(Recipe recipe) {
        ItemStack[] grid = new ItemStack[9];
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                String line = shape[row];
                for (int col = 0; col < line.length() && col < 3; col++) {
                    RecipeChoice choice = choices.get(line.charAt(col));
                    if (choice != null) {
                        grid[row * 3 + col] = choice.getItemStack();
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = shapeless.getChoiceList();
            for (int index = 0; index < choices.size() && index < 9; index++) {
                grid[index] = choices.get(index).getItemStack();
            }
        }
        return grid;
    }

    public void handleCategoryClick(Player player, int rawSlot) {
        if (rawSlot == 52) {
            player.closeInventory();
            return;
        }
        if (rawSlot == 46) {
            openRecent(player, 0);
            return;
        }
        for (int index = 0; index < CATEGORY_SLOTS.length; index++) {
            if (CATEGORY_SLOTS[index] == rawSlot && index < BlockCategory.values().length) {
                openCategory(player, BlockCategory.values()[index]);
                return;
            }
        }
    }

    public void handleRecentClick(Player player, int rawSlot, RecentGuiHolder holder) {
        switch (rawSlot) {
            case 45 -> openRecent(player, holder.page() - 1);
            case 48 -> openCategories(player);
            case 50 -> player.closeInventory();
            case 53 -> {
                if (isArrow(player, rawSlot)) {
                    openRecent(player, holder.page() + 1, false);
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            default -> {
            }
        }
    }

    public void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Object currentHolder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (!(currentHolder instanceof ItemGuiHolder) && ownsMenu(currentHolder)
                    && !player.hasPermission("everyblock.use")) {
                player.closeInventory();
                continue;
            }
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof BlockGuiHolder holder) {
                open(player, holder.page(), holder.filter(), holder.query(), holder.category(), false);
            } else if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof CategoryGuiHolder) {
                openCategories(player, false);
            } else if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof RecentGuiHolder holder) {
                openRecent(player, holder.page(), false);
            }
        }
    }

    /** Close every inventory owned by this plugin before its listeners are removed. */
    public void closeOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ownsMenu(player.getOpenInventory().getTopInventory().getHolder(false))) {
                player.closeInventory();
            }
        }
    }

    private static boolean ownsMenu(Object holder) {
        return holder instanceof BlockGuiHolder || holder instanceof CategoryGuiHolder
                || holder instanceof RecentGuiHolder || holder instanceof ItemGuiHolder
                || holder instanceof RecipeGuiHolder;
    }

    private List<Material> visibleBlocks(BlockFilter filter, String query, BlockCategory category) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).replace('_', ' ').strip();
        return plugin.catalogue().blocks().stream()
                .filter(material -> category == BlockCategory.ALL || BlockCategory.of(material) == category)
                .filter(material -> switch (filter) {
                    case ALL -> true;
                    case MISSING -> !plugin.progress().found(material);
                    case FOUND -> plugin.progress().found(material);
                })
                .filter(material -> needle.isEmpty()
                        || Text.pretty(material.name()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((Material material) -> BlockRarity.of(material))
                        .thenComparing(material -> Text.pretty(material.name())))
                .toList();
    }

    private ItemStack blockItem(Material material) {
        boolean found = plugin.progress().found(material);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // Paper exposes a handful of technical materials as item/block-like. The
            // catalogue filters them, but this fallback keeps the GUI safe if a future
            // Minecraft version adds another unusual material.
            item = new ItemStack(Material.PAPER);
            meta = Objects.requireNonNull(item.getItemMeta());
        }
        String name = Text.escape(Text.pretty(material.name()));
        BlockRarity rarity = BlockRarity.of(material);
        Component itemName;
        if (rarity == BlockRarity.EPIC || rarity == BlockRarity.LEGENDARY) {
            String prefixKey = found ? "gui.collectible.collected-prefix" : "gui.collectible.missing-prefix";
            String prefix = plugin.messages().string(prefixKey, Map.of(), "");
            itemName = Text.parse(prefix + "<bold>" + rarity.style(name) + "</bold>");
        } else {
            String nameKey = found ? "gui.collectible.collected-name" : "gui.collectible.missing-name";
            itemName = plugin.messages().text(nameKey, Map.of("name", name));
        }
        meta.displayName(itemName);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(plugin.messages().text("gui.collectible.rarity-line", Map.of("rarity", rarity.miniMessage())));
        lore.add(Component.empty());
        if (found) {
            Discovery discovery = plugin.progress().discovery(material).orElseThrow();
            lore.add(plugin.messages().text("gui.collectible.collected-header"));
            lore.add(plugin.messages().text("gui.collectible.found-by",
                    Map.of("player", Text.escape(discovery.playerName()))));
            lore.add(plugin.messages().text("gui.collectible.discovered", Map.of("date", formatDate(discovery))));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(plugin.messages().text("gui.collectible.not-collected-header"));
            lore.add(plugin.messages().text("gui.collectible.bring-block"));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack progressItem(BlockCategory category) {
        int total = categoryTotal(category);
        int found = categoryFound(category);
        ItemStack item = new ItemStack(category == BlockCategory.ALL ? Material.NETHER_STAR : category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text("gui.buttons.team-progress.name"));
        meta.lore(List.of(
                Component.empty(),
                Text.parse(progressBar(found, total, 20)),
                progressLine("gui.progress.collected-line", found, total),
                plugin.messages().text("gui.progress.complete-line", Map.of("percent", Text.percent(found, total))),
                plugin.messages().text("gui.progress.remaining-line",
                        Map.of("remaining", Integer.toString(Math.max(0, total - found))))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryItem(BlockCategory category) {
        int total = categoryTotal(category);
        int found = categoryFound(category);
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text("gui.categories.name-format", Map.of("name", category.displayName())));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().text("gui.categories.description-format",
                Map.of("description", category.description())));
        lore.add(Component.empty());
        lore.add(Text.parse(progressBar(found, total, 16)));
        lore.add(progressLine("gui.progress.collected-line", found, total));
        lore.add(plugin.messages().text("gui.progress.remaining-line",
                Map.of("remaining", Integer.toString(Math.max(0, total - found)))));
        lore.add(Component.empty());
        lore.add(plugin.messages().text("gui.categories.click-to-browse"));
        meta.lore(lore);
        if (total > 0 && found >= total) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component progressLine(String key, int found, int total) {
        return plugin.messages().text(key, Map.of("found", Integer.toString(found), "total", Integer.toString(total)));
    }

    private String progressBar(int found, int total, int width) {
        String filled = plugin.messages().string("gui.progress-bar.filled", Map.of(), "#f7a48d");
        String empty = plugin.messages().string("gui.progress-bar.empty", Map.of(), "dark_gray");
        return Text.progressBar(found, total, width, filled, empty);
    }

    private int categoryTotal(BlockCategory category) {
        if (category == BlockCategory.ALL) {
            return plugin.progress().totalCount();
        }
        return (int) plugin.catalogue().blocks().stream()
                .filter(material -> BlockCategory.of(material) == category).count();
    }

    private int categoryFound(BlockCategory category) {
        if (category == BlockCategory.ALL) {
            return plugin.progress().collectedCount();
        }
        return (int) plugin.catalogue().blocks().stream()
                .filter(material -> BlockCategory.of(material) == category)
                .filter(plugin.progress()::found).count();
    }

    private ItemStack filterToggle(BlockFilter active) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text("gui.buttons.view-filter.name"));
        meta.lore(List.of(
                Component.empty(),
                filterLine("all", active == BlockFilter.ALL),
                filterLine("found", active == BlockFilter.FOUND),
                filterLine("missing", active == BlockFilter.MISSING),
                Component.empty(),
                plugin.messages().text("gui.buttons.view-filter.click-to-cycle")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private Component filterLine(String filterKey, boolean active) {
        String label = plugin.messages().string("gui.filters." + filterKey, Map.of(), Text.pretty(filterKey));
        String key = active ? "gui.buttons.view-filter.active" : "gui.buttons.view-filter.inactive";
        return plugin.messages().text(key, Map.of("label", label));
    }

    private ItemStack button(Material material, String baseKey) {
        return button(material, baseKey, Map.of());
    }

    private ItemStack button(Material material, String baseKey, Map<String, String> replacements) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text(baseKey + ".name", replacements));
        List<Component> lore = plugin.messages().lines(baseKey + ".lore", replacements);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pageButton(Material material, String baseKey, int displayPage) {
        return button(material, baseKey, Map.of("page", Integer.toString(displayPage)));
    }

    private ItemStack backButton(String loreKey) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text("gui.buttons.back-to-categories.name"));
        meta.lore(plugin.messages().lines(loreKey));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack emptyRecentButton() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text("gui.recent-menu.empty-name"));
        meta.lore(plugin.messages().lines("gui.recent-menu.empty-lore"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static Map<String, String> pageReplacements(int page, int pages) {
        return Map.of("page", Integer.toString(page + 1), "pages", Integer.toString(pages));
    }

    private static boolean isArrow(Player player, int slot) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        return item != null && item.getType() == Material.ARROW;
    }

    private String formatDate(Discovery discovery) {
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(plugin.settings().dateFormat());
        } catch (IllegalArgumentException exception) {
            formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        }
        return formatter.withZone(ZoneId.systemDefault()).format(discovery.discoveredAt());
    }

}
