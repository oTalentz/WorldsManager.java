package com.worldsmanager.gui;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import com.worldsmanager.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldSettingsGUI {

    private final WorldsManager plugin;
    private final CustomWorld world;
    private final Map<Integer, String> settingsActionMap;

    public WorldSettingsGUI(WorldsManager plugin, CustomWorld world) {
        this.plugin = plugin;
        this.world = world;
        this.settingsActionMap = new HashMap<>();
    }

    // Open the settings menu for a player
    public void openMenu(Player player) {
        // Create inventory
        String title = plugin.getLanguageManager().getMessage("gui.settings.title", world.getName());
        Inventory inventory = Bukkit.createInventory(null, 54, title); // 6 rows

        // Reset action map
        settingsActionMap.clear();

        // Add settings items
        addGeneralSettings(inventory);
        addPlayerManagementOption(inventory);
        addWorldManagementOptions(inventory);

        // Open the inventory for the player
        player.openInventory(inventory);
    }

    // Add general world settings to the inventory
    private void addGeneralSettings(Inventory inventory) {
        WorldSettings settings = world.getSettings();

        // Block Physics toggle
        String physicsStatus = settings.isPhysicsEnabled() ? "ON" : "OFF";
        String physicsText = plugin.getLanguageManager().getMessage("gui.settings.physics-toggle", physicsStatus);
        ItemStack physicsItem = new ItemBuilder(Material.ANVIL)
                .name(ChatColor.translateAlternateColorCodes('&', physicsText))
                .build();
        inventory.setItem(10, physicsItem);
        settingsActionMap.put(10, "toggle_physics");

        // Redstone toggle
        String redstoneStatus = settings.isRedstoneEnabled() ? "ON" : "OFF";
        String redstoneText = plugin.getLanguageManager().getMessage("gui.settings.redstone-toggle", redstoneStatus);
        ItemStack redstoneItem = new ItemBuilder(Material.REDSTONE)
                .name(ChatColor.translateAlternateColorCodes('&', redstoneText))
                .build();
        inventory.setItem(11, redstoneItem);
        settingsActionMap.put(11, "toggle_redstone");

        // Weather toggle
        String weatherStatus = settings.isWeatherEnabled() ? "ON" : "OFF";
        String weatherText = plugin.getLanguageManager().getMessage("gui.settings.weather-toggle", weatherStatus);
        ItemStack weatherItem = new ItemBuilder(Material.WATER_BUCKET)
                .name(ChatColor.translateAlternateColorCodes('&', weatherText))
                .build();
        inventory.setItem(12, weatherItem);
        settingsActionMap.put(12, "toggle_weather");

        // Fluid flow toggle
        String fluidStatus = settings.isFluidFlow() ? "ON" : "OFF";
        String fluidText = plugin.getLanguageManager().getMessage("gui.settings.fluid-toggle", fluidStatus);
        ItemStack fluidItem = new ItemBuilder(Material.LAVA_BUCKET)
                .name(ChatColor.translateAlternateColorCodes('&', fluidText))
                .build();
        inventory.setItem(13, fluidItem);
        settingsActionMap.put(13, "toggle_fluid");

        // Time settings
        String timeText = plugin.getLanguageManager().getMessage("gui.settings.time-set", settings.getTimeAsString());
        ItemStack timeItem = new ItemBuilder(Material.CLOCK)
                .name(ChatColor.translateAlternateColorCodes('&', timeText))
                .build();
        inventory.setItem(14, timeItem);
        settingsActionMap.put(14, "cycle_time");

        // Tick speed settings
        String tickText = plugin.getLanguageManager().getMessage("gui.settings.tick-speed", String.valueOf(settings.getTickSpeed()));
        ItemStack tickItem = new ItemBuilder(Material.REPEATER)
                .name(ChatColor.translateAlternateColorCodes('&', tickText))
                .build();
        inventory.setItem(15, tickItem);
        settingsActionMap.put(15, "cycle_tick_speed");

        // Change icon
        String iconText = plugin.getLanguageManager().getMessage("gui.settings.icon-change");
        ItemStack iconItem = new ItemBuilder(Material.ITEM_FRAME)
                .name(ChatColor.translateAlternateColorCodes('&', iconText))
                .build();
        inventory.setItem(16, iconItem);
        settingsActionMap.put(16, "change_icon");
    }

    // Add player management option
    private void addPlayerManagementOption(Inventory inventory) {
        String playerText = plugin.getLanguageManager().getMessage("gui.settings.trusted-players");
        ItemStack playerItem = new ItemBuilder(Material.PLAYER_HEAD)
                .name(ChatColor.translateAlternateColorCodes('&', playerText))
                .build();
        inventory.setItem(28, playerItem);
        settingsActionMap.put(28, "manage_players");
    }

    // Add world management options
    private void addWorldManagementOptions(Inventory inventory) {
        // Set spawn point
        String spawnText = plugin.getLanguageManager().getMessage("gui.settings.spawnpoint");
        ItemStack spawnItem = new ItemBuilder(Material.COMPASS)
                .name(ChatColor.translateAlternateColorCodes('&', spawnText))
                .build();
        inventory.setItem(30, spawnItem);
        settingsActionMap.put(30, "set_spawnpoint");

        // Download world
        String downloadText = plugin.getLanguageManager().getMessage("gui.settings.download");
        ItemStack downloadItem = new ItemBuilder(Material.CHEST)
                .name(ChatColor.translateAlternateColorCodes('&', downloadText))
                .build();
        inventory.setItem(31, downloadItem);
        settingsActionMap.put(31, "download_world");

        // Reload world
        String reloadText = plugin.getLanguageManager().getMessage("gui.settings.reload-world");
        ItemStack reloadItem = new ItemBuilder(Material.HOPPER)
                .name(ChatColor.translateAlternateColorCodes('&', reloadText))
                .build();
        inventory.setItem(32, reloadItem);
        settingsActionMap.put(32, "reload_world");

        // Delete world
        String deleteText = plugin.getLanguageManager().getMessage("gui.settings.delete-world");
        ItemStack deleteItem = new ItemBuilder(Material.BARRIER)
                .name(ChatColor.translateAlternateColorCodes('&', deleteText))
                .build();
        inventory.setItem(34, deleteItem);
        settingsActionMap.put(34, "delete_world");
    }

    // Handle click in the settings GUI
    public void handleClick(Player player, int slot) {
        String action = settingsActionMap.get(slot);
        if (action == null) {
            return;
        }

        WorldSettings settings = world.getSettings();

        switch (action) {
            case "toggle_physics":
                settings.setPhysicsEnabled(!settings.isPhysicsEnabled());
                updateSetting(player, "physics", settings.isPhysicsEnabled() ? "ON" : "OFF");
                break;

            case "toggle_redstone":
                settings.setRedstoneEnabled(!settings.isRedstoneEnabled());
                updateSetting(player, "redstone", settings.isRedstoneEnabled() ? "ON" : "OFF");
                break;

            case "toggle_weather":
                settings.setWeatherEnabled(!settings.isWeatherEnabled());
                updateSetting(player, "weather", settings.isWeatherEnabled() ? "ON" : "OFF");
                break;

            case "toggle_fluid":
                settings.setFluidFlow(!settings.isFluidFlow());
                updateSetting(player, "fluid", settings.isFluidFlow() ? "ON" : "OFF");
                break;

            case "cycle_time":
                cycleDayTime(player);
                break;

            case "cycle_tick_speed":
                cycleTickSpeed(player);
                break;

            case "change_icon":
                // To be implemented in a separate GUI
                break;

            case "manage_players":
                new PlayerManagementGUI(plugin, world).openMenu(player);
                return; // Don't reopen this menu

            case "set_spawnpoint":
                setSpawnPoint(player);
                break;

            case "download_world":
                downloadWorld(player);
                break;

            case "reload_world":
                reloadWorld(player);
                break;

            case "delete_world":
                // Ask for confirmation before deleting
                player.closeInventory();
                String confirmMessage = plugin.getLanguageManager().getMessage("worlds.delete.confirmation");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getLanguageManager().getPrefix() + confirmMessage));
                // Set player's confirmation state (to be implemented)
                return; // Don't reopen this menu
        }

        // Apply settings and reopen the menu
        plugin.getWorldManager().applyWorldSettings(world);
        plugin.getWorldManager().saveAllWorlds(); // Save changes to database
        openMenu(player);
    }

    // Update a setting and notify the player
    private void updateSetting(Player player, String settingName, String value) {
        String message = plugin.getLanguageManager().getMessage("worlds.settings.updated", settingName, value);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + message));
    }

    // Cycle through day/night times
    private void cycleDayTime(Player player) {
        WorldSettings settings = world.getSettings();

        // Define time presets: Dawn (0), Day (6000), Dusk (12000), Night (18000)
        long[] timePresets = {0, 6000, 12000, 18000};
        String[] timeNames = {"Dawn", "Day", "Dusk", "Night"};

        // Find next time preset
        int currentIndex = 0;
        long currentTime = settings.getFixedTime();

        for (int i = 0; i < timePresets.length; i++) {
            if (currentTime == timePresets[i]) {
                currentIndex = (i + 1) % timePresets.length;
                break;
            }
        }

        // Set new time
        settings.setFixedTime(timePresets[currentIndex]);
        settings.setTimeCycle(false); // Stop time cycle when manually setting time

        updateSetting(player, "time", timeNames[currentIndex]);
    }

    // Cycle through tick speed options
    private void cycleTickSpeed(Player player) {
        WorldSettings settings = world.getSettings();

        // Define tick speed options
        int[] tickOptions = {1, 3, 5, 10, 20};

        // Find next tick speed option
        int currentSpeed = settings.getTickSpeed();
        int nextSpeed = tickOptions[0];

        for (int i = 0; i < tickOptions.length; i++) {
            if (currentSpeed == tickOptions[i]) {
                nextSpeed = tickOptions[(i + 1) % tickOptions.length];
                break;
            }
        }

        // Set new tick speed
        settings.setTickSpeed(nextSpeed);

        updateSetting(player, "tick speed", String.valueOf(nextSpeed));
    }

    // Set spawn point to player's current location
    private void setSpawnPoint(Player player) {
        // Check if player is in the correct world
        if (!player.getWorld().getName().equals(world.getWorldName())) {
            String failMessage = plugin.getLanguageManager().getMessage("worlds.spawnpoint.failed", world.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + failMessage));
            return;
        }

        // Set spawn point
        world.setSpawnPoint(player.getLocation());

        String successMessage = plugin.getLanguageManager().getMessage("worlds.spawnpoint.set", world.getName());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + successMessage));
    }

    // Download world (to be implemented)
    private void downloadWorld(Player player) {
        String startMessage = plugin.getLanguageManager().getMessage("worlds.download.started");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + startMessage));

        // This would be implemented elsewhere to handle the actual download
    }

    // Reload world
    private void reloadWorld(Player player) {
        // Unload world
        if (world.isLoaded()) {
            Bukkit.getServer().unloadWorld(world.getWorld(), true);
        }

        // Load world again
        plugin.getWorldManager().loadWorld(world);

        // Apply settings
        plugin.getWorldManager().applyWorldSettings(world);

        // Notify player
        String successMessage = plugin.getLanguageManager().getMessage("general.operation-success");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + successMessage));
    }
}