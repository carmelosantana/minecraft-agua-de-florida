package org.xpfarm.aguadeflorida;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

import org.xpfarm.aguadeflorida.listeners.AguaResurrectListener;
import org.xpfarm.aguadeflorida.listeners.MobDeathListener;
import org.xpfarm.aguadeflorida.commands.AguaCommand;
import org.xpfarm.aguadeflorida.utils.AguaItemBuilder;
import org.xpfarm.aguadeflorida.utils.ConfigManager;

/**
 * Agua de Florida Plugin Main Class
 * Provides totem-like spiritual cleansing functionality
 */
public class AguaDeFloridaPlugin extends JavaPlugin {
    
    private static AguaDeFloridaPlugin instance;
    private ConfigManager configManager;
    private AguaItemBuilder itemBuilder;
    private Logger logger;
    
    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        
        // Initialize configuration manager
        configManager = new ConfigManager(this);
        
        // Load configuration first
        saveDefaultConfig();
        configManager.loadConfig();
        
        // Initialize item builder after config is loaded
        itemBuilder = new AguaItemBuilder(this);

        // The whole plugin rests on vanilla applying minecraft:death_protection. Prove it
        // did before serving a single item, and refuse to run if it did not.
        if (!verifyDeathProtection()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register event listeners. Resurrection itself is vanilla's; the resurrect
        // listener only observes it for logging and messaging.
        getServer().getPluginManager().registerEvents(new AguaResurrectListener(this), this);
        getServer().getPluginManager().registerEvents(new MobDeathListener(this), this);

        // Register commands
        PluginCommand aguaCommand = getCommand("aguadeflorida");
        if (aguaCommand != null) {
            aguaCommand.setExecutor(new AguaCommand(this));
        } else {
            logger.severe("Command 'aguadeflorida' is missing from plugin.yml - commands will not work. " +
                          "Restore the 'commands:' block in plugin.yml and restart the server.");
        }
        
        logger.info("Agua de Florida v" + getDescription().getVersion() + " enabled!");
        logger.info("Spiritual cleansing activated - may your journeys be blessed.");
    }
    
    /**
     * Confirm the death-protection data component actually applied to the built item.
     *
     * <p>Deliberately fail-loudly: the component is the only thing that saves a player, the
     * data component API is experimental, and Paper promises no cross-version compatibility.
     * An item that silently stopped saving anyone is far worse than a plugin that refuses to
     * start, so there is no fallback path here and no emulated resurrection to degrade into.</p>
     *
     * @return true when the component applied and the plugin may continue enabling
     */
    private boolean verifyDeathProtection() {
        boolean applied;
        try {
            applied = itemBuilder.verifyDeathProtectionApplied();
        } catch (IllegalStateException e) {
            // The item could not be built at all - a different failure from the component
            // being dropped, and the cause is the only thing that explains it.
            logger.log(java.util.logging.Level.SEVERE,
                "Agua de Florida could not build its item, so DEATH_PROTECTION could not be verified. "
                + "The item would not save players. Disabling the plugin.", e);
            return false;
        }

        if (!applied) {
            logger.severe("Agua de Florida could not apply the minecraft:death_protection data component "
                + "to its item. This server's Paper build accepted the call and dropped the component, "
                + "so the item would NOT save players from death. Disabling the plugin rather than "
                + "serving an item that silently does nothing.");
            return false;
        }

        return true;
    }

    @Override
    public void onDisable() {
        logger.info("Agua de Florida disabled. Until we meet again...");
    }
    
    /**
     * Get the plugin instance
     * @return The plugin instance
     */
    public static AguaDeFloridaPlugin getInstance() {
        return instance;
    }
    
    /**
     * Get the config manager
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Get the item builder
     * @return The item builder
     */
    public AguaItemBuilder getItemBuilder() {
        return itemBuilder;
    }
    
    /**
     * Reload the plugin configuration
     */
    public void reloadPluginConfig() {
        reloadConfig();
        configManager.loadConfig();

        // Rebuild the cached item so name, lore, material and enchant changes take effect
        itemBuilder.updateCachedItem();

        logger.info("Configuration reloaded!");
    }
    
    /**
     * Log a debug message if debug is enabled
     * @param message The message to log
     */
    public void debugLog(String message) {
        if (configManager.isDebugEnabled()) {
            logger.info("[DEBUG] " + message);
        }
    }
    
    /**
     * Send a message to console and optionally to a player
     * @param message The message to send
     * @param player Optional player to send the message to
     */
    public void sendMessage(String message, Player player) {
        logger.info(message);
        if (player != null) {
            player.sendMessage(Component.text(message, NamedTextColor.GREEN));
        }
    }
}
