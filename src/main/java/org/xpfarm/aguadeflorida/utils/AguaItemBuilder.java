package org.xpfarm.aguadeflorida.utils;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DeathProtection;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the Agua de Florida item and recognises it again when it comes back.
 *
 * <p>The item is a {@link Material#POTION} carrying the vanilla
 * {@code minecraft:death_protection} data component, so the resurrection is performed
 * by the server rather than emulated by this plugin. The {@code minecraft:consumable}
 * component is removed, which is what makes right-click completely inert: a plain
 * potion's only built-in right-click behaviour is drinking, and drinking is gated on
 * that component.</p>
 */
public class AguaItemBuilder {

    /**
     * The item material, deliberately not configurable.
     *
     * <p>POTION is load-bearing rather than cosmetic. The 1.x item was a WATER_BUCKET,
     * which placed a water source and turned into an empty bucket on right-click, silently
     * destroying the player's save. Letting an admin configure the material back to a
     * bucket would reinstate exactly that defect.</p>
     */
    public static final Material ITEM_MATERIAL = Material.POTION;

    /** Sound played by the death-protection component when the item saves a player. */
    static final Key GLASS_BREAK_SOUND = Key.key("minecraft", "block.glass.break");

    /** Probability with which the death-protection status effects are applied. */
    static final float EFFECT_PROBABILITY = 1.0f;

    private final AguaDeFloridaPlugin plugin;
    private final NamespacedKey itemKey;
    private ItemStack cachedItem;

    public AguaItemBuilder(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "agua_de_florida");
        updateCachedItem();
    }

    /**
     * One entry in the death-protection effect list.
     *
     * <p>This mirrors the {@link ConsumeEffect} factories, which cannot be called off a
     * running server: every one of them delegates to a server-provided bridge. Planning the
     * list as plain values keeps the ordering and contents testable, and leaves only the
     * one-to-one translation in {@link #toConsumeEffects(List)} needing a live server.</p>
     */
    sealed interface DeathEffect {
        /** Play a sound at the saved player. */
        record PlaySound(Key sound) implements DeathEffect { }

        /** Apply the configured status effects with the given probability. */
        record ApplyStatusEffects(List<PotionEffect> effects, float probability) implements DeathEffect { }

        /** Wipe existing status effects, matching a vanilla Totem of Undying. */
        record ClearAllStatusEffects() implements DeathEffect { }
    }

    /**
     * Create a new Agua de Florida item based on current configuration
     * @return ItemStack representing Agua de Florida
     */
    public ItemStack createAguaDeFloridaItem() {
        ConfigManager config = plugin.getConfigManager();

        ItemStack item = new ItemStack(ITEM_MATERIAL);

        item.editMeta(meta -> {
            // Set display name (italics off so the config formatting shows as written)
            Component displayName = LegacyComponentSerializer.legacySection()
                .deserialize(config.getItemName())
                .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);

            // Set lore
            List<Component> lore = new ArrayList<>();
            for (String loreLine : config.getItemLore()) {
                Component component = LegacyComponentSerializer.legacySection()
                    .deserialize(loreLine)
                    .decoration(TextDecoration.ITALIC, false);
                lore.add(component);
            }
            meta.lore(lore);

            // Add enchantment glow if configured
            if (config.isItemEnchanted()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // A POTION otherwise renders vanilla potion tooltip lines ("No effects") under
            // the configured lore. HIDE_POTION_EFFECTS does not exist in this API version.
            //
            // HIDE_ADDITIONAL_TOOLTIP is deprecated in favour of the TOOLTIP_DISPLAY data
            // component, but it is kept deliberately: the glow above already hides its
            // enchantment through ItemFlag, and setting TOOLTIP_DISPLAY writes that whole
            // component at once, which would drop the HIDE_ENCHANTS flag mapped onto it.
            // Both tooltip decisions stay on one mechanism until they move together.
            @SuppressWarnings("deprecation")
            ItemFlag hideAdditionalTooltip = ItemFlag.HIDE_ADDITIONAL_TOOLTIP;
            meta.addItemFlags(hideAdditionalTooltip);

            // Add custom data to identify this as Agua de Florida
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BOOLEAN, true);
        });

        // Tint the liquid. Only the custom colour is set: no potion type is declared, so
        // the item brews no vanilla effect of its own.
        item.setData(DataComponentTypes.POTION_CONTENTS, PotionContents.potionContents()
            .customColor(resolveColor(config.getItemColor())));

        // Vanilla performs the resurrection; the plugin only declares what it should do.
        item.setData(DataComponentTypes.DEATH_PROTECTION,
            DeathProtection.deathProtection(toConsumeEffects(planDeathEffects(config.getEffects()))));

        // Last, so nothing set above can leave a consumable behind. unsetData, never
        // resetData: resetData restores the POTION prototype's consumable and would make
        // the item drinkable again, which is the whole behaviour being removed.
        item.unsetData(DataComponentTypes.CONSUMABLE);

        // Stack size is left at the vanilla POTION default of 1, matching a Totem of Undying.

        return item;
    }

    /**
     * Describe the effects the death-protection component should declare.
     *
     * @param configuredEffects The effects from totem.effects.*, may be null or empty
     * @return The planned effects, in the order they are declared on the component
     */
    static List<DeathEffect> planDeathEffects(List<PotionEffect> configuredEffects) {
        List<DeathEffect> planned = new ArrayList<>();

        planned.add(new DeathEffect.PlaySound(GLASS_BREAK_SOUND));

        // An empty apply-effects entry is not declared at all: an admin who zeroes out
        // every effect should get a clean component, not one carrying an empty list.
        if (configuredEffects != null && !configuredEffects.isEmpty()) {
            // Snapshot, so a later config reload cannot mutate an already-built item.
            // Deliberately not List.copyOf: this layer only carries the list through and
            // has no reason to impose a no-nulls contract of its own on the config.
            planned.add(new DeathEffect.ApplyStatusEffects(
                Collections.unmodifiableList(new ArrayList<>(configuredEffects)), EFFECT_PROBABILITY));
        }

        // Matches a vanilla Totem of Undying, which wipes existing effects before applying its own.
        planned.add(new DeathEffect.ClearAllStatusEffects());

        return planned;
    }

    /**
     * Translate a planned effect list into the Paper API effects.
     *
     * <p>Every {@link ConsumeEffect} factory resolves through a server-provided bridge, so
     * this method only works on a running server.</p>
     *
     * @param planned The planned effects
     * @return The Paper consume effects, in the same order
     */
    private static List<ConsumeEffect> toConsumeEffects(List<DeathEffect> planned) {
        List<ConsumeEffect> effects = new ArrayList<>(planned.size());
        for (DeathEffect effect : planned) {
            switch (effect) {
                case DeathEffect.PlaySound playSound ->
                    effects.add(ConsumeEffect.playSoundConsumeEffect(playSound.sound()));
                case DeathEffect.ApplyStatusEffects apply ->
                    effects.add(ConsumeEffect.applyStatusEffects(apply.effects(), apply.probability()));
                case DeathEffect.ClearAllStatusEffects ignored ->
                    effects.add(ConsumeEffect.clearAllStatusEffects());
            }
        }
        return effects;
    }

    /**
     * Resolve the potion tint, falling back rather than passing null to the API.
     *
     * <p>{@link ConfigManager#getItemColor()} already falls back on a malformed value, so
     * this only guards a manager that was never loaded.</p>
     *
     * @param configured The configured colour, may be null
     * @return A usable colour, never null
     */
    static Color resolveColor(Color configured) {
        return configured == null ? ConfigManager.defaultItemColor() : configured;
    }

    /**
     * Check if an ItemStack is Agua de Florida
     * @param item The item to check
     * @return true if the item is Agua de Florida
     */
    public boolean isAguaDeFloridaItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        return isGenuineItem(
            item.getType(),
            item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BOOLEAN));
    }

    /**
     * Decide whether a material and tag value together identify a genuine item.
     *
     * <p>The material check is what makes 2.0.0 a hard break: a 1.x WATER_BUCKET item still
     * carries the tag but is no longer recognised, and no conversion code ships. Existing
     * items are re-issued manually.</p>
     *
     * @param material The item material, may be null
     * @param tagValue The value read from the container, may be null when absent
     * @return true only when the item is a POTION and the tag is present and true
     */
    static boolean isGenuineItem(Material material, Boolean tagValue) {
        return material == ITEM_MATERIAL && isTagPresentAndTrue(tagValue);
    }

    /**
     * Evaluate a persistent data tag value as a genuine-item marker
     * @param tagValue The value read from the container, may be null when absent
     * @return true only when the tag is present and true
     */
    static boolean isTagPresentAndTrue(Boolean tagValue) {
        return Boolean.TRUE.equals(tagValue);
    }

    /**
     * Verify that the death-protection data component actually applied to a freshly
     * built item.
     *
     * <p>The data component API is {@code @ApiStatus.Experimental} and Paper makes no
     * cross-version compatibility promise, so a future server could accept the call and
     * drop the component. That would degrade silently into an item that no longer saves
     * anyone. Callers are expected to run this at enable time and refuse to start rather
     * than ship that degradation.</p>
     *
     * @return true if the built item carries {@code minecraft:death_protection}
     * @throws IllegalStateException if the item could not be built at all
     */
    public boolean verifyDeathProtectionApplied() {
        ItemStack probe;
        try {
            probe = createAguaDeFloridaItem();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Could not build the Agua de Florida item to verify death protection: " + e.getMessage(), e);
        }
        return probe.hasData(DataComponentTypes.DEATH_PROTECTION);
    }

    /**
     * Get a cached Agua de Florida item for performance
     * @return Cached ItemStack
     */
    public ItemStack getCachedItem() {
        if (cachedItem == null) {
            updateCachedItem();
        }
        return cachedItem.clone();
    }

    /**
     * Update the cached item when configuration changes
     */
    public void updateCachedItem() {
        cachedItem = createAguaDeFloridaItem();
    }
}
