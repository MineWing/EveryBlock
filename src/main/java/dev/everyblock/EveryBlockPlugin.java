package dev.everyblock;

import dev.everyblock.command.BlocksCommand;
import dev.everyblock.config.ConfigService;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.gui.BlockCategory;
import dev.everyblock.gui.BlockRarity;
import dev.everyblock.gui.GuiService;
import dev.everyblock.gui.ItemGuiService;
import dev.everyblock.hook.EveryBlockExpansion;
import dev.everyblock.listener.GuiListener;
import dev.everyblock.listener.InventoryTracker;
import dev.everyblock.message.MessageService;
import dev.everyblock.service.BlockCatalogue;
import dev.everyblock.service.ItemCatalogue;
import dev.everyblock.service.ItemProgressService;
import dev.everyblock.service.ProgressService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class EveryBlockPlugin extends JavaPlugin {
    public static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    private ConfigService configService;
    private MessageService messages;
    private BlockCatalogue catalogue;
    private ItemCatalogue itemCatalogue;
    private ProgressDatabase database;
    private ProgressService progress;
    private ItemProgressService itemProgress;
    private GuiService gui;
    private ItemGuiService itemGui;
    private InventoryTracker tracker;

    @Override
    public void onEnable() {
        try {
            configService = new ConfigService(this);
            configService.load();
            messages = new MessageService(this);
            messages.load();
            BlockRarity.configure(messages);
            BlockCategory.configure(messages);
            dev.everyblock.util.Text.configureAccent(
                    messages.string("gui.progress-bar.filled", java.util.Map.of(), "#f7a48d"));
            catalogue = new BlockCatalogue(this);
            catalogue.rebuild(settings());
            itemCatalogue = new ItemCatalogue(this);
            itemCatalogue.rebuild(settings());

            database = new ProgressDatabase(settings().databasePath());
            database.open();
            progress = new ProgressService(database, catalogue, database.load());
            itemProgress = new ItemProgressService(database, itemCatalogue, database.loadItems());
            gui = new GuiService(this);
            itemGui = new ItemGuiService(this);
            tracker = new InventoryTracker(this);

            getServer().getPluginManager().registerEvents(tracker, this);
            getServer().getPluginManager().registerEvents(new GuiListener(this), this);
            PluginCommand blocks = java.util.Objects.requireNonNull(getCommand("blocks"));
            BlocksCommand command = new BlocksCommand(this);
            blocks.setExecutor(command);
            blocks.setTabCompleter(command);
            PluginCommand items = java.util.Objects.requireNonNull(getCommand("items"));
            dev.everyblock.command.ItemsCommand itemsCommand = new dev.everyblock.command.ItemsCommand(this);
            items.setExecutor(itemsCommand);
            items.setTabCompleter(itemsCommand);

            tracker.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                new EveryBlockExpansion(this).register();
                getLogger().info("PlaceholderAPI expansion registered.");
            }
            getLogger().info("EveryBlock enabled: " + progress.collectedCount() + "/"
                    + progress.totalCount() + " blocks collected.");
        } catch (Exception exception) {
            getLogger().severe("EveryBlock could not start: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.stop();
        }
        if (database != null) {
            try {
                database.close();
            } catch (Exception exception) {
                getLogger().warning("Could not close progress database: " + exception.getMessage());
            }
        }
    }

    public boolean reloadPlugin() {
        try {
            java.nio.file.Path originalDatabase = settings().databasePath();
            configService.load();
            if (!settings().databasePath().equals(originalDatabase)) {
                getLogger().warning("storage.database-file changes require a full server restart.");
            }
            messages.load();
            BlockRarity.configure(messages);
            BlockCategory.configure(messages);
            dev.everyblock.util.Text.configureAccent(
                    messages.string("gui.progress-bar.filled", java.util.Map.of(), "#f7a48d"));
            catalogue.rebuild(settings());
            itemCatalogue.rebuild(settings());
            tracker.start();
            gui.refreshOpenMenus();
            return true;
        } catch (Exception exception) {
            getLogger().severe("Reload failed: " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    public PluginSettings settings() {
        return configService.settings();
    }

    public MessageService messages() {
        return messages;
    }

    public BlockCatalogue catalogue() {
        return catalogue;
    }

    public ProgressService progress() {
        return progress;
    }

    public GuiService gui() {
        return gui;
    }

    public ItemCatalogue itemCatalogue() {
        return itemCatalogue;
    }

    public ItemProgressService itemProgress() {
        return itemProgress;
    }

    public ItemGuiService itemGui() {
        return itemGui;
    }
}
