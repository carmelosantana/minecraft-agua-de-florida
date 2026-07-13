package org.xpfarm.aguadeflorida;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

import org.xpfarm.aguadeflorida.listeners.PlayerDeathListener;
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
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new MobDeathListener(this), this);
        
        // Register commands
        getCommand("aguadeflorida").setExecutor(new AguaCommand(this));
        
        // Initialize recipe if enabled
        if (configManager.isRecipeEnabled()) {
            itemBuilder.registerRecipe();
        }
        
        logger.info("Agua de Florida v" + getDescription().getVersion() + " enabled!");
        logger.info("Spiritual cleansing activated - may your journeys be blessed.");
    }
    
    @Override
    public void onDisable() {
        // Clean up recipes
        if (itemBuilder != null) {
            itemBuilder.unregisterRecipe();
        }
        
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
        
        // Update recipe based on new config
        if (configManager.isRecipeEnabled()) {
            itemBuilder.registerRecipe();
        } else {
            itemBuilder.unregisterRecipe();
        }
        
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
