package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.listeners.WorldsMessageListener;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import com.worldsmanager.utils.WorldCreationUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gerenciador de mundos do plugin
 */
public class WorldManager implements Listener {

    private final WorldsManager plugin;
    private final Map<String, CustomWorld> loadedWorlds;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final MessagingManager messagingManager;
    private final TeleportManager teleportManager;

    // Sistema de teleportes pendentes
    private final Map<UUID, String> pendingTeleports = new HashMap<>();

    public WorldManager(WorldsManager plugin) {
        this.plugin = plugin;
        this.loadedWorlds = new HashMap<>();
        this.databaseManager = plugin.getDatabaseManager();
        this.configManager = plugin.getConfigManager();
        this.messagingManager = plugin.getMessagingManager();
        this.teleportManager = new TeleportManager(plugin);

        // Inicializa o WorldCreationUtils
        WorldCreationUtils.init(plugin);

        // Verificação de inicialização dos gerenciadores
        if (this.messagingManager == null && configManager.isCrossServerMode()) {
            plugin.getLogger().severe("AVISO: MessagingManager não foi inicializado corretamente e o modo cross-server está ativado!");
            plugin.getLogger().severe("As operações cross-server não funcionarão adequadamente!");
        }

        // Cria o diretório de mundos dentro da pasta do plugin
        File worldsFolder = new File(plugin.getDataFolder(), "mundos-jogadores");
        if (!worldsFolder.exists()) {
            worldsFolder.mkdirs();
            plugin.getLogger().info("Diretório de mundos criado: " + worldsFolder.getAbsolutePath());
        }

        // Registrar listener para teleportes pendentes
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerPendingTeleportListener();
    }

    /**
     * Carrega todos os mundos do banco de dados
     */
    public void loadAllWorlds() {
        try {
            List<CustomWorld> worlds = databaseManager.getAllWorlds();
            for (CustomWorld world : worlds) {
                loadedWorlds.put(world.getWorldName(), world);
                // Não carrega o mundo ainda, carrega sob demanda
            }
            plugin.getLogger().info("Carregados " + worlds.size() + " mundos do banco de dados.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao carregar mundos do banco de dados", e);
        }
    }

    /**
     * Salva todos os mundos no banco de dados
     */
    public void saveAllWorlds() {
        for (CustomWorld world : loadedWorlds.values()) {
            try {
                databaseManager.saveWorld(world);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao salvar mundo " + world.getWorldName(), e);
            }
        }
    }

    /**
     * Recarrega todos os mundos
     */
    public void reloadAllWorlds() {
        // Primeiro descarrega todos os mundos carregados
        List<World> worldsToUnload = new ArrayList<>();
        for (CustomWorld customWorld : loadedWorlds.values()) {
            World world = customWorld.getWorld();
            if (world != null) {
                worldsToUnload.add(world);
            }
        }

        // Descarrega os mundos
        for (World world : worldsToUnload) {
            unloadWorld(world.getName(), true);
        }

        // Limpa o mapa de mundos carregados
        loadedWorlds.clear();

        // Carrega mundos do banco de dados
        loadAllWorlds();
    }

    /**
     * Verifica se um mundo existe nos registros do plugin
     *
     * @param worldName Nome do mundo
     * @return true se o mundo existe
     */
    public boolean worldExists(String worldName) {
        return loadedWorlds.containsKey(worldName);
    }

    /**
     * Verifica se um mundo existe no sistema de arquivos
     *
     * @param worldName Nome do mundo
     * @return true se o mundo existe no sistema de arquivos
     */
    public boolean worldExistsOnDisk(String worldName) {
        return WorldCreationUtils.worldExists(worldName);
    }

