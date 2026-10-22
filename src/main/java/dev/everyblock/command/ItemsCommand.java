package dev.everyblock.command;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.gui.BlockFilter;
import dev.everyblock.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemsCommand implements CommandExecutor, TabCompleter {
    private final EveryBlockPlugin plugin;

    public ItemsCommand(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        // Resetting is an admin action: it needs only everyblock.admin and works while items are disabled.
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            return reset(sender, args);
        }
        if (!sender.hasPermission("everyblock.items")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!plugin.settings().itemsEnabled()) {
            plugin.messages().send(sender, "items-disabled");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            int found = plugin.itemProgress().collectedCount();
            int total = plugin.itemProgress().totalCount();
            plugin.messages().send(sender, "items-status", Map.of(
                    "collected", Integer.toString(found),
                    "total", Integer.toString(total),
                    "remaining", Integer.toString(Math.max(0, total - found)),
                    "percent", Text.percent(found, total)));
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        plugin.itemGui().open(player, 0, BlockFilter.ALL);
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("everyblock.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            plugin.messages().send(sender, "items-reset-warning");
            return true;
        }
        plugin.itemProgress().reset().whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("Could not reset item progress: " + ProgressDatabase.describe(failure));
                plugin.messages().send(sender, "database-error");
                return;
            }
            plugin.itemGui().refreshOpenMenus();
            plugin.messages().sendAll("items-reset", Map.of("player", Text.escape(sender.getName())));
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return matching(sender.hasPermission("everyblock.admin")
                    ? List.of("status", "reset") : List.of("status"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")
                && sender.hasPermission("everyblock.admin")) {
            return matching(List.of("confirm"), args[1]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }
}
