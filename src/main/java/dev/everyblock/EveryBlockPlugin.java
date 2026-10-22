package dev.everyblock;

import dev.everyblock.command.BlocksCommand;
import dev.everyblock.command.ItemsCommand;
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
import dev.everyblock.util.Text;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

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
    private EveryBlockExpansion expansion;
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        stopping = false;
        try {
            configService = new ConfigService(this);
            messages = new MessageService(this);
            PluginSettings preparedSettings = configService.prepare();
            var preparedMessages = messages.prepare();
            configService.apply(preparedSettings);
            messages.apply(preparedMessages);
            BlockRarity.configure(messages);
            BlockCategory.configure(messages);
            Text.configureAccent(messages.string("gui.progress-bar.filled", Map.of(), "#f7a48d"));
            catalogue = new BlockCatalogue(this);
            catalogue.rebuild(settings());
            itemCatalogue = new ItemCatalogue(this);
            itemCatalogue.rebuild(settings());

            database = new ProgressDatabase(settings().databasePath(), getLogger());
            database.open();
            progress = new ProgressService(database, catalogue, database.load());
            itemProgress = new ItemProgressService(database, itemCatalogue, database.loadItems());
            database.startWorker(action -> {
                if (!stopping && isEnabled()) {
                    getServer().getScheduler().runTask(this, () -> {
                        if (!stopping && isEnabled()) {
                            action.run();
                        }
                    });
                }
            });
            gui = new GuiService(this);
            itemGui = new ItemGuiService(this);
            tracker = new InventoryTracker(this);

            getServer().getPluginManager().registerEvents(tracker, this);
            getServer().getPluginManager().registerEvents(new GuiListener(this), this);
            getServer().getPluginManager().registerEvents(messages, this);
            PluginCommand blocks = Objects.requireNonNull(getCommand("blocks"));
            BlocksCommand command = new BlocksCommand(this);
            blocks.setExecutor(command);
            blocks.setTabCompleter(command);
            PluginCommand items = Objects.requireNonNull(getCommand("items"));
            ItemsCommand itemsCommand = new ItemsCommand(this);
            items.setExecutor(itemsCommand);
            items.setTabCompleter(itemsCommand);

            tracker.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                expansion = new EveryBlockExpansion(this);
                if (expansion.register()) {
                    getLogger().info("PlaceholderAPI expansion registered.");
                } else {
                    getLogger().warning("Could not register the PlaceholderAPI expansion.");
                    expansion = null;
                }
            }
            getLogger().info("EveryBlock enabled: " + progress.collectedCount() + "/"
                    + progress.totalCount() + " blocks collected.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "EveryBlock could not start", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stopping = true;
        if (tracker != null) {
            tracker.stop();
        }
        if (gui != null) {
            gui.closeOpenMenus();
        }
        if (messages != null) {
            messages.shutdown();
        }
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
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
            Path originalDatabase = settings().databasePath();
            PluginSettings preparedSettings = configService.prepare();
            var preparedMessages = messages.prepare();
            if (!preparedSettings.databasePath().equals(originalDatabase)) {
                getLogger().warning("storage.database-file changes require a full server restart.");
                preparedSettings = preparedSettings.withDatabasePath(originalDatabase);
            }
            configService.apply(preparedSettings);
            messages.apply(preparedMessages);
            BlockRarity.configure(messages);
            BlockCategory.configure(messages);
            Text.configureAccent(messages.string("gui.progress-bar.filled", Map.of(), "#f7a48d"));
            boolean blocksWereComplete = InventoryTracker.complete(progress.collectedCount(), progress.totalCount());
            boolean itemsWereComplete = InventoryTracker.complete(itemProgress.collectedCount(),
                    itemProgress.totalCount());
            catalogue.rebuild(settings());
            itemCatalogue.rebuild(settings());
            tracker.start();
            // A smaller catalogue can complete a challenge without any new discovery.
            tracker.catalogueChanged(blocksWereComplete, itemsWereComplete);
            gui.refreshOpenMenus();
            itemGui.refreshOpenMenus();
            return true;
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Reload failed", exception);
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

    public InventoryTracker tracker() {
        return tracker;
    }
}
