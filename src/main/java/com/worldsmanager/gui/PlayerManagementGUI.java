package com.worldsmanager.gui;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class PlayerManagementGUI {

    private final WorldsManager plugin;
    private final CustomWorld world;
    private final Map<Integer, UUID> playerSlotMap;
    private final Map<Player, String> pendingPlayerAdditions;

    public PlayerManagementGUI(WorldsManager plugin, CustomWorld world) {
        this.plugin = plugin;
        this.world = world;
        this.playerSlotMap = new HashMap<>();
        this.pendingPlayerAdditions = new HashMap<>();
    }

    // Open the player management menu
    public void openMenu(Player player) {
        // Create inventory
        String title = plugin.getLanguageManager().getMessage("gui.players.title", world.getName());
        Inventory inventory = Bukkit.createInventory(null, 54, title);

        // Reset player slot map
        playerSlotMap.clear();

        // Add players to the inventory
        addTrustedPlayers(inventory);

        // Add button to add a new player
        String addPlayerText = plugin.getLanguageManager().getMessage("gui.players.add-player");
        ItemStack addItem = new ItemBuilder(Material.EMERALD)
                .name(ChatColor.translateAlternateColorCodes('&', addPlayerText))
                .build();
        inventory.setItem(49, addItem);

        // Open the inventory
        player.openInventory(inventory);
    }

    // Add trusted players to the inventory
    private void addTrustedPlayers(Inventory inventory) {
        List<UUID> trustedPlayers = world.getTrustedPlayers();

        // Add players to the inventory
        int slot = 0;
        for (UUID playerUUID : trustedPlayers) {
            // Get player name
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            String playerName = offlinePlayer.getName();
            if (playerName == null) {
                playerName = "Unknown Player";
            }

            // Create player head item
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(offlinePlayer);

                String displayName = plugin.getLanguageManager().getMessage("gui.players.player-item", playerName);
                skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

                List<String> lore = new ArrayList<>();
                for (String loreLine : plugin.getLanguageManager().getMessages("gui.players.player-lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
                }
                skullMeta.setLore(lore);

                playerHead.setItemMeta(skullMeta);
            }

            // Add to inventory
            inventory.setItem(slot, playerHead);
            playerSlotMap.put(slot, playerUUID);

            // Move to next slot
            slot++;
            if (slot >= 45) {
                break; // Limit to 45 players (5 rows)
            }
        }
    }

    // Handle click in the player management GUI
    public void handleClick(Player player, int slot, boolean isRightClick) {
        // Check if this is the "Add Player" button
        if (slot == 49) {
            promptForPlayerName(player);
            return;
        }

        // Check if this is a player
        UUID playerUUID = playerSlotMap.get(slot);
        if (playerUUID != null && isRightClick) {
            removePlayer(player, playerUUID);
        }
    }

    // Prompt the player to enter a player name
    private void promptForPlayerName(Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + "&eType the name of the player you want to add:"));

        // Set pending player addition state
        pendingPlayerAdditions.put(player, "");
    }

    // Handle player name input
    public void handlePlayerNameInput(Player player, String name) {
        // Try to find the player
        @SuppressWarnings("deprecation")
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(name);

        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("general.player-not-found")));
            pendingPlayerAdditions.remove(player);
            return;
        }

        // Add player to trusted players
        addPlayer(player, targetPlayer);
    }

    // Add a player to trusted players
    private void addPlayer(Player player, OfflinePlayer targetPlayer) {
        // Check if player is already trusted
        if (world.getTrustedPlayers().contains(targetPlayer.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + "&cThis player is already trusted in this world."));
            pendingPlayerAdditions.remove(player);
            openMenu(player);
            return;
        }

        // Add player to trusted players
        world.addTrustedPlayer(targetPlayer.getUniqueId());

        // Save changes
        plugin.getWorldManager().saveAllWorlds();

        // Notify player
        String successMessage = plugin.getLanguageManager().getMessage("worlds.players.added",
                targetPlayer.getName(), world.getName());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + successMessage));

        // Clean up and reopen menu
        pendingPlayerAdditions.remove(player);
        openMenu(player);
    }

    // Remove a player from trusted players
    private void removePlayer(Player player, UUID targetUUID) {
        // Get player name
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
        String playerName = targetPlayer.getName();
        if (playerName == null) {
            playerName = "Unknown Player";
        }

        // Remove player from trusted players
        world.removeTrustedPlayer(targetUUID);

        // Save changes
        plugin.getWorldManager().saveAllWorlds();

        // Notify player
        String successMessage = plugin.getLanguageManager().getMessage("worlds.players.removed",
                playerName, world.getName());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + successMessage));

        // Reopen menu
        openMenu(player);
    }

    // Check if player is in player name input mode
    public boolean isWaitingForPlayerName(Player player) {
        return pendingPlayerAdditions.containsKey(player);
    }

    // Clean up player data
    public void removePlayer(Player player) {
        pendingPlayerAdditions.remove(player);
    }
}