package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import com.worldsmanager.utils.WorldCreationUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class WorldManager {

    private final WorldsManager plugin;
    private final Map<String, CustomWorld> loadedWorlds;
    private final DatabaseManager databaseManager;

    public WorldManager(WorldsManager plugin) {
        this.plugin = plugin;
        this.loadedWorlds = new HashMap<>();
        this.databaseManager = plugin.getDatabaseManager();
    }

    // Load all worlds from database
    public void loadAllWorlds() {
        try {
            List<CustomWorld> worlds = databaseManager.getAllWorlds();
            for (CustomWorld world : worlds) {
                loadedWorlds.put(world.getWorldName(), world);
                // Don't load the world yet, load on demand
            }
            plugin.getLogger().info("Loaded " + worlds.size() + " worlds from database.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load worlds from database", e);
        }
    }

    // Save all worlds to database
    public void saveAllWorlds() {
        for (CustomWorld world : loadedWorlds.values()) {
            try {
                databaseManager.saveWorld(world);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save world " + world.getWorldName(), e);
            }
        }
    }

    // Reload all worlds
    public void reloadAllWorlds() {
        // Unload all loaded worlds first
        List<World> worldsToUnload = new ArrayList<>();
        for (CustomWorld customWorld : loadedWorlds.values()) {
            World world = customWorld.getWorld();
            if (world != null) {
                worldsToUnload.add(world);
            }
        }

        // Unload worlds
        for (World world : worldsToUnload) {
            unloadWorld(world.getName(), true);
        }

        // Clear loaded worlds map
        loadedWorlds.clear();

        // Load worlds from database
        loadAllWorlds();
    }

    // Create a new world
    public CompletableFuture<CustomWorld> createWorld(String name, UUID ownerUUID, Material icon) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate a unique world name
                String worldName = "wm_" + UUID.randomUUID().toString().substring(0, 8);

                // Create world using bukkit
                World world = WorldCreationUtils.createWorld(worldName,
                        plugin.getConfigManager().getWorldType(),
                        plugin.getConfigManager().getWorldEnvironment(),
                        plugin.getConfigManager().isGenerateStructures());

                if (world == null) {
                    throw new IllegalStateException("Failed to create world");
                }

                // Create custom world object
                CustomWorld customWorld = new CustomWorld(name, ownerUUID, worldName, icon);

                // Apply default settings
                WorldSettings defaultSettings = plugin.getConfigManager().getDefaultWorldSettings();
                customWorld.setSettings(new WorldSettings(defaultSettings));

                // Apply settings to the world
                applyWorldSettings(customWorld);

                // Save to database
                databaseManager.saveWorld(customWorld);

                // Add to loaded worlds
                loadedWorlds.put(worldName, customWorld);

                return customWorld;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create world", e);
                throw new RuntimeException("Failed to create world", e);
            }
        });
    }

    // Delete a world
    public CompletableFuture<Boolean> deleteWorld(CustomWorld customWorld) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String worldName = customWorld.getWorldName();

                // Teleport all players out of this world
                World world = customWorld.getWorld();
                if (world != null) {
                    World defaultWorld = Bukkit.getWorld("world");
                    for (Player player : world.getPlayers()) {
                        player.teleport(defaultWorld.getSpawnLocation());
                    }

                    // Unload the world
                    if (!unloadWorld(worldName, false)) {
                        throw new IllegalStateException("Failed to unload world");
                    }
                }

                // Delete world files
                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                try {
                    deleteFolder(worldFolder);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to delete world folder", e);
                    return false;
                }

                // Remove from database
                databaseManager.deleteWorld(customWorld);

                // Remove from loaded worlds
                loadedWorlds.remove(worldName);

                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete world", e);
                return false;
            }
        });
    }

    // Load a world if not already loaded
    public World loadWorld(CustomWorld customWorld) {
        if (customWorld.isLoaded()) {
            return customWorld.getWorld();
        }

        World world = Bukkit.getWorld(customWorld.getWorldName());
        if (world == null) {
            world = WorldCreationUtils.loadWorld(customWorld.getWorldName());
            if (world != null) {
                applyWorldSettings(customWorld);
            }
        }

        return world;
    }

    // Apply world settings to a loaded world
    public void applyWorldSettings(CustomWorld customWorld) {
        World world = customWorld.getWorld();
        if (world == null) {
            return;
        }

        WorldSettings settings = customWorld.getSettings();

        // Apply settings
        world.setPVP(settings.isPvpEnabled());
        world.setSpawnFlags(settings.isMobSpawning(), true);

        // Set time and weather
        if (!settings.isTimeCycle()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(settings.getFixedTime());
        } else {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }

        if (!settings.isWeatherEnabled()) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setStorm(false);
            world.setThundering(false);
        } else {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        }

        // Set block physics and fluid flow
        world.setGameRule(GameRule.DO_TILE_DROPS, settings.isPhysicsEnabled());
        world.setGameRule(GameRule.DISABLE_RAIDS, !settings.isMobSpawning());

        // Redstone
        if (!settings.isRedstoneEnabled()) {
            world.setGameRule(GameRule.DO_REDSTONE, false);
        } else {
            world.setGameRule(GameRule.DO_REDSTONE, true);
        }

        // Fluid flow
        if (!settings.isFluidFlow()) {
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
        } else {
            world.setGameRule(GameRule.DO_FIRE_TICK, true);
        }

        // Tick speed
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, settings.getTickSpeed());
    }

    // Get all worlds owned by a player
    public List<CustomWorld> getPlayerWorlds(UUID playerUUID) {
        return loadedWorlds.values().stream()
                .filter(world -> world.getOwnerUUID().equals(playerUUID))
                .collect(Collectors.toList());
    }

    // Get all worlds a player has access to
    public List<CustomWorld> getAccessibleWorlds(UUID playerUUID) {
        return loadedWorlds.values().stream()
                .filter(world -> world.getOwnerUUID().equals(playerUUID) ||
                        world.getTrustedPlayers().contains(playerUUID))
                .collect(Collectors.toList());
    }

    // Get world by name
    public CustomWorld getWorldByName(String worldName) {
        return loadedWorlds.get(worldName);
    }

    // Get all loaded worlds
    public Collection<CustomWorld> getAllWorlds() {
        return Collections.unmodifiableCollection(loadedWorlds.values());
    }

    // Helper method to unload a world
    private boolean unloadWorld(String worldName, boolean save) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return true;
        }

        // Teleport players out of the world
        World defaultWorld = Bukkit.getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(defaultWorld.getSpawnLocation());
        }

        return Bukkit.unloadWorld(world, save);
    }

    // Helper method to delete a folder recursively
    private void deleteFolder(File folder) throws IOException {
        if (!folder.exists()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Failed to delete file: " + file);
                    }
                }
            }
        }

        if (!folder.delete()) {
            throw new IOException("Failed to delete folder: " + folder);
        }
    }
}