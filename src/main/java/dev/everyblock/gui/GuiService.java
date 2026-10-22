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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuiService {
    private static final int PAGE_SIZE = 45;
    private static final int[] CATEGORY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24
    };

    private final EveryBlockPlugin plugin;
    private final MenuItems items;
    private final CoalescedRefresh refresher;
    private volatile CatalogueIndex index;

    public GuiService(EveryBlockPlugin plugin) {
        this.plugin = plugin;
        this.items = new MenuItems(plugin);
        this.refresher = new CoalescedRefresh(plugin, this::refreshNow);
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
        ItemStack filler = MenuItems.filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        BlockCategory[] categories = BlockCategory.values();
        for (int index = 0; index < Math.min(categories.length, CATEGORY_SLOTS.length); index++) {
            inventory.setItem(CATEGORY_SLOTS[index], categoryItem(categories[index]));
        }
        inventory.setItem(46, items.button(Material.CLOCK, "gui.buttons.recent-discoveries"));
        inventory.setItem(48, progressItem(BlockCategory.ALL));
        inventory.setItem(50, items.button(Material.NAME_TAG, "gui.buttons.search-blocks"));
        inventory.setItem(52, items.button(Material.BARRIER, "gui.buttons.close"));
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
        ItemStack filler = MenuItems.filler();
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(45, items.pageButton("gui.buttons.previous", page));
        }
        inventory.setItem(48, backButton("gui.buttons.back-to-categories.recent-lore"));
        inventory.setItem(50, items.button(Material.BARRIER, "gui.buttons.close"));
        if (page + 1 < pages) {
            inventory.setItem(53, items.pageButton("gui.buttons.next", page + 2));
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
        BlockGuiHolder holder = new BlockGuiHolder(page, filter, query == null ? "" : query, category);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(blockMenuTitle(category, page, pages)));
        holder.inventory(inventory);

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(visible.size(), start + PAGE_SIZE); index++) {
            inventory.setItem(index - start, blockItem(visible.get(index)));
        }
        ItemStack filler = MenuItems.filler();
        inventory.setItem(45, backButton("gui.buttons.back-to-categories.block-lore"));
        inventory.setItem(46, page > 0 ? items.pageButton("gui.buttons.previous", page) : filler);
        inventory.setItem(47, items.filterToggle(filter));
        inventory.setItem(48, filler);
        inventory.setItem(49, progressItem(category));
        inventory.setItem(50, filler);
        inventory.setItem(51, items.button(Material.BARRIER, "gui.buttons.close"));
        inventory.setItem(52, filler);
        inventory.setItem(53, page + 1 < pages ? items.pageButton("gui.buttons.next", page + 2) : filler);
        player.openInventory(inventory);
        if (playOpenEffect) {
            plugin.messages().send(player, "gui-open");
        }
        return true;
    }

    private String blockMenuTitle(BlockCategory category, int page, int pages) {
        Map<String, String> replacements = Map.of(
                "category", Text.escape(category.displayName().toUpperCase(Locale.ROOT)),
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pages));
        String title = plugin.messages().string("gui.block-menu.title", replacements, null);
        return title != null ? title : plugin.messages().applyPlaceholders(plugin.settings().guiTitle(), replacements);
    }

    public void handleClick(Player player, int rawSlot, BlockGuiHolder holder) {
        switch (rawSlot) {
            case 45 -> openCategories(player);
            case 46 -> {
                if (MenuItems.isArrow(player, rawSlot)
                        && open(player, holder.page() - 1, holder.filter(), holder.query(), holder.category(), false)) {
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            case 47 -> open(player, 0, holder.filter().next(), holder.query(), holder.category(), true);
            case 51 -> player.closeInventory();
            case 53 -> {
                if (MenuItems.isArrow(player, rawSlot)
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
            case 45 -> {
                if (MenuItems.isArrow(player, rawSlot)) {
                    openRecent(player, holder.page() - 1, false);
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            case 48 -> openCategories(player);
            case 50 -> player.closeInventory();
            case 53 -> {
                if (MenuItems.isArrow(player, rawSlot)) {
                    openRecent(player, holder.page() + 1, false);
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            default -> {
            }
        }
    }

    /** Re-renders open block menus, coalescing bursts into at most one pass per second. */
    public void refreshOpenMenus() {
        refresher.request();
    }

    private void refreshNow() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Object currentHolder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (!(currentHolder instanceof ItemGuiHolder) && ownsMenu(currentHolder)
                    && !player.hasPermission("everyblock.use")) {
                player.closeInventory();
                continue;
            }
            if (currentHolder instanceof BlockGuiHolder holder) {
                open(player, holder.page(), holder.filter(), holder.query(), holder.category(), false);
            } else if (currentHolder instanceof CategoryGuiHolder) {
                openCategories(player, false);
            } else if (currentHolder instanceof RecentGuiHolder holder) {
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
        return holder instanceof EveryBlockMenu;
    }

    private List<Material> visibleBlocks(BlockFilter filter, String query, BlockCategory category) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).replace('_', ' ').strip();
        CatalogueIndex catalogue = index();
        List<Material> visible = new ArrayList<>();
        for (Material material : catalogue.materials(category)) {
            boolean matchesFilter = switch (filter) {
                case ALL -> true;
                case MISSING -> !plugin.progress().found(material);
                case FOUND -> plugin.progress().found(material);
            };
            if (matchesFilter && (needle.isEmpty() || catalogue.searchNames().get(material).contains(needle))) {
                visible.add(material);
            }
        }
        return visible;
    }

    private ItemStack blockItem(Material material) {
        Discovery discovery = plugin.progress().found(material)
                ? plugin.progress().discovery(material).orElseThrow() : null;
        return items.collectible(material, discovery, true, "gui.collectible.bring-block");
    }

    private ItemStack progressItem(BlockCategory category) {
        return items.progress(category == BlockCategory.ALL ? Material.NETHER_STAR : category.icon(),
                "gui.buttons.team-progress.name", categoryFound(category), categoryTotal(category));
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
        lore.add(Text.parse(items.progressBar(found, total, 16)));
        lore.add(items.progressLine(found, total));
        lore.add(items.remainingLine(found, total));
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

    private int categoryTotal(BlockCategory category) {
        if (category == BlockCategory.ALL) {
            return plugin.progress().totalCount();
        }
        return index().materials(category).size();
    }

    private int categoryFound(BlockCategory category) {
        if (category == BlockCategory.ALL) {
            return plugin.progress().collectedCount();
        }
        int found = 0;
        for (Material material : index().materials(category)) {
            if (plugin.progress().found(material)) {
                found++;
            }
        }
        return found;
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

    private static Map<String, String> pageReplacements(int page, int pages) {
        return Map.of("page", Integer.toString(page + 1), "pages", Integer.toString(pages));
    }

    private CatalogueIndex index() {
        List<Material> blocks = plugin.catalogue().blocks();
        CatalogueIndex current = index;
        if (current == null || current.source() != blocks) {
            current = CatalogueIndex.of(blocks);
            index = current;
        }
        return current;
    }

    /** Rarity/name ordering, category buckets and search keys for one catalogue build. */
    private record CatalogueIndex(List<Material> source, List<Material> sorted,
                                  Map<BlockCategory, List<Material>> byCategory,
                                  Map<Material, String> searchNames) {
        static CatalogueIndex of(List<Material> blocks) {
            record Entry(Material material, BlockRarity rarity, String name) {
            }
            List<Entry> entries = new ArrayList<>(blocks.size());
            for (Material material : blocks) {
                entries.add(new Entry(material, BlockRarity.of(material), Text.pretty(material.name())));
            }
            entries.sort(Comparator.comparing(Entry::rarity).thenComparing(Entry::name));
            List<Material> sorted = new ArrayList<>(entries.size());
            Map<BlockCategory, List<Material>> byCategory = new EnumMap<>(BlockCategory.class);
            Map<Material, String> searchNames = new EnumMap<>(Material.class);
            for (Entry entry : entries) {
                sorted.add(entry.material());
                byCategory.computeIfAbsent(BlockCategory.of(entry.material()), category -> new ArrayList<>())
                        .add(entry.material());
                searchNames.put(entry.material(), entry.name().toLowerCase(Locale.ROOT));
            }
            byCategory.replaceAll((category, materials) -> List.copyOf(materials));
            return new CatalogueIndex(blocks, List.copyOf(sorted), byCategory, searchNames);
        }

        List<Material> materials(BlockCategory category) {
            return category == BlockCategory.ALL ? sorted : byCategory.getOrDefault(category, List.of());
        }
    }
}
