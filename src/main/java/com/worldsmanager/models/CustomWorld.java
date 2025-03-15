package com.worldsmanager.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CustomWorld {

    private int id;
    private String name;
    private UUID ownerUUID;
    private String worldName;
    private Material icon;
    private WorldSettings settings;
    private List<UUID> trustedPlayers;
    private Location spawnPoint;

    public CustomWorld(int id, String name, UUID ownerUUID, String worldName, Material icon) {
        this.id = id;
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.worldName = worldName;
        this.icon = icon;
        this.settings = new WorldSettings();
        this.trustedPlayers = new ArrayList<>();
    }

    public CustomWorld(String name, UUID ownerUUID, String worldName, Material icon) {
        this(-1, name, ownerUUID, worldName, icon);
    }

    // World operations

    public boolean isLoaded() {
        return Bukkit.getWorld(worldName) != null;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public boolean teleportPlayer(Player player) {
        if (!isLoaded()) {
            return false;
        }

        World world = getWorld();
        Location teleportLocation = (spawnPoint != null) ? spawnPoint : world.getSpawnLocation();
        return player.teleport(teleportLocation);
    }

    public boolean canAccess(Player player) {
        return player.getUniqueId().equals(ownerUUID) ||
                trustedPlayers.contains(player.getUniqueId()) ||
                player.hasPermission("worldsmanager.admin");
    }

    public void addTrustedPlayer(UUID playerUUID) {
        if (!trustedPlayers.contains(playerUUID)) {
            trustedPlayers.add(playerUUID);
        }
    }

    public void removeTrustedPlayer(UUID playerUUID) {
        trustedPlayers.remove(playerUUID);
    }

    public boolean setSpawnPoint(Location location) {
        if (location.getWorld().getName().equals(worldName)) {
            this.spawnPoint = location;
            return true;
        }
        return false;
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getWorldName() {
        return worldName;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public WorldSettings getSettings() {
        return settings;
    }

    public void setSettings(WorldSettings settings) {
        this.settings = settings;
    }

    public List<UUID> getTrustedPlayers() {
        return new ArrayList<>(trustedPlayers);
    }

    public void setTrustedPlayers(List<UUID> trustedPlayers) {
        this.trustedPlayers = new ArrayList<>(trustedPlayers);
    }

    public Location getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(Location spawnPoint) {
        this.spawnPoint = spawnPoint;
    }
}