package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import com.worldsmanager.services.DatabaseService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador de operações de banco de dados melhorado
 */
public class ImprovedDatabaseManager implements DatabaseService {

    private final WorldsManager plugin;
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final boolean enabled;
    private final Logger logger;

    private static final String CREATE_WORLDS_TABLE =
            "CREATE TABLE IF NOT EXISTS %s_worlds (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(64) NOT NULL," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "world_name VARCHAR(64) NOT NULL UNIQUE," +
                    "icon VARCHAR(64) NOT NULL," +
                    "world_path VARCHAR(255)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX (owner_uuid)" +
                    ")";

    private static final String CREATE_WORLD_SETTINGS_TABLE =
            "CREATE TABLE IF NOT EXISTS %s_world_settings (" +
                    "world_id INT PRIMARY KEY," +
                    "game_mode VARCHAR(32) NOT NULL," +
                    "pvp_enabled BOOLEAN NOT NULL DEFAULT FALSE," +
                    "mob_spawning BOOLEAN NOT NULL DEFAULT FALSE," +
                    "redstone_enabled BOOLEAN NOT NULL DEFAULT TRUE," +
                    "physics_enabled BOOLEAN NOT NULL DEFAULT TRUE," +
                    "weather_enabled BOOLEAN NOT NULL DEFAULT FALSE," +
                    "fluid_flow BOOLEAN NOT NULL DEFAULT TRUE," +
                    "time_cycle BOOLEAN NOT NULL DEFAULT FALSE," +
                    "fixed_time BIGINT NOT NULL DEFAULT 6000," +
                    "tick_speed INT NOT NULL DEFAULT 3," +
                    "FOREIGN KEY (world_id) REFERENCES %s_worlds(id) ON DELETE CASCADE" +
                    ")";

    private static final String CREATE_TRUSTED_PLAYERS_TABLE =
            "CREATE TABLE IF NOT EXISTS %s_trusted_players (" +
                    "world_id INT NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "PRIMARY KEY (world_id, player_uuid)," +
                    "FOREIGN KEY (world_id) REFERENCES %s_worlds(id) ON DELETE CASCADE" +
                    ")";

    private static final String CREATE_SPAWN_POINTS_TABLE =
            "CREATE TABLE IF NOT EXISTS %s_spawn_points (" +
                    "world_id INT PRIMARY KEY," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL," +
                    "pitch FLOAT NOT NULL," +
                    "FOREIGN KEY (world_id) REFERENCES %s_worlds(id) ON DELETE CASCADE" +
                    ")";

    private static final String INSERT_WORLD =
            "INSERT INTO %s_worlds (name, owner_uuid, world_name, icon, world_path) VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_WORLD =
            "UPDATE %s_worlds SET name = ?, icon = ?, world_path = ? WHERE id = ?";

    private static final String INSERT_OR_UPDATE_SETTINGS =
            "INSERT INTO %s_world_settings " +
                    "(world_id, game_mode, pvp_enabled, mob_spawning, redstone_enabled, physics_enabled, " +
                    "weather_enabled, fluid_flow, time_cycle, fixed_time, tick_speed) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "game_mode = ?, pvp_enabled = ?, mob_spawning = ?, redstone_enabled = ?, physics_enabled = ?, " +
                    "weather_enabled = ?, fluid_flow = ?, time_cycle = ?, fixed_time = ?, tick_speed = ?";

    private static final String DELETE_TRUSTED_PLAYERS =
            "DELETE FROM %s_trusted_players WHERE world_id = ?";

    private static final String INSERT_TRUSTED_PLAYER =
            "INSERT INTO %s_trusted_players (world_id, player_uuid) VALUES (?, ?)";

    private static final String INSERT_OR_UPDATE_SPAWN_POINT =
            "INSERT INTO %s_spawn_points (world_id, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE x = ?, y = ?, z = ?, yaw = ?, pitch = ?";

    private static final String DELETE_WORLD =
            "DELETE FROM %s_worlds WHERE id = ?";

    private static final String SELECT_ALL_WORLDS =
            "SELECT w.id, w.name, w.owner_uuid, w.world_name, w.icon, w.world_path, " +
                    "s.game_mode, s.pvp_enabled, s.mob_spawning, s.redstone_enabled, s.physics_enabled, " +
                    "s.weather_enabled, s.fluid_flow, s.time_cycle, s.fixed_time, s.tick_speed " +
                    "FROM %s_worlds w " +
                    "LEFT JOIN %s_world_settings s ON w.id = s.world_id";

