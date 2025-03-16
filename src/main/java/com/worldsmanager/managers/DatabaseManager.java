package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gerencia operações de banco de dados
 */
public class DatabaseManager {

    private final WorldsManager plugin;
    private Connection connection;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final boolean enabled;

    public DatabaseManager(WorldsManager plugin) {
        this.plugin = plugin;

        // Carrega configurações de banco de dados
        this.enabled = plugin.getConfigManager().isDatabaseEnabled();
        this.host = plugin.getConfigManager().getDatabaseHost();
        this.port = plugin.getConfigManager().getDatabasePort();
        this.database = plugin.getConfigManager().getDatabaseName();
        this.username = plugin.getConfigManager().getDatabaseUsername();
        this.password = plugin.getConfigManager().getDatabasePassword();
        this.tablePrefix = plugin.getConfigManager().getDatabaseTablePrefix();
    }

    /**
     * Conecta ao banco de dados
     */
    public void connect() {
        if (!enabled) {
            plugin.getLogger().warning("Banco de dados está desativado no config. Usando armazenamento em arquivo.");
            return;
        }

        try {
            // Cria conexão
            if (connection != null && !connection.isClosed()) {
                return;
            }

            // Log para debug
            plugin.getLogger().info("Tentando conectar ao banco de dados MySQL: " + host + ":" + port + "/" + database);
            plugin.getLogger().info("Usuário: " + username);

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";

            // Aumentar os timeouts para dar mais tempo à conexão
            url += "&connectTimeout=10000&socketTimeout=60000";

            // Try to connect
            connection = DriverManager.getConnection(url, username, password);

            // Test connection
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    plugin.getLogger().info("Conexão de teste ao MySQL bem-sucedida!");
                }
            }

            // Cria tabelas se não existirem
            createTables();

            plugin.getLogger().info("Conectado ao banco de dados MySQL com sucesso.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao conectar ao banco de dados", e);
            plugin.getLogger().severe("URL: jdbc:mysql://" + host + ":" + port + "/" + database);
            plugin.getLogger().severe("Usuário: " + username);
            plugin.getLogger().severe("Detalhes do erro: " + e.getMessage());