    /**
     * Método para obter a lista de mundos formatada para GUI
     * Compatível com o formato do MultiVerse
     *
     * @param player Jogador para verificar permissões
     * @return Lista de strings formatadas com informações dos mundos
     */
    public List<String> getFormattedWorldList(Player player) {
        List<String> formattedList = new ArrayList<>();
        Collection<CustomWorld> worlds = getAllWorlds();

        // Verifica se o MultiVerse está presente
        boolean hasMultiverse = Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null;

        for (CustomWorld world : worlds) {
            // Verifica se o jogador tem acesso
            if (!world.canAccess(player) && !player.hasPermission("worldsmanager.admin")) {
                continue;
            }

            // Formatação similar ao MultiVerse
            String worldName = world.getName();
            String ownerName = Bukkit.getOfflinePlayer(world.getOwnerUUID()).getName();
            ownerName = (ownerName != null) ? ownerName : "Desconhecido";

            StringBuilder worldInfo = new StringBuilder();
            worldInfo.append("§a").append(worldName).append(" §7- ");

            if (world.getOwnerUUID().equals(player.getUniqueId())) {
                worldInfo.append("§6(Seu) ");
            } else {
                worldInfo.append("§7(Dono: ").append(ownerName).append(") ");
            }

            // Adiciona o nome técnico para uso em comandos
            worldInfo.append("§8[").append(world.getWorldName()).append("]");

            formattedList.add(worldInfo.toString());
        }

        return formattedList;
    }