    private static final String SELECT_TRUSTED_PLAYERS =
            "SELECT player_uuid FROM %s_trusted_players WHERE world_id = ?";

    private static final String SELECT_SPAWN_POINT =
            "SELECT x, y, z, yaw, pitch FROM %s_spawn_points WHERE world_id = ?";

    public ImprovedDatabaseManager(WorldsManager plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Carrega configurações de banco de dados
        this.enabled = plugin.getConfigManager().isDatabaseEnabled();
        this.host = plugin.getConfigManager().getDatabaseHost();
        this.port = plugin.getConfigManager().getDatabasePort();
        this.database = plugin.getConfigManager().getDatabaseName();
        this.username = plugin.getConfigManager().getDatabaseUsername();
        this.password = plugin.getConfigManager().getDatabasePassword();
        this.tablePrefix = plugin.getConfigManager().getDatabaseTablePrefix();
    }

    @Override
    public void connect() {
        if (!enabled) {
            logger.warning("Banco de dados está desativado no config. Usando armazenamento em arquivo.");
            return;
        }

        try {
            // Se já tiver uma conexão ativa, não faz nada
            if (dataSource != null && !dataSource.isClosed()) {
                return;
            }

            // Log para debug
            logger.info("Iniciando conexão com o banco de dados MySQL: " + host + ":" + port + "/" + database);

            // Configuração do HikariCP
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8");
            config.setUsername(username);
            config.setPassword(password);

            // Configurações de pool
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(5);
            config.setIdleTimeout(300000); // 5 minutos
            config.setMaxLifetime(1800000); // 30 minutos
            config.setConnectionTimeout(10000); // 10 segundos
            config.setLeakDetectionThreshold(60000); // 1 minuto

            // Configurações adicionais
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            // Criação do pool de conexões
            dataSource = new HikariDataSource(config);

            // Testa a conexão
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    logger.info("Conexão de teste ao MySQL bem-sucedida!");
                }
            }

            // Cria tabelas se não existirem
            createTables();

            logger.info("Conexão com banco de dados MySQL estabelecida com sucesso!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Falha ao conectar ao banco de dados", e);
            logger.severe("URL: jdbc:mysql://" + host + ":" + port + "/" + database);
            logger.severe("Usuário: " + username);
            logger.severe("Detalhes do erro: " + e.getMessage());

