package com.worldsmanager;

import com.worldsmanager.commands.WorldsAdminCommand;
import com.worldsmanager.commands.WorldsCommand;
import com.worldsmanager.listeners.MenuClickListener;
import com.worldsmanager.listeners.WorldsListener;
import com.worldsmanager.managers.ConfigManager;
import com.worldsmanager.managers.DatabaseManager;
import com.worldsmanager.managers.LanguageManager;
import com.worldsmanager.managers.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Plugin principal para gerenciamento de mundos através de uma interface GUI.
 * Permite que jogadores criem, personalizem e gerenciem seus próprios mundos.
 */
public class WorldsManager extends JavaPlugin {

    // Instância estática para acesso em toda aplicação
    private static WorldsManager instance;

    // Gerenciadores do plugin
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private DatabaseManager databaseManager;
    private WorldManager worldManager;

    // Listeners
    private MenuClickListener menuClickListener;
    private WorldsListener worldsListener;

    // Executores de comando
    private WorldsCommand worldsCommand;
    private WorldsAdminCommand worldsAdminCommand;

    @Override
    public void onEnable() {
        // Define a instância
        instance = this;

        try {
            // Inicializa gerenciadores
            initializeManagers();

            // Conecta ao banco de dados
            connectDatabase();

            // Registra comandos
            registerCommands();

            // Registra listeners
            registerListeners();

            // Carrega todos os mundos do banco de dados
            loadWorlds();

            getLogger().info("=======================");
            getLogger().info("WorldsManager Ativado!");
            getLogger().info("Versão: " + getDescription().getVersion());
            getLogger().info("=======================");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Erro ao inicializar o plugin WorldsManager", e);
            getLogger().severe("O plugin será desativado devido a erros na inicialização!");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Salva todos os mundos antes de desativar o plugin
        if (worldManager != null) {
            worldManager.saveAllWorlds();
        }

        // Fecha a conexão com o banco de dados
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("=======================");
        getLogger().info("WorldsManager Desativado!");
        getLogger().info("=======================");
    }

    /**
     * Recarrega o plugin
     */
    public void reload() {
        // Recarrega configurações
        configManager.reloadConfig();
        languageManager.reload();

        // Recarrega todos os mundos
        worldManager.reloadAllWorlds();

        getLogger().info("WorldsManager foi recarregado com sucesso!");
    }

    /**
     * Inicializa os gerenciadores do plugin
     */
    private void initializeManagers() {
        // Cria gerenciadores na ordem correta de dependência
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.worldManager = new WorldManager(this);
    }

    /**
     * Conecta ao banco de dados
     */
    private void connectDatabase() {
        try {
            databaseManager.connect();
            getLogger().info("Conectado ao banco de dados com sucesso!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Falha ao conectar ao banco de dados", e);
            if (configManager.isDatabaseEnabled()) {
                getLogger().warning("As operações de banco de dados não funcionarão corretamente!");
            }
        }
    }

    /**
     * Registra os comandos do plugin
     */
    private void registerCommands() {
        // Cria executores de comando
        this.worldsCommand = new WorldsCommand(this);
        this.worldsAdminCommand = new WorldsAdminCommand(this);

        // Registra comando principal com aliases
        registerCommand("worlds", worldsCommand, "w", "mundos");

        // Registra comando administrativo
        registerCommand("worldsadm", worldsAdminCommand);

        getLogger().info("Comandos registrados com sucesso!");
    }

    /**
     * Método auxiliar para registrar comandos com aliases
     */
    private void registerCommand(String name, CommandExecutor executor, String... aliases) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (aliases.length > 0 && command.getAliases().isEmpty()) {
                for (String alias : aliases) {
                    command.getAliases().add(alias);
                }
            }
        } else {
            getLogger().warning("Não foi possível registrar o comando: " + name);
        }
    }

    /**
     * Registra os listeners do plugin
     */
    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();

        // Cria listeners
        this.menuClickListener = new MenuClickListener(this);
        this.worldsListener = new WorldsListener(this);

        // Registra listeners
        pm.registerEvents(menuClickListener, this);
        pm.registerEvents(worldsListener, this);

        getLogger().info("Listeners registrados com sucesso!");
    }

    /**
     * Carrega todos os mundos do banco de dados
     */
    private void loadWorlds() {
        try {
            worldManager.loadAllWorlds();
            getLogger().info("Mundos carregados com sucesso!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Falha ao carregar mundos", e);
        }
    }

    /**
     * Acesso estático à instância do plugin
     *
     * @return Instância do plugin
     */
    public static WorldsManager getInstance() {
        return instance;
    }

    /**
     * Getters para os gerenciadores
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * Getters para listeners e comandos
     */
    public MenuClickListener getMenuClickListener() {
        return menuClickListener;
    }

    public WorldsCommand getWorldsCommand() {
        return worldsCommand;
    }
}