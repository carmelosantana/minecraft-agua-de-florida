package org.xpfarm.aguadeflorida.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;
import org.xpfarm.aguadeflorida.utils.AguaItemBuilder;
import org.xpfarm.aguadeflorida.utils.ConfigManager;

import java.util.List;

/**
 * Reports a save that vanilla already performed. Observation only.
 *
 * <p>Since MC 1.21.2 the resurrection is driven by the {@code minecraft:death_protection}
 * data component that {@link AguaItemBuilder} puts on the item, so the server performs it.
 * This listener exists purely so the save can be logged ({@code debug.log_saves}) and
 * announced to the player ({@code messages.life_saved}). It must never perform a
 * resurrection and must never cancel the event: there is exactly one resurrection path
 * and it is not this one.</p>
 *
 * <p>Nothing is load-bearing here. It is not confirmed that {@link EntityResurrectEvent}
 * fires at all for a custom death-protection item, nor that {@link
 * EntityResurrectEvent#getHand()} is populated when it does — that is an open question for
 * runtime verification. If the event never arrives, the item still saves players and only
 * the logging and messaging are absent.</p>
 */
public class AguaResurrectListener implements Listener {

    /** Used when messages.life_saved is absent from config. */
    static final String DEFAULT_LIFE_SAVED =
        "§eThe Agua de Florida has cleansed your spirit and granted you a second chance!";

    private final AguaDeFloridaPlugin plugin;
    private final ConfigManager configManager;
    private final AguaItemBuilder itemBuilder;

    public AguaResurrectListener(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.itemBuilder = plugin.getItemBuilder();
    }

    /**
     * MONITOR because this only observes an outcome: by this priority the decision is
     * final, and nothing here may influence it. ignoreCancelled so a resurrection some
     * other plugin cancelled is never reported to the player as a save that happened.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        // Mobs can hold a totem too. Only players get logged or messaged.
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // A vanilla Totem of Undying resurrection must not print the Agua save message,
        // so the item that did the saving has to be identified before anything is sent.
        if (findAguaItem(player, event.getHand()) == null) {
            return;
        }

        if (configManager.isLogSaves()) {
            plugin.getLogger().info("Agua de Florida saved " + player.getName());
        }

        String message = configManager.getMessage("life_saved", DEFAULT_LIFE_SAVED);
        if (!message.isEmpty()) {
            // The configured message carries legacy section codes, so it has to be
            // deserialized. Sending the raw string prints the codes literally.
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
        }
    }

    /**
     * Find the Agua de Florida item responsible for a resurrection.
     *
     * @param player The resurrected player
     * @param hand The hand reported by the event, may be null
     * @return The matching item, or null when none of the inspected slots hold one
     */
    private ItemStack findAguaItem(Player player, EquipmentSlot hand) {
        for (EquipmentSlot slot : slotsToInspect(hand)) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (itemBuilder.isAguaDeFloridaItem(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Decide which slots to look in for the saving item.
     *
     * <p>{@link EntityResurrectEvent} has a one-argument constructor that leaves the hand
     * unset, so {@code getHand()} is genuinely nullable and no hand may be assumed. With no
     * hand reported, both hands are inspected — the same pair vanilla's own death-protection
     * check scans. Package-private so the null case can be tested without a live server.</p>
     *
     * @param hand The hand reported by the event, may be null
     * @return The slots to inspect, in priority order, never empty
     */
    static List<EquipmentSlot> slotsToInspect(EquipmentSlot hand) {
        if (hand == null) {
            return List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);
        }
        return List.of(hand);
    }
}
