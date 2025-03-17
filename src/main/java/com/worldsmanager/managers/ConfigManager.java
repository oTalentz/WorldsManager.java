package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.WorldSettings;
import com.worldsmanager.services.ConfigService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Gerenciador de configurações do plugin
 */
public class ConfigManager implements ConfigService {

    private final WorldsManager plugin;
    private FileConfiguration config;
    private File configFile;

    // Cache de configurações para acesso rápido
    private final Map<String, Object> configCache = new HashMap<>();

    public ConfigManager(WorldsManager plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Carrega as configurações do arquivo config.yml
     */
    private void loadConfig() {
        // Salva configuração padrão se não existir
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        // Carrega a configuração
        config = YamlConfiguration.loadConfiguration(configFile);

        // Limpa o cache
        configCache.clear();

        // Carrega configurações no cache para acesso rápido
        loadDefaultSettings();
    }

    /**
     * Carrega as configurações padrão no cache
     */
    private void loadDefaultSettings() {
        // Database
        configCache.put("database.enabled", config.getBoolean("database.enabled", false));
        configCache.put("database.type", config.getString("database.type", "sqlite"));
        configCache.put("database.host", config.getString("database.host", "localhost"));
        configCache.put("database.port", config.getInt("database.port", 3306));
        configCache.put("database.name", config.getString("database.name", "worldsmanager"));
        configCache.put("database.user", config.getString("database.user", "root"));
        configCache.put("database.password", config.getString("database.password", ""));
        configCache.put("database.table-prefix", config.getString("database.table-prefix", "wm_"));

        // World Type
        String worldTypeStr = config.getString("worlds.type", "NORMAL");
        try {
            configCache.put("worlds.type", WorldType.valueOf(worldTypeStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            configCache.put("worlds.type", WorldType.NORMAL);
            plugin.getLogger().warning("Tipo de mundo inválido: " + worldTypeStr + ". Usando NORMAL.");
        }

        // World Environment
        String worldEnvStr = config.getString("worlds.environment", "NORMAL");
        try {
            configCache.put("worlds.environment", World.Environment.valueOf(worldEnvStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            configCache.put("worlds.environment", World.Environment.NORMAL);
            plugin.getLogger().warning("Ambiente de mundo inválido: " + worldEnvStr + ". Usando NORMAL.");
        }

        // Generate Structures
        configCache.put("worlds.generate-structures", config.getBoolean("worlds.generate-structures", true));

        // Limits
        configCache.put("limits.max-worlds-per-player", config.getInt("limits.max-worlds-per-player", 3));
        configCache.put("limits.max-trusted-players", config.getInt("limits.max-trusted-players", 10));

        // Economy
        configCache.put("economy.enabled", config.getBoolean("economy.enabled", false));
        configCache.put("economy.world-creation-cost", config.getDouble("economy.world-creation-cost", 1000.0));
        configCache.put("economy.world-teleport-cost", config.getDouble("economy.world-teleport-cost", 100.0));

        // Cross-Server
        configCache.put("cross-server.enabled", config.getBoolean("cross-server.enabled", false));
        configCache.put("cross-server.worlds-server", config.getString("cross-server.worlds-server", "worlds"));
        configCache.put("cross-server.auto-teleport", config.getBoolean("cross-server.auto-teleport", true));
        configCache.put("cross-server.teleport-delay", config.getInt("cross-server.teleport-delay", 20));

        // GUI
        configCache.put("gui.main-title", config.getString("gui.main-title", "&8Seus Mundos"));
        configCache.put("gui.create-title", config.getString("gui.create-title", "&8Criar Novo Mundo"));
        configCache.put("gui.settings-title", config.getString("gui.settings-title", "&8Configurações do Mundo"));
        configCache.put("gui.players-title", config.getString("gui.players-title", "&8Gerenciar Jogadores"));
        configCache.put("gui.admin-title", config.getString("gui.admin-title", "&8Administração de Mundos"));
        configCache.put("gui.confirm-title", config.getString("gui.confirm-title", "&cConfirmar Exclusão"));
        configCache.put("gui.main-rows", config.getInt("gui.main-rows", 6));

        // Create Button
        String createButtonMaterial = config.getString("gui.create-button-material", "EMERALD_BLOCK");
        try {
            configCache.put("gui.create-button-material", Material.valueOf(createButtonMaterial.toUpperCase()));
        } catch (IllegalArgumentException e) {
            configCache.put("gui.create-button-material", Material.EMERALD_BLOCK);
            plugin.getLogger().warning("Material inválido para botão de criação: " + createButtonMaterial);
        }
        configCache.put("gui.create-button-slot", config.getInt("gui.create-button-slot", 49));

        // Messages
        configCache.put("messages.default-language", config.getString("messages.default-language", "en"));
        configCache.put("messages.use-prefix", config.getBoolean("messages.use-prefix", true));
        configCache.put("messages.prefix", config.getString("messages.prefix", "&8[&bWorldManager&8] &r"));

        // Debug
        configCache.put("debug.enabled", config.getBoolean("debug.enabled", false));
        configCache.put("debug.level", config.getInt("debug.level", 1));
    }

    @Override
    public void reloadConfig() {
        loadConfig();
    }

    @Override
    public boolean validateToken(String token) {
        return token != null && token.equals(getSecurityToken());
    }

    @Override
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar config.yml", e);
        }
    }

    @Override
    public WorldSettings getDefaultWorldSettings() {
        WorldSettings settings = new WorldSettings();

        ConfigurationSection defaultSection = config.getConfigurationSection("worlds.default-settings");
        if (defaultSection != null) {
            settings.setPvpEnabled(defaultSection.getBoolean("pvp", false));
            settings.setMobSpawning(defaultSection.getBoolean("mob-spawning", true));
            settings.setTimeCycle(defaultSection.getBoolean("time-cycle", true));
            settings.setFixedTime(defaultSection.getLong("fixed-time", 6000));
            settings.setWeatherEnabled(defaultSection.getBoolean("weather", true));
            settings.setPhysicsEnabled(defaultSection.getBoolean("physics", true));
            settings.setRedstoneEnabled(defaultSection.getBoolean("redstone", true));
            settings.setFluidFlow(defaultSection.getBoolean("fluid-flow", true));
            settings.setTickSpeed(defaultSection.getInt("tick-speed", 3));
            settings.setKeepInventory(defaultSection.getBoolean("keep-inventory", true));
            settings.setAnnounceDeaths(defaultSection.getBoolean("announce-deaths", true));
            settings.setFallDamage(defaultSection.getBoolean("fall-damage", true));
            settings.setHungerDepletion(defaultSection.getBoolean("hunger-depletion", true));
            settings.setFireSpread(defaultSection.getBoolean("fire-spread", true));
            settings.setLeafDecay(defaultSection.getBoolean("leaf-decay", true));
            settings.setBlockUpdates(defaultSection.getBoolean("block-updates", true));

            // GameMode
            String gameModeStr = defaultSection.getString("game-mode", "SURVIVAL");
            try {
                settings.setGameMode(GameMode.valueOf(gameModeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                settings.setGameMode(GameMode.SURVIVAL);
                plugin.getLogger().warning("Modo de jogo inválido na configuração: " + gameModeStr);
            }
        }

        return settings;
    }

    @Override
    public boolean isValidIconMaterial(Material material) {
        if (material == null) {
            return false;
        }

        // Get blacklist from config
        List<String> blacklist = config.getStringList("icons.blacklist");
        if (blacklist.contains(material.name())) {
            return false;
        }

        // Get available icons list
        List<String> available = config.getStringList("icons.available");

        // If no specific list is provided, accept any valid material
        if (available.isEmpty()) {
            return true;
        }

        // Check if the material is in the available list
        return available.contains(material.name());
    }

    @Override
    public List<Material> getAvailableIcons() {
        List<Material> icons = new ArrayList<>();
        List<String> available = config.getStringList("icons.available");

        if (available.isEmpty()) {
            // Se não houver lista específica, usa alguns padrões
            icons.add(Material.GRASS_BLOCK);
            icons.add(Material.STONE);
            icons.add(Material.SAND);
            icons.add(Material.DIAMOND_BLOCK);
            icons.add(Material.BEACON);
            icons.add(Material.CHEST);
            icons.add(Material.OAK_LOG);
        } else {
            // Usa a lista de configuração
            for (String matName : available) {
                try {
                    Material mat = Material.valueOf(matName.toUpperCase());
                    if (mat.isItem()) {
                        icons.add(mat);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignora materiais inválidos
                }
            }
        }

        return icons;
    }

    // Métodos getters para as configurações

    @Override
    public boolean isDatabaseEnabled() {
        return (boolean) configCache.getOrDefault("database.enabled", false);
    }

    @Override
    public String getDatabaseType() {
        return (String) configCache.getOrDefault("database.type", "sqlite");
    }

    @Override
    public String getDatabaseHost() {
        return (String) configCache.getOrDefault("database.host", "localhost");
    }

    @Override
    public int getDatabasePort() {
        return (int) configCache.getOrDefault("database.port", 3306);
    }

    @Override
    public String getDatabaseName() {
        return (String) configCache.getOrDefault("database.name", "worldsmanager");
    }

    @Override
    public String getDatabaseUsername() {
        return (String) configCache.getOrDefault("database.user", "root");
    }

    @Override
    public String getDatabasePassword() {
        return (String) configCache.getOrDefault("database.password", "");
    }

    @Override
    public String getDatabaseTablePrefix() {
        return (String) configCache.getOrDefault("database.table-prefix", "wm_");
    }

    @Override
    public WorldType getWorldType() {
        return (WorldType) configCache.getOrDefault("worlds.type", WorldType.NORMAL);
    }

    @Override
    public World.Environment getWorldEnvironment() {
        return (World.Environment) configCache.getOrDefault("worlds.environment", World.Environment.NORMAL);
    }

    @Override
    public boolean isGenerateStructures() {
        return (boolean) configCache.getOrDefault("worlds.generate-structures", true);
    }

    @Override
    public int getMaxWorldsPerPlayer() {
        return (int) configCache.getOrDefault("limits.max-worlds-per-player", 3);
    }

    @Override
    public boolean isEconomyEnabled() {
        return (boolean) configCache.getOrDefault("economy.enabled", false);
    }

    @Override
    public double getWorldCreationCost() {
        return (double) configCache.getOrDefault("economy.world-creation-cost", 1000.0);
    }

    @Override
    public double getWorldTeleportCost() {
        return (double) configCache.getOrDefault("economy.world-teleport-cost", 100.0);
    }

    @Override
    public boolean isCrossServerMode() {
        return (boolean) configCache.getOrDefault("cross-server.enabled", false);
    }

    @Override
    public String getWorldsServerName() {
        return (String) configCache.getOrDefault("cross-server.worlds-server", "worlds");
    }

    @Override
    public boolean isAutoTeleport() {
        return (boolean) configCache.getOrDefault("cross-server.auto-teleport", true);
    }

    @Override
    public int getTeleportDelay() {
        return (int) configCache.getOrDefault("cross-server.teleport-delay", 20);
    }

    @Override
    public String getDefaultLanguage() {
        return (String) configCache.getOrDefault("messages.default-language", "en");
    }

    @Override
    public boolean useMessagePrefix() {
        return (boolean) configCache.getOrDefault("messages.use-prefix", true);
    }

    @Override
    public String getMessagePrefix() {
        return (String) configCache.getOrDefault("messages.prefix", "&8[&bWorldManager&8] &r");
    }

    @Override
    public int getMainGUIRows() {
        return (int) configCache.getOrDefault("gui.main-rows", 6);
    }

    @Override
    public Material getCreateButtonMaterial() {
        return (Material) configCache.getOrDefault("gui.create-button-material", Material.EMERALD_BLOCK);
    }

    @Override
    public int getCreateButtonSlot() {
        return (int) configCache.getOrDefault("gui.create-button-slot", 49);
    }

    @Override
    public String getMainGUITitle() {
        return (String) configCache.getOrDefault("gui.main-title", "&8Seus Mundos");
    }

    @Override
    public String getCreateGUITitle() {
        return (String) configCache.getOrDefault("gui.create-title", "&8Criar Novo Mundo");
    }

    @Override
    public String getSettingsGUITitle() {
        return (String) configCache.getOrDefault("gui.settings-title", "&8Configurações do Mundo");
    }

    @Override
    public String getPlayersGUITitle() {
        return (String) configCache.getOrDefault("gui.players-title", "&8Gerenciar Jogadores");
    }

    @Override
    public String getAdminGUITitle() {
        return (String) configCache.getOrDefault("gui.admin-title", "&8Administração de Mundos");
    }

    @Override
    public String getConfirmGUITitle() {
        return (String) configCache.getOrDefault("gui.confirm-title", "&cConfirmar Exclusão");
    }

    @Override
    public boolean isDebugEnabled() {
        return (boolean) configCache.getOrDefault("debug.enabled", false);
    }

    @Override
    public int getDebugLevel() {
        return (int) configCache.getOrDefault("debug.level", 1);
    }

    @Override
    public Object get(String path, Object defaultValue) {
        if (configCache.containsKey(path)) {
            return configCache.get(path);
        }
        return config.get(path, defaultValue);
    }

    @Override
    public void set(String path, Object value) {
        config.set(path, value);
        configCache.put(path, value);
    }

    @Override
    public String getSecurityToken() {
        return "";
    }
}