            // Try to reconnect after a delay if needed
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::reconnect, 200L); // 10 seconds
        }
    }

    /**
     * Tentativa de reconexão ao banco de dados
     */
    private void reconnect() {
        plugin.getLogger().info("Tentando reconectar ao banco de dados...");
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8" +
                    "&connectTimeout=10000&socketTimeout=60000";

            // Tenta reconectar com usuário e senha do config
            connection = DriverManager.getConnection(url, username, password);

            // Test connection
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    plugin.getLogger().info("Reconexão ao MySQL bem-sucedida!");
                    createTables();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha na tentativa de reconexão", e);
            plugin.getLogger().severe("Usuário: " + username);
            plugin.getLogger().severe("Detalhes do erro: " + e.getMessage());

            // Se falhar após várias tentativas, notifique no console
            plugin.getLogger().severe("Não foi possível conectar ao banco de dados após múltiplas tentativas.");
            plugin.getLogger().severe("Verifique as credenciais no config.yml e se o servidor MySQL está acessível.");
        }
    }

    /**
     * Verifica se a conexão está ativa
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Desconecta do banco de dados
     */
    public void disconnect() {
        if (!enabled) {
            return;
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao desconectar do banco de dados", e);
        }
    }

    /**
     * Cria tabelas do banco de dados
     */
    private void createTables() {
        try {
            // Cria tabela de mundos
            try (Statement statement = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "worlds ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "name VARCHAR(64) NOT NULL,"
                        + "owner_uuid VARCHAR(36) NOT NULL,"
                        + "world_name VARCHAR(64) NOT NULL UNIQUE,"
                        + "icon VARCHAR(64) NOT NULL,"
                        + "world_path VARCHAR(255),"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "INDEX (owner_uuid)"
                        + ");";
                statement.executeUpdate(sql);
            }

            // Cria tabela de configurações de mundo
            try (Statement statement = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "world_settings ("
                        + "world_id INT PRIMARY KEY,"
                        + "game_mode VARCHAR(32) NOT NULL,"
                        + "pvp_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "mob_spawning BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "redstone_enabled BOOLEAN NOT NULL DEFAULT TRUE,"
                        + "physics_enabled BOOLEAN NOT NULL DEFAULT TRUE,"
                        + "weather_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "fluid_flow BOOLEAN NOT NULL DEFAULT TRUE,"
                        + "time_cycle BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "fixed_time BIGINT NOT NULL DEFAULT 6000,"
                        + "tick_speed INT NOT NULL DEFAULT 3,"
                        + "FOREIGN KEY (world_id) REFERENCES " + tablePrefix + "worlds(id) ON DELETE CASCADE"
                        + ");";
                statement.executeUpdate(sql);
            }

            // Cria tabela de jogadores confiáveis
            try (Statement statement = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "trusted_players ("
                        + "world_id INT NOT NULL,"
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "PRIMARY KEY (world_id, player_uuid),"
                        + "FOREIGN KEY (world_id) REFERENCES " + tablePrefix + "worlds(id) ON DELETE CASCADE"
                        + ");";
                statement.executeUpdate(sql);
            }

            // Cria tabela de pontos de spawn
            try (Statement statement = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "spawn_points ("
                        + "world_id INT PRIMARY KEY,"
                        + "x DOUBLE NOT NULL,"
                        + "y DOUBLE NOT NULL,"
                        + "z DOUBLE NOT NULL,"
                        + "yaw FLOAT NOT NULL,"
                        + "pitch FLOAT NOT NULL,"
                        + "FOREIGN KEY (world_id) REFERENCES " + tablePrefix + "worlds(id) ON DELETE CASCADE"
                        + ");";
                statement.executeUpdate(sql);
            }

            // Verificar se a coluna world_path existe, caso contrário adicioná-la
            try (Statement statement = connection.createStatement()) {
                try {
                    ResultSet rs = statement.executeQuery("SELECT world_path FROM " + tablePrefix + "worlds LIMIT 1");
                    rs.close(); // Coluna existe, não precisa fazer nada
                } catch (SQLException e) {
                    // Coluna não existe, vamos adicioná-la
                    statement.executeUpdate("ALTER TABLE " + tablePrefix + "worlds ADD COLUMN world_path VARCHAR(255) AFTER icon");
                    plugin.getLogger().info("Coluna world_path adicionada à tabela " + tablePrefix + "worlds");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao criar tabelas do banco de dados", e);
        }
    }

    /**
     * Salva um mundo no banco de dados
     *
     * @param world Mundo para salvar
     */
    public void saveWorld(CustomWorld world) {
        if (!enabled) {
            return;
        }

        try {
            // Verifica se a conexão está ativa
            if (!isConnected()) {
                plugin.getLogger().warning("Conexão com banco de dados perdida. Tentando reconectar...");
                connect();
                if (!isConnected()) {
                    plugin.getLogger().severe("Não foi possível reconectar ao banco de dados. Operação cancelada.");
                    return;
                }
            }

            connection.setAutoCommit(false);

            // Salva ou atualiza mundo
            int worldId = saveWorldData(world);
            world.setId(worldId);

            // Salva configurações do mundo
            saveWorldSettings(worldId, world.getSettings());

            // Salva jogadores confiáveis
            saveTrustedPlayers(worldId, world.getTrustedPlayers());

            // Salva ponto de spawn
            if (world.getSpawnPoint() != null) {
                saveSpawnPoint(worldId, world.getSpawnPoint());
            }

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao desfazer transação", rollbackEx);
            }
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar mundo no banco de dados", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao redefinir auto-commit", e);
            }
        }
    }

    /**
     * Salva ou atualiza dados do mundo e retorna o ID
     *
     * @param world Mundo para salvar
     * @return ID do mundo
     * @throws SQLException Se ocorrer um erro
     */
    private int saveWorldData(CustomWorld world) throws SQLException {
        if (world.getId() == -1) {
            // Insere novo mundo
            String sql = "INSERT INTO " + tablePrefix + "worlds (name, owner_uuid, world_name, icon, world_path) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, world.getName());
                statement.setString(2, world.getOwnerUUID().toString());
                statement.setString(3, world.getWorldName());
                statement.setString(4, world.getIcon().name());
                statement.setString(5, world.getWorldPath());
                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Falha ao obter ID gerado para novo mundo");
                    }
                }
            }
        } else {
            // Atualiza mundo existente
            String sql = "UPDATE " + tablePrefix + "worlds SET name = ?, icon = ?, world_path = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, world.getName());
                statement.setString(2, world.getIcon().name());
                statement.setString(3, world.getWorldPath());
                statement.setInt(4, world.getId());
                statement.executeUpdate();
                return world.getId();
            }
        }
    }

    /**
     * Salva configurações do mundo
     *
     * @param worldId ID do mundo
     * @param settings Configurações para salvar
     * @throws SQLException Se ocorrer um erro
     */
    private void saveWorldSettings(int worldId, WorldSettings settings) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "world_settings "
                + "(world_id, game_mode, pvp_enabled, mob_spawning, redstone_enabled, physics_enabled, "
                + "weather_enabled, fluid_flow, time_cycle, fixed_time, tick_speed) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "game_mode = ?, pvp_enabled = ?, mob_spawning = ?, redstone_enabled = ?, physics_enabled = ?, "
                + "weather_enabled = ?, fluid_flow = ?, time_cycle = ?, fixed_time = ?, tick_speed = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // Valores de inserção
            statement.setInt(1, worldId);
            statement.setString(2, settings.getGameMode().name());
            statement.setBoolean(3, settings.isPvpEnabled());
            statement.setBoolean(4, settings.isMobSpawning());
            statement.setBoolean(5, settings.isRedstoneEnabled());
            statement.setBoolean(6, settings.isPhysicsEnabled());
            statement.setBoolean(7, settings.isWeatherEnabled());
            statement.setBoolean(8, settings.isFluidFlow());
            statement.setBoolean(9, settings.isTimeCycle());
            statement.setLong(10, settings.getFixedTime());
            statement.setInt(11, settings.getTickSpeed());

            // Valores de atualização
            statement.setString(12, settings.getGameMode().name());
            statement.setBoolean(13, settings.isPvpEnabled());
            statement.setBoolean(14, settings.isMobSpawning());
            statement.setBoolean(15, settings.isRedstoneEnabled());
            statement.setBoolean(16, settings.isPhysicsEnabled());
            statement.setBoolean(17, settings.isWeatherEnabled());
            statement.setBoolean(18, settings.isFluidFlow());
            statement.setBoolean(19, settings.isTimeCycle());
            statement.setLong(20, settings.getFixedTime());
            statement.setInt(21, settings.getTickSpeed());

            statement.executeUpdate();
        }
    }

    /**
     * Salva jogadores confiáveis
     *
     * @param worldId ID do mundo
     * @param trustedPlayers Lista de UUIDs de jogadores confiáveis
     * @throws SQLException Se ocorrer um erro
     */
    private void saveTrustedPlayers(int worldId, List<UUID> trustedPlayers) throws SQLException {
        // Deleta todos os jogadores confiáveis para este mundo
        String deleteSql = "DELETE FROM " + tablePrefix + "trusted_players WHERE world_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setInt(1, worldId);
            statement.executeUpdate();
        }

        // Insere novos jogadores confiáveis
        if (!trustedPlayers.isEmpty()) {
            String insertSql = "INSERT INTO " + tablePrefix + "trusted_players (world_id, player_uuid) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (UUID playerUUID : trustedPlayers) {
                    statement.setInt(1, worldId);
                    statement.setString(2, playerUUID.toString());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    /**
     * Salva ponto de spawn
     *
     * @param worldId ID do mundo
     * @param spawnPoint Localização do spawn
     * @throws SQLException Se ocorrer um erro
     */
    private void saveSpawnPoint(int worldId, Location spawnPoint) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "spawn_points (world_id, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE x = ?, y = ?, z = ?, yaw = ?, pitch = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // Valores de inserção
            statement.setInt(1, worldId);
            statement.setDouble(2, spawnPoint.getX());
            statement.setDouble(3, spawnPoint.getY());
            statement.setDouble(4, spawnPoint.getZ());
            statement.setFloat(5, spawnPoint.getYaw());
            statement.setFloat(6, spawnPoint.getPitch());

            // Valores de atualização
            statement.setDouble(7, spawnPoint.getX());
            statement.setDouble(8, spawnPoint.getY());
            statement.setDouble(9, spawnPoint.getZ());
            statement.setFloat(10, spawnPoint.getYaw());
            statement.setFloat(11, spawnPoint.getPitch());

            statement.executeUpdate();
        }
    }

    /**
     * Deleta um mundo do banco de dados
     *
     * @param world Mundo para deletar
     */
    public void deleteWorld(CustomWorld world) {
        if (!enabled || world.getId() == -1) {
            return;
        }

        try {
            // Verifica se a conexão está ativa
            if (!isConnected()) {
                plugin.getLogger().warning("Conexão com banco de dados perdida. Tentando reconectar...");
                connect();
                if (!isConnected()) {
                    plugin.getLogger().severe("Não foi possível reconectar ao banco de dados. Operação cancelada.");
                    return;
                }
            }

            String sql = "DELETE FROM " + tablePrefix + "worlds WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, world.getId());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao deletar mundo do banco de dados", e);
        }
    }

    /**
     * Obtém todos os mundos do banco de dados
     *
     * @return Lista de mundos
     */
    public List<CustomWorld> getAllWorlds() {
        List<CustomWorld> worlds = new ArrayList<>();

        if (!enabled) {
            return worlds;
        }

        try {
            // Verifica se a conexão está ativa
            if (!isConnected()) {
                plugin.getLogger().warning("Conexão com banco de dados perdida. Tentando reconectar...");
                connect();
                if (!isConnected()) {
                    plugin.getLogger().severe("Não foi possível reconectar ao banco de dados. Operação cancelada.");
                    return worlds;
                }
            }

            String sql = "SELECT w.id, w.name, w.owner_uuid, w.world_name, w.icon, w.world_path, "
                    + "s.game_mode, s.pvp_enabled, s.mob_spawning, s.redstone_enabled, s.physics_enabled, "
                    + "s.weather_enabled, s.fluid_flow, s.time_cycle, s.fixed_time, s.tick_speed "
                    + "FROM " + tablePrefix + "worlds w "
                    + "LEFT JOIN " + tablePrefix + "world_settings s ON w.id = s.world_id";

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    String name = resultSet.getString("name");
                    UUID ownerUUID = UUID.fromString(resultSet.getString("owner_uuid"));
                    String worldName = resultSet.getString("world_name");
                    String worldPath = resultSet.getString("world_path");

                    // Handle potentially invalid material names
                    Material icon;
                    try {
                        icon = Material.valueOf(resultSet.getString("icon"));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Material inválido para o mundo " + name + ": " +
                                resultSet.getString("icon") + ". Usando GRASS_BLOCK como padrão.");
                        icon = Material.GRASS_BLOCK;
                    }

                    CustomWorld world = new CustomWorld(id, name, ownerUUID, worldName, icon);
                    if (worldPath != null) {
                        world.setWorldPath(worldPath);
                    }

                    // Carrega configurações se disponíveis
                    if (resultSet.getString("game_mode") != null) {
                        WorldSettings settings = new WorldSettings();

                        try {
                            settings.setGameMode(GameMode.valueOf(resultSet.getString("game_mode")));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("GameMode inválido para o mundo " + name + ": " +
                                    resultSet.getString("game_mode") + ". Usando SURVIVAL como padrão.");
                            settings.setGameMode(GameMode.SURVIVAL);
                        }

                        settings.setPvpEnabled(resultSet.getBoolean("pvp_enabled"));
                        settings.setMobSpawning(resultSet.getBoolean("mob_spawning"));
                        settings.setRedstoneEnabled(resultSet.getBoolean("redstone_enabled"));
                        settings.setPhysicsEnabled(resultSet.getBoolean("physics_enabled"));
                        settings.setWeatherEnabled(resultSet.getBoolean("weather_enabled"));
                        settings.setFluidFlow(resultSet.getBoolean("fluid_flow"));
                        settings.setTimeCycle(resultSet.getBoolean("time_cycle"));
                        settings.setFixedTime(resultSet.getLong("fixed_time"));
                        settings.setTickSpeed(resultSet.getInt("tick_speed"));

                        world.setSettings(settings);
                    }

                    // Carrega jogadores confiáveis
                    world.setTrustedPlayers(getTrustedPlayers(id));

                    // Carrega ponto de spawn
                    Location spawnPoint = getSpawnPoint(id, worldName);
                    if (spawnPoint != null) {
                        world.setSpawnPoint(spawnPoint);
                    }

                    worlds.add(world);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao obter mundos do banco de dados", e);
        }

        return worlds;
    }

    /**
     * Obtém jogadores confiáveis de um mundo
     *
     * @param worldId ID do mundo
     * @return Lista de UUIDs de jogadores confiáveis
     */
    private List<UUID> getTrustedPlayers(int worldId) {
        List<UUID> trustedPlayers = new ArrayList<>();

        try {
            String sql = "SELECT player_uuid FROM " + tablePrefix + "trusted_players WHERE world_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, worldId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        trustedPlayers.add(UUID.fromString(resultSet.getString("player_uuid")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao obter jogadores confiáveis do banco de dados", e);
        }

        return trustedPlayers;
    }

    /**
     * Obtém o ponto de spawn de um mundo
     *
     * @param worldId ID do mundo
     * @param worldName Nome do mundo
     * @return Localização do spawn ou null se não encontrado
     */
    private Location getSpawnPoint(int worldId, String worldName) {
        try {
            String sql = "SELECT x, y, z, yaw, pitch FROM " + tablePrefix + "spawn_points WHERE world_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, worldId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        double x = resultSet.getDouble("x");
                        double y = resultSet.getDouble("y");
                        double z = resultSet.getDouble("z");
                        float yaw = resultSet.getFloat("yaw");
                        float pitch = resultSet.getFloat("pitch");

                        // O mundo pode não estar carregado ainda, então criamos uma Location sem World
                        World world = Bukkit.getWorld(worldName);
                        return new Location(world, x, y, z, yaw, pitch);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao obter ponto de spawn do banco de dados", e);
        }

        return null;
    }
}