    /**
     * Cria um novo mundo
     *
     * @param name Nome de exibição do mundo
     * @param ownerUUID UUID do proprietário
     * @param icon Ícone do mundo
     * @param requester Jogador que solicitou a criação
     * @return CompletableFuture com o CustomWorld criado
     */
    public CompletableFuture<CustomWorld> createWorld(String name, UUID ownerUUID, Material icon, Player requester) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Verifica permissões
                if (requester != null && !requester.hasPermission("worldsmanager.create") &&
                        !plugin.getConfigManager().get("permissions.allow-all-players-to-create", true).equals(true)) {
                    requester.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("no-permission"));
                    throw new RuntimeException("Jogador não tem permissão para criar mundos");
                }

                // Gera um nome de mundo único
                String worldName = "wm_" + UUID.randomUUID().toString().substring(0, 8);

                plugin.getLogger().info("[MUNDO] Iniciando criação de mundo: " + worldName);

                // Cria objeto de mundo personalizado
                CustomWorld customWorld = new CustomWorld(name, ownerUUID, worldName, icon);

                // Aplica configurações padrão
                WorldSettings defaultSettings = configManager.getDefaultWorldSettings();
                customWorld.setSettings(new WorldSettings(defaultSettings));

                // Define o caminho personalizado
                String playerName = "unknown";
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUUID);
                if (offlinePlayer.getName() != null) {
                    playerName = offlinePlayer.getName().toLowerCase();
                }

                // Caminho relativo à pasta mundos-jogadores dentro do plugin
                String worldPath = playerName;
                customWorld.setWorldPath(worldPath);

                // Salva no banco de dados PRIMEIRO para garantir que o mundo exista no banco
                databaseManager.saveWorld(customWorld);
                plugin.getLogger().info("[MUNDO] Mundo salvo no banco de dados: " + worldName);

                // Adiciona aos mundos carregados
                loadedWorlds.put(worldName, customWorld);

                // Se estiver no modo cross-server e tiver um jogador que solicitou
                if (configManager.isCrossServerMode() && requester != null) {
                    // Verificação de null para evitar NullPointerException
                    if (messagingManager != null) {
                        plugin.getLogger().info("[MUNDO] Enviando mensagem cross-server para criar mundo: " + worldName);

                        // Verifica se os canais estão registrados
                        if (!plugin.isReadyForCrossServer()) {
                            plugin.getLogger().severe("Canais cross-server não estão corretamente registrados!");
                            // Registra novamente os canais
                            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
                            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord",
                                    new WorldsMessageListener(plugin));
                        }

                        // Adiciona informação de teleporte pendente ANTES de enviar para o outro servidor
                        // Isso garante que o jogador será teleportado para o mundo após a mudança de servidor
                        addPendingTeleport(requester.getUniqueId(), worldName);
                        plugin.getLogger().info("[MUNDO] Adicionado teleporte pendente para " + requester.getName() +
                                " ao mundo " + worldName);

                        // Envia mensagem para criar o mundo no servidor de mundos
                        boolean messageSent = messagingManager.sendCreateWorldMessage(customWorld, requester);

                        if (!messageSent) {
                            plugin.getLogger().severe("Falha ao enviar mensagem de criação cross-server!");
                            if (requester != null) {
                                requester.sendMessage(ChatColor.RED + "Falha ao enviar mensagem de criação para o servidor de mundos!");
                            }
                            // Não criar localmente em caso de falha - apenas notificar o erro
                            return null;
                        }

                        // O MessagingManager agora lida com o teleporte após delay
                        return customWorld;
                    } else {
                        plugin.getLogger().severe("MessagingManager não foi inicializado! Não é possível enviar mensagem cross-server");
                        if (requester != null) {
                            requester.sendMessage(ChatColor.RED + "Erro interno: MessagingManager não inicializado!");
                        }
                        // Neste caso, não criamos localmente - apenas reportamos o erro
                        return null;
                    }
                } else {
                    // Cria o mundo localmente se não estiver em modo cross-server
                    createWorldLocally(worldName, customWorld);

                    // Registra no MultiVerse e finaliza a criação
                    finalizeWorldCreation(customWorld, requester);
                }

                return customWorld;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao criar mundo", e);
                if (requester != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        requester.sendMessage(ChatColor.RED + "Erro ao criar mundo: " + e.getMessage());
                    });
                }
                throw new RuntimeException("Falha ao criar mundo", e);
            }
        }).exceptionally(e -> {
            plugin.getLogger().severe("Exceção não tratada na criação de mundo: " + e.getMessage());
            e.printStackTrace();
            return null;
        });
    }

    /**
     * Finaliza o processo de criação do mundo com integração do MultiVerse
     */
    private void finalizeWorldCreation(CustomWorld customWorld, Player requester) {
        // Registra no MultiVerse se disponível
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                registerWithMultiverse(customWorld.getWorldName());
            }, 60L); // 3 segundos após a criação para garantir que o mundo esteja carregado
        }

        // Notifica o proprietário se estiver online
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-created-success"));

            // Teleporta o jogador para o mundo recém-criado após um breve delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (requester.isOnline()) {
                    requester.sendMessage(ChatColor.GREEN + "Teleportando para seu novo mundo: " + customWorld.getName());
                    customWorld.teleportPlayer(requester);

                    // Configura o jogador para modo criativo
                    requester.setGameMode(GameMode.CREATIVE);

                    // Mensagem de bem-vindo
                    requester.sendMessage(ChatColor.GREEN + "Bem-vindo ao seu novo mundo! Você está no modo criativo.");
                    requester.sendMessage(ChatColor.YELLOW + "Use /worlds para gerenciar seus mundos.");
                }
            }, 20L); // 1 segundo de delay
        }
    }

    /**
     * Método para integração com MultiVerse-Core
     * Registra um mundo no MultiVerse após sua criação
     *
     * @param worldName Nome do mundo a ser registrado
     * @return true se o registro foi bem-sucedido
     */
    public boolean registerWithMultiverse(String worldName) {
        try {
            // Verifica se o MultiVerse-Core está presente
            if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
                plugin.getLogger().warning("MultiVerse-Core não encontrado. O mundo não será registrado no MultiVerse.");
                return false;
            }

            // Executa o comando multiverse para importar o mundo
            String command = "mv import " + worldName + " normal";
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            if (success) {
                plugin.getLogger().info("Mundo " + worldName + " registrado com sucesso no MultiVerse-Core");

                // Configura propriedades do mundo no MultiVerse (flat world)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify set generator flat " + worldName);

                return true;
            } else {
                plugin.getLogger().warning("Falha ao registrar mundo " + worldName + " no MultiVerse-Core");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao integrar com MultiVerse-Core: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verifica se um mundo está registrado no MultiVerse
     *
     * @param worldName Nome do mundo a verificar
     * @return true se o mundo estiver registrado no MultiVerse
     */
    public boolean isRegisteredInMultiverse(String worldName) {
        // Se o Multiverse não estiver instalado, retorna false
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
            return false;
        }

        try {
            // Tenta acessar a API do Multiverse
            org.bukkit.plugin.Plugin mvPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            Class<?> coreClass = mvPlugin.getClass();

            // Tenta obter o método para verificar se o mundo existe no Multiverse
            java.lang.reflect.Method mvWorldsMethod = coreClass.getMethod("getMVWorldManager");
            Object worldManager = mvWorldsMethod.invoke(mvPlugin);

            // Procura o método isMVWorld na classe do gerenciador de mundos
            for (java.lang.reflect.Method method : worldManager.getClass().getMethods()) {
                if (method.getName().equals("isMVWorld") && method.getParameterCount() == 1) {
                    return (boolean) method.invoke(worldManager, worldName);
                }
            }

            // Método alternativo - verifica se o mundo existe na configuração do Multiverse
            File mvWorldsFile = new File(Bukkit.getWorldContainer().getParentFile(),
                    "plugins/Multiverse-Core/worlds.yml");
            if (mvWorldsFile.exists()) {
                org.bukkit.configuration.file.YamlConfiguration config =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mvWorldsFile);
                return config.contains("worlds." + worldName);
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao verificar mundo no Multiverse: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cria um mundo localmente usando WorldCreationUtils
     *
     * @param worldName Nome do mundo
     * @param customWorld Objeto CustomWorld associado
     * @throws IllegalStateException Se a criação falhar
     */
    private void createWorldLocally(String worldName, CustomWorld customWorld) throws IllegalStateException {
        plugin.getLogger().info("Criando mundo localmente: " + worldName);

        // Obtém nome do jogador para o diretório
        String playerFolder = customWorld.getWorldPath();

        // Cria o mundo na pasta personalizada
        World world = WorldCreationUtils.createWorldInPath(
                worldName,
                playerFolder,
                configManager.getWorldType(),
                configManager.getWorldEnvironment(),
                configManager.isGenerateStructures()
        );

        if (world == null) {
            plugin.getLogger().severe("Falha ao criar mundo localmente: " + worldName);
            throw new IllegalStateException("Falha ao criar mundo");
        }

        plugin.getLogger().info("Mundo criado localmente com sucesso: " + worldName);

        // Aplica configurações ao mundo
        applyWorldSettings(customWorld);
    }

    /**
     * Exclui um mundo
     *
     * @param customWorld Mundo personalizado a ser excluído
     * @param requester Jogador que solicitou a exclusão (pode ser null)
     * @return CompletableFuture com resultado booleano
     */
    public CompletableFuture<Boolean> deleteWorld(CustomWorld customWorld, Player requester) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String worldName = customWorld.getWorldName();
                plugin.getLogger().info("Iniciando exclusão do mundo: " + worldName);

                // Se estiver no modo cross-server e tiver um jogador que solicitou
                if (configManager.isCrossServerMode() && requester != null && messagingManager != null) {
                    plugin.getLogger().info("Enviando solicitação de exclusão para o servidor de mundos: " + worldName);

                    // Verifica se os canais estão registrados
                    if (!plugin.isReadyForCrossServer()) {
                        plugin.getLogger().severe("Canais cross-server não estão corretamente registrados!");
                        // Registra novamente os canais
                        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
                        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord",
                                new WorldsMessageListener(plugin));
                    }

                    // Envia mensagem para excluir o mundo no servidor de mundos
                    boolean messageSent = messagingManager.sendDeleteWorldMessage(worldName, requester);

                    if (!messageSent) {
                        plugin.getLogger().severe("Falha ao enviar mensagem de exclusão cross-server!");
                        if (requester != null) {
                            requester.sendMessage(ChatColor.RED + "Falha ao enviar mensagem de exclusão para o servidor de mundos!");
                        }
                        // Continua com a exclusão local dos dados
                    }
                } else {
                    // Teleporta todos os jogadores para fora deste mundo
                    World world = customWorld.getWorld();
                    if (world != null) {
                        World defaultWorld = Bukkit.getWorld("world");
                        for (Player player : world.getPlayers()) {
                            player.teleport(defaultWorld.getSpawnLocation());
                        }

                        // Descarrega o mundo
                        if (!unloadWorld(worldName, false)) {
                            plugin.getLogger().warning("Falha ao descarregar mundo: " + worldName);
                        }
                    }

                    // Remove do Multiverse se estiver registrado
                    if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                        if (isRegisteredInMultiverse(worldName)) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
                        }
                    }

                    // Exclui os arquivos do mundo da pasta do plugin
                    String playerFolder = customWorld.getWorldPath();
                    File worldFolder;

                    if (playerFolder != null && !playerFolder.isEmpty()) {
                        worldFolder = WorldCreationUtils.getWorldDirectoryInPath(worldName, playerFolder);
                    } else {
                        worldFolder = WorldCreationUtils.getWorldDirectory(worldName);
                    }

                    try {
                        if (worldFolder.exists()) {
                            deleteFolder(worldFolder);
                            plugin.getLogger().info("Arquivos do mundo excluídos: " + worldFolder.getAbsolutePath());
                        } else {
                            plugin.getLogger().warning("Pasta do mundo não encontrada: " + worldFolder.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Falha ao excluir pasta do mundo", e);
                        return false;
                    }
                }

                // Remove do banco de dados
                databaseManager.deleteWorld(customWorld);
                plugin.getLogger().info("Mundo removido do banco de dados: " + worldName);

                // Remove dos mundos carregados
                loadedWorlds.remove(worldName);

                plugin.getLogger().info("Exclusão do mundo concluída com sucesso: " + worldName);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao excluir mundo", e);
                if (requester != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        requester.sendMessage(ChatColor.RED + "Erro ao excluir mundo: " + e.getMessage());
                    });
                }
                return false;
            }
        });
    }

    /**
     * Sobrecarga do método deleteWorld para manter compatibilidade
     */
    public CompletableFuture<Boolean> deleteWorld(CustomWorld customWorld) {
        return deleteWorld(customWorld, null);
    }

    /**
     * Carrega um mundo se ainda não estiver carregado
     *
     * @param customWorld Mundo personalizado a ser carregado
     * @return O mundo carregado ou null se falhar
     */
    public World loadWorld(CustomWorld customWorld) {
        if (customWorld.isLoaded()) {
            return customWorld.getWorld();
        }

        plugin.getLogger().info("Carregando mundo: " + customWorld.getWorldName());

        World world = Bukkit.getWorld(customWorld.getWorldName());
        if (world == null) {
            // Tenta carregar do caminho personalizado na pasta do plugin
            if (customWorld.getWorldPath() != null && !customWorld.getWorldPath().isEmpty()) {
                world = WorldCreationUtils.loadWorldFromPath(
                        customWorld.getWorldName(),
                        customWorld.getWorldPath()
                );
            }

            // Se ainda não conseguiu, tenta o método normal
            if (world == null) {
                world = WorldCreationUtils.loadWorld(customWorld.getWorldName());
            }

            if (world != null) {
                plugin.getLogger().info("Mundo carregado com sucesso: " + customWorld.getWorldName());
                applyWorldSettings(customWorld);
            } else {
                plugin.getLogger().warning("Falha ao carregar mundo: " + customWorld.getWorldName());
            }
        } else {
            plugin.getLogger().info("Mundo já está carregado: " + customWorld.getWorldName());
        }

        return world;
    }

    /**
     * Teleporta um jogador para um mundo
     *
     * @param player Jogador a ser teleportado
     * @param customWorld Mundo de destino
     * @return true se o teleporte foi bem-sucedido
     */
    public boolean teleportPlayerToWorld(Player player, CustomWorld customWorld) {
        // Verifica se o modo cross-server está ativado
        if (configManager.isCrossServerMode()) {
            plugin.getLogger().info("Teleportando jogador via cross-server com verificação de servidor: " +
                    player.getName() + " para " + customWorld.getWorldName());

            // Usar o TeleportManager que verifica o servidor atual
            return teleportManager.teleportToWorld(player, customWorld.getWorldName());
        } else {
            // Carrega o mundo se não estiver carregado
            World world = loadWorld(customWorld);
            if (world == null) {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-load-failed"));
                return false;
            }

            // Teleporta o jogador
            boolean success = customWorld.teleportPlayer(player);
            if (success) {
                plugin.getLogger().info("Jogador teleportado com sucesso: " + player.getName() +
                        " para " + customWorld.getWorldName());
            } else {
                plugin.getLogger().warning("Falha ao teleportar jogador: " + player.getName() +
                        " para " + customWorld.getWorldName());
            }
            return success;
        }
    }

    /**
     * Aplica configurações de mundo a um mundo carregado
     *
     * @param customWorld Mundo personalizado
     */
    public void applyWorldSettings(CustomWorld customWorld) {
        World world = customWorld.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Tentativa de aplicar configurações a um mundo não carregado: " +
                    customWorld.getWorldName());
            return;
        }

        WorldSettings settings = customWorld.getSettings();
        plugin.getLogger().info("Aplicando configurações ao mundo: " + customWorld.getWorldName());

        // Aplica configurações
        world.setPVP(settings.isPvpEnabled());
        world.setSpawnFlags(settings.isMobSpawning(), true);

        // Configura tempo e clima
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

        // Configura física de blocos e fluxo de fluidos
        world.setGameRule(GameRule.DO_TILE_DROPS, settings.isPhysicsEnabled());
        world.setGameRule(GameRule.DISABLE_RAIDS, !settings.isMobSpawning());

        // Redstone - o GameRule DO_REDSTONE pode não existir em algumas versões do Bukkit
        if (!settings.isRedstoneEnabled()) {
            try {
                // Tenta usar o GameRule se existir
                @SuppressWarnings("unchecked")
                GameRule<Boolean> redstoneRule = (GameRule<Boolean>) GameRule.getByName("DO_REDSTONE");
                if (redstoneRule != null) {
                    world.setGameRule(redstoneRule, false);
                }
            } catch (Exception e) {
                // Se falhar, faz log do erro mas continua a execução
                plugin.getLogger().warning("Não foi possível definir regra do redstone: " + e.getMessage());
            }
        } else {
            try {
                @SuppressWarnings("unchecked")
                GameRule<Boolean> redstoneRule = (GameRule<Boolean>) GameRule.getByName("DO_REDSTONE");
                if (redstoneRule != null) {
                    world.setGameRule(redstoneRule, true);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Não foi possível definir regra do redstone: " + e.getMessage());
            }
        }

        // Fluxo de fluidos
        if (!settings.isFluidFlow()) {
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
        } else {
            world.setGameRule(GameRule.DO_FIRE_TICK, true);
        }

        // Velocidade de tick
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, settings.getTickSpeed());

        // Aplicar configurações adicionais
        world.setGameRule(GameRule.KEEP_INVENTORY, settings.isKeepInventory());
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, settings.isAnnounceDeaths());
        world.setGameRule(GameRule.FALL_DAMAGE, settings.isFallDamage());
        world.setGameRule(GameRule.NATURAL_REGENERATION, settings.isHungerDepletion());

        // Para o leaf decay:
        try {
            @SuppressWarnings("unchecked")
            GameRule<Boolean> leafDecayRule = (GameRule<Boolean>) GameRule.getByName("LEAF_DECAY");
            if (leafDecayRule != null) {
                world.setGameRule(leafDecayRule, settings.isLeafDecay());
            }
        } catch (Exception e) {
            // Ignora se a regra não existir
        }

        // Para o fire spread:
        try {
            @SuppressWarnings("unchecked")
            GameRule<Boolean> fireSpreadRule = (GameRule<Boolean>) GameRule.getByName("FIRE_SPREAD");
            if (fireSpreadRule != null) {
                world.setGameRule(fireSpreadRule, settings.isFireSpread());
            }
        } catch (Exception e) {
            // Ignora se a regra não existir
        }

        // Para os block updates:
        try {
            @SuppressWarnings("unchecked")
            GameRule<Boolean> blockUpdatesRule = (GameRule<Boolean>) GameRule.getByName("BLOCK_UPDATES");
            if (blockUpdatesRule != null) {
                world.setGameRule(blockUpdatesRule, settings.isBlockUpdates());
            }
        } catch (Exception e) {
            // Ignora se a regra não existir
        }

        // Aplica o modo de jogo padrão para o mundo
        if (settings.getGameMode() != null) {
            for (Player player : world.getPlayers()) {
                if (!player.hasPermission("worldsmanager.gamemode.bypass")) {
                    player.setGameMode(settings.getGameMode());
                }
            }
        }

        plugin.getLogger().info("Configurações aplicadas com sucesso ao mundo: " + customWorld.getWorldName());
    }

    /**
     * Atualiza as configurações de um mundo
     */
    public void updateWorldSettings(CustomWorld customWorld, WorldSettings settings, Player requester) {
        // Atualiza as configurações no objeto
        customWorld.setSettings(settings);
        plugin.getLogger().info("Atualizando configurações para o mundo: " + customWorld.getWorldName());

        // Se estiver no modo cross-server e tiver um jogador que solicitou
        if (configManager.isCrossServerMode() && requester != null && messagingManager != null) {
            plugin.getLogger().info("Enviando atualização de configurações via cross-server: " +
                    customWorld.getWorldName());

            // Verifica se os canais estão registrados
            if (!plugin.isReadyForCrossServer()) {
                plugin.getLogger().severe("Canais cross-server não estão corretamente registrados!");
                if (requester != null) {
                    requester.sendMessage(ChatColor.RED + "Erro: canais cross-server não estão registrados corretamente!");
                }
                // Registra novamente os canais
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
                plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord",
                        new WorldsMessageListener(plugin));
            }

            // Envia mensagem para atualizar as configurações no servidor de mundos
            boolean messageSent = messagingManager.sendUpdateWorldSettingsMessage(
                    customWorld.getWorldName(), settings, requester);

            if (!messageSent && requester != null) {
                requester.sendMessage(ChatColor.RED + "Falha ao enviar atualização de configurações para o servidor de mundos!");
            }
        } else if (customWorld.isLoaded()) {
            // Aplica as configurações ao mundo
            applyWorldSettings(customWorld);
        }

        // Salva no banco de dados
        databaseManager.saveWorld(customWorld);
        plugin.getLogger().info("Configurações salvas no banco de dados: " + customWorld.getWorldName());
    }

    /**
     * Obtém todos os mundos de um jogador
     */
    public List<CustomWorld> getPlayerWorlds(UUID playerUUID) {
        return loadedWorlds.values().stream()
                .filter(world -> world.getOwnerUUID().equals(playerUUID))
                .collect(Collectors.toList());
    }

    /**
     * Obtém todos os mundos que um jogador tem acesso
     */
    public List<CustomWorld> getAccessibleWorlds(UUID playerUUID) {
        return loadedWorlds.values().stream()
                .filter(world -> world.getOwnerUUID().equals(playerUUID) ||
                        world.getTrustedPlayers().contains(playerUUID))
                .collect(Collectors.toList());
    }

    /**
     * Obtém um mundo pelo nome
     */
    public CustomWorld getWorldByName(String worldName) {
        return loadedWorlds.get(worldName);
    }

    /**
     * Obtém todos os mundos carregados
     */
    public Collection<CustomWorld> getAllWorlds() {
        return Collections.unmodifiableCollection(loadedWorlds.values());
    }

    /**
     * Adiciona um mundo à lista de mundos carregados
     *
     * @param customWorld Mundo a ser adicionado
     */
    public void addLoadedWorld(CustomWorld customWorld) {
        loadedWorlds.put(customWorld.getWorldName(), customWorld);
        plugin.getLogger().info("Mundo adicionado à lista de mundos carregados: " + customWorld.getWorldName());
    }

    /**
     * Método auxiliar para descarregar um mundo
     */
    private boolean unloadWorld(String worldName, boolean save) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return true;
        }

        plugin.getLogger().info("Descarregando mundo: " + worldName + " (save=" + save + ")");

        // Teleporta jogadores para fora do mundo
        World defaultWorld = Bukkit.getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(defaultWorld.getSpawnLocation());
        }

        boolean success = Bukkit.unloadWorld(world, save);
        if (success) {
            plugin.getLogger().info("Mundo descarregado com sucesso: " + worldName);
        } else {
            plugin.getLogger().warning("Falha ao descarregar mundo: " + worldName);
        }

        return success;
    }

    /**
     * Método auxiliar para excluir uma pasta recursivamente
     */
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
                        throw new IOException("Falha ao excluir arquivo: " + file);
                    }
                }
            }
        }

        if (!folder.delete()) {
            throw new IOException("Falha ao excluir pasta: " + folder);
        }

        plugin.getLogger().info("Pasta excluída com sucesso: " + folder.getAbsolutePath());
    }

    /**
     * Adiciona um teleporte pendente para um jogador
     */
    public void addPendingTeleport(UUID playerUUID, String worldName) {
        pendingTeleports.put(playerUUID, worldName);
        plugin.getLogger().info("[MUNDO] Teleporte pendente adicionado para " + playerUUID + " para o mundo " + worldName);
    }

    /**
     * Verifica e processa teleportes pendentes para um jogador
     */
    public void checkPendingTeleports(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (pendingTeleports.containsKey(playerUUID)) {
            String worldName = pendingTeleports.get(playerUUID);
            plugin.getLogger().info("[MUNDO] Processando teleporte pendente para " + player.getName() +
                    " para o mundo " + worldName);

            // Remove do mapa de pendentes antes de teleportar
            pendingTeleports.remove(playerUUID);

            // Delay para garantir que o jogador terminou de entrar no servidor
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CustomWorld world = getWorldByName(worldName);

                if (world != null) {
                    // Primeiro tentamos carregar o mundo se ele não estiver carregado
                    if (!world.isLoaded()) {
                        plugin.getLogger().info("[MUNDO] Carregando mundo para teleporte pendente: " + worldName);
                        loadWorld(world);
                    }

                    if (world.isLoaded()) {
                        world.teleportPlayer(player);
                        player.sendMessage(ChatColor.GREEN + "Você foi teleportado para o mundo: " + world.getName());
                    } else {
                        plugin.getLogger().warning("Mundo não pôde ser carregado: " + worldName);
                        player.sendMessage(ChatColor.RED + "Não foi possível teleportar você para o mundo. " +
                                "Mundo não pôde ser carregado.");
                    }
                } else {
                    plugin.getLogger().warning("Mundo para teleporte pendente não encontrado: " + worldName);
                    player.sendMessage(ChatColor.RED + "Não foi possível teleportar você para o mundo. " +
                            "Mundo não encontrado.");
                }
            }, 40L); // 2 segundos de delay
        }
    }

    /**
     * Registra um listener para processar teleportes pendentes
     */
    public void registerPendingTeleportListener() {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPlayerJoin(PlayerJoinEvent event) {
                // Processa teleportes pendentes quando o jogador entrar
                Player player = event.getPlayer();

                // Pequeno delay para garantir que o jogador terminou de entrar
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    checkPendingTeleports(player);
                }, 10L);
            }
        }, plugin);
    }

    /**
     * Verifica se o plugin está pronto para comunicação cross-server
     */
    public boolean isReadyForCrossServer() {
        if (!configManager.isCrossServerMode()) {
            return false;
        }

        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getLogger().severe("Canal BungeeCord não está registrado para saída!");
            return false;
        }

        if (!plugin.getServer().getMessenger().isIncomingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getLogger().severe("Canal BungeeCord não está registrado para entrada!");
            return false;
        }

        if (messagingManager == null) {
            plugin.getLogger().severe("MessagingManager não está inicializado!");
            return false;
        }

        return true;
    }

    /**
     * Listener para jogadores entrando no servidor Worlds-1
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Informar o TeleportManager sobre a entrada do jogador
        teleportManager.onPlayerJoin(player);

        // Verifica se há um teleporte pendente para este jogador
        UUID playerUUID = player.getUniqueId();
        if (pendingTeleports.containsKey(playerUUID)) {
            String worldName = pendingTeleports.get(playerUUID);
            plugin.getLogger().info("Teleporte pendente detectado para " + player.getName() + " ao mundo " + worldName);

            // Remove da lista de pendentes - o TeleportManager cuidará disso
            pendingTeleports.remove(playerUUID);
        }
    }

    /**
     * Obtém o gerenciador de teleportes
     * @return TeleportManager
     */
    public TeleportManager getTeleportManager() {
        return teleportManager;
    }
}