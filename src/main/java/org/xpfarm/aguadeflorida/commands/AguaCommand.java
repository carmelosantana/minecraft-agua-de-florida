package org.xpfarm.aguadeflorida.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;
import org.xpfarm.aguadeflorida.utils.AguaItemBuilder;
import org.xpfarm.aguadeflorida.utils.ConfigManager;
import org.xpfarm.aguadeflorida.utils.PlayerLookup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Handles all Agua de Florida commands
 */
public class AguaCommand implements CommandExecutor, TabCompleter {
    
    private final AguaDeFloridaPlugin plugin;
    private final ConfigManager configManager;
    private final AguaItemBuilder itemBuilder;

    public AguaCommand(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.itemBuilder = plugin.getItemBuilder();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "give":
                return handleGiveCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "help":
                showHelp(sender);
                return true;
            default:
                sender.sendMessage(Component.text("Unknown subcommand: " + subCommand, NamedTextColor.RED));
                showHelp(sender);
                return true;
        }
    }
    
    /**
     * Handle the give subcommand
     */
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aguadeflorida.give")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        Player target;
        int amount = 1;
        
        if (args.length >= 2) {
            // Target player specified
            String targetName = args[1];
            // Exact match only: prefix matching could resolve "Car" to "Carmelo123".
            // PlayerLookup additionally tries the Floodgate "." prefix that Bedrock
            // accounts carry on their Java-side username.
            target = PlayerLookup.resolve(targetName).orElse(null);
            if (target == null) {
                sender.sendMessage(Component.text(
                        PlayerLookup.noSuchPlayerMessage(targetName, PlayerLookup.onlineNames()),
                        NamedTextColor.RED));
                return true;
            }
        } else {
            // Give to sender (must be a player)
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("You must specify a player when using this command from console.", NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
        }
        
        if (args.length >= 3) {
            // Amount specified
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) {
                    sender.sendMessage(Component.text("Amount must be a positive number.", NamedTextColor.RED));
                    return true;
                }
                if (amount > 64) {
                    sender.sendMessage(Component.text("Amount cannot exceed 64.", NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[2], NamedTextColor.RED));
                return true;
            }
        }
        
        // Give the item(s). The item may have a max stack size of 1, so the total
        // has to be handed out one stack at a time rather than via setAmount().
        ItemStack aguaItem = itemBuilder.getCachedItem();
        boolean dropped = false;

        for (int stackSize : splitIntoStacks(amount, aguaItem.getMaxStackSize())) {
            ItemStack stack = aguaItem.clone();
            stack.setAmount(stackSize);

            // addItem returns whatever did not fit; drop it instead of losing it
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                dropped = true;
            }
        }

        if (dropped) {
            target.sendMessage(Component.text("Your inventory is full! Agua de Florida dropped at your feet.", NamedTextColor.YELLOW));
        }

        // Notify participants. The configured name is legacy-coloured, so it has to be
        // deserialized instead of concatenated into a plain Component.text(...)
        Component itemName = LegacyComponentSerializer.legacySection().deserialize(configManager.getItemName());
        target.sendMessage(Component.text("You have received " + amount + "x ", NamedTextColor.GREEN)
            .append(itemName)
            .append(Component.text("!", NamedTextColor.GREEN)));

        if (!sender.equals(target)) {
            sender.sendMessage(Component.text("Gave " + amount + "x ", NamedTextColor.GREEN)
                .append(itemName)
                .append(Component.text(" to " + target.getName(), NamedTextColor.GREEN)));
        }

        plugin.debugLog("Gave " + amount + "x Agua de Florida to " + target.getName() + " (by " + sender.getName() + ")");
        return true;
    }
    
    /**
     * Split a total amount into per-iteration stack sizes, none larger than maxStackSize.
     * Non-positive amounts produce no stacks. Package-private so it can be unit tested
     * without a live server.
     */
    static List<Integer> splitIntoStacks(int amount, int maxStackSize) {
        List<Integer> stacks = new ArrayList<>();

        if (amount <= 0) {
            return stacks;
        }

        // Guard against a nonsensical max stack size so this can never loop forever
        int perStack = Math.max(1, maxStackSize);
        int remaining = amount;

        while (remaining > 0) {
            int stackSize = Math.min(remaining, perStack);
            stacks.add(stackSize);
            remaining -= stackSize;
        }

        return stacks;
    }

    /**
     * Handle the reload subcommand
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("aguadeflorida.reload")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        try {
            plugin.reloadPluginConfig();
            sender.sendMessage(Component.text("Agua de Florida configuration reloaded successfully!", NamedTextColor.GREEN));
            plugin.debugLog("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error reloading configuration: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Error reloading configuration: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Show help information
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Agua de Florida Commands ===", NamedTextColor.GOLD));
        
        if (sender.hasPermission("aguadeflorida.give")) {
            sender.sendMessage(Component.text("/aguadeflorida give [player] [amount]", NamedTextColor.YELLOW)
                .append(Component.text(" - Give Agua de Florida item", NamedTextColor.WHITE)));
        }
        
        if (sender.hasPermission("aguadeflorida.reload")) {
            sender.sendMessage(Component.text("/aguadeflorida reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload plugin configuration", NamedTextColor.WHITE)));
        }
        
        sender.sendMessage(Component.text("/aguadeflorida help", NamedTextColor.YELLOW)
            .append(Component.text(" - Show this help message", NamedTextColor.WHITE)));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument - subcommands
            List<String> availableCommands = new ArrayList<>();
            
            if (sender.hasPermission("aguadeflorida.give")) {
                availableCommands.add("give");
            }
            if (sender.hasPermission("aguadeflorida.reload")) {
                availableCommands.add("reload");
            }
            availableCommands.add("help");
            
            StringUtil.copyPartialMatches(args[0], availableCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Second argument for give command - player names
            if (sender.hasPermission("aguadeflorida.give")) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Third argument for give command - amount suggestions
            if (sender.hasPermission("aguadeflorida.give")) {
                List<String> amounts = Arrays.asList("1", "5", "10", "16", "32", "64");
                StringUtil.copyPartialMatches(args[2], amounts, completions);
            }
        }
        
        Collections.sort(completions);
        return completions;
    }
}
