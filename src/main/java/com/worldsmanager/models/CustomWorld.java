package com.worldsmanager.models;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representa um mundo customizado gerenciado pelo plugin
 */
public class CustomWorld {

    private int id;
    private String name;
    private UUID ownerUUID;
    private String worldName;
    private Material icon;
    private WorldSettings settings;
    private List<UUID> trustedPlayers;
    private Location spawnPoint;
    private String worldPath; // Caminho personalizado para o mundo

    /**
     * Construtor para mundos existentes
     *
     * @param id ID no banco de dados
     * @param name Nome amigável do mundo
     * @param ownerUUID UUID do dono
     * @param worldName Nome interno do mundo
     * @param icon Material do ícone
     */
    public CustomWorld(int id, String name, UUID ownerUUID, String worldName, Material icon) {
        this.id = id;
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.worldName = worldName;
        this.icon = icon;
        this.settings = new WorldSettings();
        this.trustedPlayers = new ArrayList<>();
        this.worldPath = null; // Inicialmente nulo, será definido se necessário
    }

    /**
     * Construtor para novos mundos
     *
     * @param name Nome amigável do mundo
     * @param ownerUUID UUID do dono
     * @param worldName Nome interno do mundo
     * @param icon Material do ícone
     */
    public CustomWorld(String name, UUID ownerUUID, String worldName, Material icon) {
        this(-1, name, ownerUUID, worldName, icon);
    }

    // Operações de mundo

    /**
     * Verifica se o mundo está carregado
     *
     * @return true se o mundo está carregado
     */
    public boolean isLoaded() {
        return Bukkit.getWorld(worldName) != null;
    }

    /**
     * Obtém a instância do mundo
     *
     * @return Instância do mundo ou null se não estiver carregado
     */
    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    /**
     * Teleporta um jogador para este mundo
     *
     * @param player Jogador para teleportar
     * @return true se o teleporte foi bem-sucedido
     */
    public boolean teleportPlayer(Player player) {
        if (!isLoaded()) {
            return false;
        }

        World world = getWorld();
        Location teleportLocation = (spawnPoint != null) ? spawnPoint : world.getSpawnLocation();

        // Configura o modo de jogo apropriado
        if (player.getUniqueId().equals(ownerUUID)) {
            player.setGameMode(GameMode.CREATIVE);
        } else if (settings != null && settings.getGameMode() != null) {
            player.setGameMode(settings.getGameMode());
        }

        return player.teleport(teleportLocation);
    }

    /**
     * Verifica se um jogador pode acessar este mundo
     *
     * @param player Jogador para verificar
     * @return true se o jogador pode acessar
     */
    public boolean canAccess(Player player) {
        return player.getUniqueId().equals(ownerUUID) ||
                trustedPlayers.contains(player.getUniqueId()) ||
                player.hasPermission("worldsmanager.admin");
    }

    /**
     * Adiciona um jogador confiável
     *
     * @param playerUUID UUID do jogador
     */
    public void addTrustedPlayer(UUID playerUUID) {
        if (!trustedPlayers.contains(playerUUID)) {
            trustedPlayers.add(playerUUID);
        }
    }

    /**
     * Remove um jogador confiável
     *
     * @param playerUUID UUID do jogador
     */
    public void removeTrustedPlayer(UUID playerUUID) {
        trustedPlayers.remove(playerUUID);
    }

    /**
     * Define o ponto de spawn
     *
     * @param location Localização do spawn
     * @return true se bem-sucedido
     */
    public boolean setSpawnPoint(Location location) {
        if (location.getWorld().getName().equals(worldName)) {
            this.spawnPoint = location;
            return true;
        }
        return false;
    }

    // Getters e Setters

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

    /**
     * Obtém o caminho personalizado para o mundo
     *
     * @return Caminho personalizado ou nome do mundo se não definido
     */
    public String getWorldPath() {
        return worldPath != null ? worldPath : worldName;
    }

    /**
     * Define um caminho personalizado para o mundo
     *
     * @param worldPath Caminho personalizado
     */
    public void setWorldPath(String worldPath) {
        this.worldPath = worldPath;
    }
}