            // Agenda uma tentativa de reconexão assíncrona
            scheduleReconnect();
        }
    }

    /**
     * Agenda uma tentativa de reconexão
     */
    private void scheduleReconnect() {
        new BukkitRunnable() {
            @Override
            public void run() {
                logger.info("Tentando reconectar ao banco de dados...");
                try {
                    if (dataSource != null && !dataSource.isClosed()) {
                        dataSource.close();
                    }
                    connect();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Falha na tentativa de reconexão", e);
                }
            }
        }.runTaskLaterAsynchronously(plugin, 200L); // 10 segundos
    }

    @Override
    public boolean isConnected() {
        try {
            return dataSource != null && !dataSource.isClosed() &&
                    dataSource.getConnection().isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (!enabled || dataSource == null) {
            return;
        }

        if (!dataSource.isClosed()) {
            dataSource.close();
            logger.info("Conexão com o banco de dados fechada com sucesso");
        }
    }

    /**
     * Cria tabelas do banco de dados
     */
    private void createTables() {
        try (Connection conn = getConnection()) {
            // Cria tabela de mundos
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(CREATE_WORLDS_TABLE, tablePrefix);
                stmt.executeUpdate(sql);
            }

            // Cria tabela de configurações de mundo
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(CREATE_WORLD_SETTINGS_TABLE, tablePrefix, tablePrefix);
                stmt.executeUpdate(sql);
            }

            // Cria tabela de jogadores confiáveis
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(CREATE_TRUSTED_PLAYERS_TABLE, tablePrefix, tablePrefix);
                stmt.executeUpdate(sql);
            }

            // Cria tabela de pontos de spawn
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(CREATE_SPAWN_POINTS_TABLE, tablePrefix, tablePrefix);
                stmt.executeUpdate(sql);
            }

            // Verificar se a coluna world_path existe, caso contrário adicioná-la
            try {
                DatabaseMetaData meta = conn.getMetaData();
                ResultSet rs = meta.getColumns(null, null, tablePrefix + "_worlds", "world_path");
                if (!rs.next()) {
                    // Coluna não existe, adiciona
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("ALTER TABLE " + tablePrefix +
                                "_worlds ADD COLUMN world_path VARCHAR(255) AFTER icon");
                        logger.info("Coluna world_path adicionada à tabela " + tablePrefix + "_worlds");
                    }
                }
                rs.close();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Erro ao verificar colunas", e);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Falha ao criar tabelas do banco de dados", e);
        }
    }

    @Override
    public void saveWorld(CustomWorld world) {
        if (!enabled) {
            return;
        }

        // Executa assincronamente para não bloquear a thread principal
        CompletableFuture.runAsync(() -> {
            try {
                // Verifica se a conexão está ativa
                if (!isConnected()) {
                    logger.warning("Conexão com banco de dados perdida. Tentando reconectar...");
                    connect();
                    if (!isConnected()) {
                        logger.severe("Não foi possível reconectar ao banco de dados. Operação cancelada.");
                        return;
                    }
                }

                Connection conn = getConnection();
                conn.setAutoCommit(false);

                try {
                    // Salva ou atualiza mundo
                    int worldId = saveWorldData(conn, world);
                    world.setId(worldId);

                    // Salva configurações do mundo
                    saveWorldSettings(conn, worldId, world.getSettings());

                    // Salva jogadores confiáveis
                    saveTrustedPlayers(conn, worldId, world.getTrustedPlayers());

                    // Salva ponto de spawn
                    if (world.getSpawnPoint() != null) {
                        saveSpawnPoint(conn, worldId, world.getSpawnPoint());
                    }

                    conn.commit();
                    logger.info("Mundo salvo com sucesso: " + world.getWorldName());
                } catch (SQLException e) {
                    conn.rollback();
                    logger.log(Level.SEVERE, "Falha ao salvar mundo no banco de dados", e);
                } finally {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Erro de SQL ao salvar mundo", e);
            }
        });
    }

    /**
     * Salva ou atualiza dados do mundo e retorna o ID
     *
     * @param conn Conexão com o banco de dados
     * @param world Mundo para salvar
     * @return ID do mundo
     * @throws SQLException Se ocorrer um erro
     */
    private int saveWorldData(Connection conn, CustomWorld world) throws SQLException {
        if (world.getId() == -1) {
            // Insere novo mundo
            String sql = String.format(INSERT_WORLD, tablePrefix);
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, world.getName());
                stmt.setString(2, world.getOwnerUUID().toString());
                stmt.setString(3, world.getWorldName());
                stmt.setString(4, world.getIcon().name());
                stmt.setString(5, world.getWorldPath());
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    } else {
                        throw new SQLException("Falha ao obter ID gerado para novo mundo");
                    }
                }
            }
        } else {
            // Atualiza mundo existente
            String sql = String.format(UPDATE_WORLD, tablePrefix);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, world.getName());
                stmt.setString(2, world.getIcon().name());
                stmt.setString(3, world.getWorldPath());
                stmt.setInt(4, world.getId());
                stmt.executeUpdate();
                return world.getId();
            }
        }
    }

    /**
     * Salva configurações do mundo
     *
     * @param conn Conexão com o banco de dados
     * @param worldId ID do mundo
     * @param settings Configurações para salvar
     * @throws SQLException Se ocorrer um erro
     */
    private void saveWorldSettings(Connection conn, int worldId, WorldSettings settings) throws SQLException {
        String sql = String.format(INSERT_OR_UPDATE_SETTINGS, tablePrefix);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Valores de inserção
            stmt.setInt(1, worldId);
            stmt.setString(2, settings.getGameMode().name());
            stmt.setBoolean(3, settings.isPvpEnabled());
            stmt.setBoolean(4, settings.isMobSpawning());
            stmt.setBoolean(5, settings.isRedstoneEnabled());
            stmt.setBoolean(6, settings.isPhysicsEnabled());
            stmt.setBoolean(7, settings.isWeatherEnabled());
            stmt.setBoolean(8, settings.isFluidFlow());
            stmt.setBoolean(9, settings.isTimeCycle());
            stmt.setLong(10, settings.getFixedTime());
            stmt.setInt(11, settings.getTickSpeed());

            // Valores de atualização
            stmt.setString(12, settings.getGameMode().name());
            stmt.setBoolean(13, settings.isPvpEnabled());
            stmt.setBoolean(14, settings.isMobSpawning());
            stmt.setBoolean(15, settings.isRedstoneEnabled());
            stmt.setBoolean(16, settings.isPhysicsEnabled());
            stmt.setBoolean(17, settings.isWeatherEnabled());
            stmt.setBoolean(18, settings.isFluidFlow());
            stmt.setBoolean(19, settings.isTimeCycle());
            stmt.setLong(20, settings.getFixedTime());
            stmt.setInt(21, settings.getTickSpeed());

            stmt.executeUpdate();
        }
    }

    /**
     * Salva jogadores confiáveis
     *
     * @param conn Conexão com o banco de dados
     * @param worldId ID do mundo
     * @param trustedPlayers Lista de UUIDs de jogadores confiáveis
     * @throws SQLException Se ocorrer um erro
     */
    private void saveTrustedPlayers(Connection conn, int worldId, List<UUID> trustedPlayers) throws SQLException {
        // Deleta todos os jogadores confiáveis para este mundo
        String deleteSql = String.format(DELETE_TRUSTED_PLAYERS, tablePrefix);
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, worldId);
            stmt.executeUpdate();
        }

        // Insere novos jogadores confiáveis
        if (!trustedPlayers.isEmpty()) {
            String insertSql = String.format(INSERT_TRUSTED_PLAYER, tablePrefix);
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                for (UUID playerUUID : trustedPlayers) {
                    stmt.setInt(1, worldId);
                    stmt.setString(2, playerUUID.toString());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }

    /**
     * Salva ponto de spawn
     *
     * @param conn Conexão com o banco de dados
     * @param worldId ID do mundo
     * @param spawnPoint Localização do spawn
     * @throws SQLException Se ocorrer um erro
     */
    private void saveSpawnPoint(Connection conn, int worldId, Location spawnPoint) throws SQLException {
        String sql = String.format(INSERT_OR_UPDATE_SPAWN_POINT, tablePrefix);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Valores de inserção
            stmt.setInt(1, worldId);
            stmt.setDouble(2, spawnPoint.getX());
            stmt.setDouble(3, spawnPoint.getY());
            stmt.setDouble(4, spawnPoint.getZ());
            stmt.setFloat(5, spawnPoint.getYaw());
            stmt.setFloat(6, spawnPoint.getPitch());

            // Valores de atualização
            stmt.setDouble(7, spawnPoint.getX());
            stmt.setDouble(8, spawnPoint.getY());
            stmt.setDouble(9, spawnPoint.getZ());
            stmt.setFloat(10, spawnPoint.getYaw());
            stmt.setFloat(11, spawnPoint.getPitch());

            stmt.executeUpdate();
        }
    }

    @Override
    public void deleteWorld(CustomWorld world) {
        if (!enabled || world.getId() == -1) {
            return;
        }

        // Executa assincronamente
        CompletableFuture.runAsync(() -> {
            try {
                // Verifica se a conexão está ativa
                if (!isConnected()) {
                    logger.warning("Conexão com banco de dados perdida. Tentando reconectar...");
                    connect();
                    if (!isConnected()) {
                        logger.severe("Não foi possível reconectar ao banco de dados. Operação cancelada.");
                        return;
                    }
                }

                String sql = String.format(DELETE_WORLD, tablePrefix);
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, world.getId());
                    int rowsAffected = stmt.executeUpdate();
                    logger.info("Mundo deletado do banco de dados: " + world.getWorldName() +
                            " (linhas afetadas: " + rowsAffected + ")");
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Falha ao deletar mundo do banco de dados", e);
            }
        });
    }

    @Override
    public List<CustomWorld> getAllWorlds() {
        List<CustomWorld> worlds = new ArrayList<>();

        if (!enabled) {
            return worlds;
        }

        try {
            // Verifica se a conexão está ativa
            if (!isConnected()) {
                logger.warning("Conexão com banco de dados perdida. Tentando reconectar...");
                connect();
                if (!isConnected()) {
                    logger.severe("Não foi possível reconectar ao banco de dados. Operação cancelada.");
                    return worlds;
                }
            }

            String sql = String.format(SELECT_ALL_WORLDS, tablePrefix, tablePrefix);

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
                    String worldName = rs.getString("world_name");
                    String worldPath = rs.getString("world_path");

                    // Handle potentially invalid material names
                    Material icon;
                    try {
                        icon = Material.valueOf(rs.getString("icon"));
                    } catch (IllegalArgumentException e) {
                        logger.warning("Material inválido para o mundo " + name + ": " +
                                rs.getString("icon") + ". Usando GRASS_BLOCK como padrão.");
                        icon = Material.GRASS_BLOCK;
                    }

                    CustomWorld world = new CustomWorld(id, name, ownerUUID, worldName, icon);
                    if (worldPath != null) {
                        world.setWorldPath(worldPath);
                    }

                    // Carrega configurações se disponíveis
                    if (rs.getString("game_mode") != null) {
                        WorldSettings settings = new WorldSettings();

                        try {
                            settings.setGameMode(GameMode.valueOf(rs.getString("game_mode")));
                        } catch (IllegalArgumentException e) {
                            logger.warning("GameMode inválido para o mundo " + name + ": " +
                                    rs.getString("game_mode") + ". Usando SURVIVAL como padrão.");
                            settings.setGameMode(GameMode.SURVIVAL);
                        }

                        settings.setPvpEnabled(rs.getBoolean("pvp_enabled"));
                        settings.setMobSpawning(rs.getBoolean("mob_spawning"));
                        settings.setRedstoneEnabled(rs.getBoolean("redstone_enabled"));
                        settings.setPhysicsEnabled(rs.getBoolean("physics_enabled"));
                        settings.setWeatherEnabled(rs.getBoolean("weather_enabled"));
                        settings.setFluidFlow(rs.getBoolean("fluid_flow"));
                        settings.setTimeCycle(rs.getBoolean("time_cycle"));
                        settings.setFixedTime(rs.getLong("fixed_time"));
                        settings.setTickSpeed(rs.getInt("tick_speed"));

                        world.setSettings(settings);
                    }

                    // Carrega jogadores confiáveis
                    world.setTrustedPlayers(getTrustedPlayers(conn, id));

                    // Carrega ponto de spawn
                    Location spawnPoint = getSpawnPoint(conn, id, worldName);
                    if (spawnPoint != null) {
                        world.setSpawnPoint(spawnPoint);
                    }

                    worlds.add(world);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Falha ao obter mundos do banco de dados", e);
        }

        return worlds;
    }

    /**
     * Obtém jogadores confiáveis de um mundo
     *
     * @param conn Conexão com o banco de dados
     * @param worldId ID do mundo
     * @return Lista de UUIDs de jogadores confiáveis
     */
    private List<UUID> getTrustedPlayers(Connection conn, int worldId) {
        List<UUID> trustedPlayers = new ArrayList<>();

        try {
            String sql = String.format(SELECT_TRUSTED_PLAYERS, tablePrefix);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, worldId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        trustedPlayers.add(UUID.fromString(rs.getString("player_uuid")));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Falha ao obter jogadores confiáveis do banco de dados", e);
        }

        return trustedPlayers;
    }

    /**
     * Obtém o ponto de spawn de um mundo
     *
     * @param conn Conexão com o banco de dados
     * @param worldId ID do mundo
     * @param worldName Nome do mundo
     * @return Localização do spawn ou null se não encontrado
     */
    private Location getSpawnPoint(Connection conn, int worldId, String worldName) {
        try {
            String sql = String.format(SELECT_SPAWN_POINT, tablePrefix);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, worldId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        float yaw = rs.getFloat("yaw");
                        float pitch = rs.getFloat("pitch");

                        // O mundo pode não estar carregado ainda, então criamos uma Location sem World
                        World world = Bukkit.getWorld(worldName);
                        return new Location(world, x, y, z, yaw, pitch);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Falha ao obter ponto de spawn do banco de dados", e);
        }

        return null;
    }

    @Override
    public Connection getConnection() {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                connect();
            }
            return dataSource.getConnection();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao obter conexão", e);
            scheduleReconnect();
            return null;
        }
    }

    @Override
    public boolean executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao executar update SQL", e);
            return false;
        }
    }

    @Override
    public <T> T executeQuery(String sql, ResultProcessor<T> processor, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return processor.process(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao executar query SQL", e);
            return null;
        }
    }
}