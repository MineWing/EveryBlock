package dev.everyblock.command;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.gui.BlockFilter;
import dev.everyblock.listener.InventoryTracker;
import dev.everyblock.model.Contributor;
import dev.everyblock.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlocksCommand implements CommandExecutor, TabCompleter {
    private final EveryBlockPlugin plugin;

    public BlocksCommand(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("everyblock.use")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            openCategories(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> help(sender);
            case "status" -> status(sender);
            case "gui", "open" -> args.length < 2
                    ? openCategories(sender) : open(sender, filter(args), "");
            case "search", "find" -> search(sender, args);
            case "top", "leaderboard" -> top(sender);
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        };
    }

    private boolean help(CommandSender sender) {
        plugin.messages().send(sender, "help");
        if (sender.hasPermission("everyblock.admin")) {
            plugin.messages().send(sender, "admin-help");
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        int collected = plugin.progress().collectedCount();
        int total = plugin.progress().totalCount();
        plugin.messages().send(sender, "status", Map.of(
                "collected", Integer.toString(collected),
                "total", Integer.toString(total),
                "remaining", Integer.toString(Math.max(0, total - collected)),
                "percent", Text.percent(collected, total)
        ));
        return true;
    }

    private boolean open(CommandSender sender, BlockFilter filter, String query) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        plugin.gui().open(player, 0, filter, query);
        return true;
    }

    private boolean openCategories(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        plugin.gui().openCategories(player);
        return true;
    }

    private boolean search(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return help(sender);
        }
        return open(sender, BlockFilter.ALL, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private boolean top(CommandSender sender) {
        List<Contributor> contributors = plugin.progress().contributors();
        if (contributors.isEmpty()) {
            plugin.messages().send(sender, "top-empty");
            return true;
        }
        plugin.messages().send(sender, "top-header");
        for (int index = 0; index < Math.min(10, contributors.size()); index++) {
            Contributor contributor = contributors.get(index);
            plugin.messages().send(sender, "top-row", Map.of(
                    "position", Integer.toString(index + 1),
                    "player", Text.escape(contributor.playerName()),
                    "amount", Integer.toString(contributor.amount())
            ));
        }
        return true;
    }

    private boolean add(CommandSender sender, String[] args) {
        if (!admin(sender) || args.length < 2) {
            return true;
        }
        Material material = material(args);
        if (material == null) {
            unknown(sender, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }
        plugin.progress().add(material, sender instanceof Player player
                        ? player.getUniqueId() : EveryBlockPlugin.CONSOLE_UUID, sender.getName())
                .whenComplete((added, failure) -> {
                    if (failure != null) {
                        databaseError(sender, failure);
                        return;
                    }
                    plugin.messages().send(sender, added ? "added" : "already-found",
                            Map.of("block", InventoryTracker.display(material)));
                    plugin.gui().refreshOpenMenus();
                });
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (!admin(sender) || args.length < 2) {
            return true;
        }
        Material material = material(args);
        if (material == null) {
            unknown(sender, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }
        plugin.progress().remove(material).whenComplete((removed, failure) -> {
            if (failure != null) {
                databaseError(sender, failure);
                return;
            }
            plugin.messages().send(sender, removed ? "removed" : "not-found",
                    Map.of("block", InventoryTracker.display(material)));
            plugin.gui().refreshOpenMenus();
        });
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            plugin.messages().send(sender, "reset-warning");
            return true;
        }
        plugin.progress().reset().whenComplete((ignored, failure) -> {
            if (failure != null) {
                databaseError(sender, failure);
                return;
            }
            plugin.messages().sendAll("reset-complete", Map.of("player", Text.escape(sender.getName())));
            plugin.gui().refreshOpenMenus();
        });
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!admin(sender)) {
            return true;
        }
        if (plugin.reloadPlugin()) {
            plugin.messages().send(sender, "reloaded",
                    Map.of("total", Integer.toString(plugin.progress().totalCount())));
        } else {
            plugin.messages().send(sender, "reload-failed");
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("everyblock.admin")) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private Material material(String[] args) {
        return plugin.catalogue().match(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private void unknown(CommandSender sender, String input) {
        plugin.messages().send(sender, "unknown-block", Map.of("block", Text.escape(input)));
    }

    private void databaseError(CommandSender sender, Throwable exception) {
        plugin.getLogger().severe("Could not update block progress: " + exception.getMessage());
        plugin.messages().send(sender, "database-error");
    }

    private static BlockFilter filter(String[] args) {
        if (args.length < 2) {
            return BlockFilter.ALL;
        }
        try {
            return BlockFilter.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BlockFilter.ALL;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("help", "status", "gui", "search", "top"));
            if (sender.hasPermission("everyblock.admin")) {
                values.addAll(List.of("add", "remove", "reset", "reload"));
            }
            return matching(values, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gui")) {
            return matching(List.of("all", "missing", "found"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            return matching(plugin.catalogue().blocks().stream()
                    .filter(material -> args[0].equalsIgnoreCase("add") != plugin.progress().found(material))
                    .map(material -> material.name().toLowerCase(Locale.ROOT)).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return matching(List.of("confirm"), args[1]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
