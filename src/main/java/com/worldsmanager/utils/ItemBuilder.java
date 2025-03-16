package com.worldsmanager.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A utility class to create ItemStack objects with ease
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    /**
     * Create a new ItemBuilder with the specified material
     *
     * @param material The material to use
     */
    public ItemBuilder(Material material) {
        this(material, 1);
    }

    /**
     * Create a new ItemBuilder with the specified material and amount
     *
     * @param material The material to use
     * @param amount The amount of items
     */
    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    /**
     * Create a new ItemBuilder from an existing ItemStack
     *
     * @param item The ItemStack to copy
     */
    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    /**
     * Set the display name of the item
     *
     * @param name The name to set
     * @return This ItemBuilder
     */
    public ItemBuilder name(String name) {
        meta.setDisplayName(name);
        return this;
    }

    /**
     * Set the lore of the item
     *
     * @param lore The lore to set
     * @return This ItemBuilder
     */
    public ItemBuilder lore(String... lore) {
        return lore(Arrays.asList(lore));
    }

    /**
     * Set the lore of the item
     *
     * @param lore The lore to set
     * @return This ItemBuilder
     */
    public ItemBuilder lore(List<String> lore) {
        meta.setLore(lore);
        return this;
    }

    /**
     * Add lines to the item's lore
     *
     * @param lines The lines to add
     * @return This ItemBuilder
     */
    public ItemBuilder addLore(String... lines) {
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        lore.addAll(Arrays.asList(lines));
        meta.setLore(lore);
        return this;
    }

    /**
     * Add an enchantment to the item
     *
     * @param enchantment The enchantment to add
     * @param level The level of the enchantment
     * @return This ItemBuilder
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    /**
     * Add an enchantment glow to the item (without an actual enchantment)
     *
     * @return This ItemBuilder
     */
    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    /**
     * Add item flags to the item
     *
     * @param flags The flags to add
     * @return This ItemBuilder
     */
    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    /**
     * Hide all attributes from the item
     *
     * @return This ItemBuilder
     */
    public ItemBuilder hideAllAttributes() {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_POTION_EFFECTS,
                ItemFlag.HIDE_UNBREAKABLE
        );
        return this;
    }

    /**
     * Set the unbreakable state of the item
     *
     * @param unbreakable Whether the item should be unbreakable
     * @return This ItemBuilder
     */
    public ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    /**
     * Set the custom model data of the item
     *
     * @param data The custom model data
     * @return This ItemBuilder
     */
    public ItemBuilder modelData(int data) {
        meta.setCustomModelData(data);
        return this;
    }

    /**
     * Build the ItemStack
     *
     * @return The resulting ItemStack
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Utility method to color a string with the color code character
     *
     * @param text The text to color
     * @return The colored text
     */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}