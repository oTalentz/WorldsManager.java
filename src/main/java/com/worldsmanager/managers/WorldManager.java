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

/**
 * Gerenciador de mundos do plugin
 */
public class WorldManager {

    private final WorldsManager plugin;
    private final Map<String, CustomWorld> loadedWorlds;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final MessagingManager messagingManager;

    public WorldManager(WorldsManager plugin) {
        this.plugin = plugin;
        this.loadedWorlds = new HashMap<>();
        this.databaseManager = plugin.getDatabaseManager();
        this.configManager = plugin.getConfigManager();
        this.messagingManager = plugin.getMessagingManager();

        // Verificação de inicialização dos gerenciadores
        if (this.messagingManager == null && configManager.isCrossServerMode()) {
            plugin.getLogger().severe("AVISO: MessagingManager não foi inicializado corretamente e o modo cross-server está ativado!");
            plugin.getLogger().severe("As operações cross-server não funcionarão adequadamente!");
        }
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
                // Gera um nome de mundo único
                String worldName = "wm_" + UUID.randomUUID().toString().substring(0, 8);

                // Cria objeto de mundo personalizado
                CustomWorld customWorld = new CustomWorld(name, ownerUUID, worldName, icon);

                // Aplica configurações padrão
                WorldSettings defaultSettings = configManager.getDefaultWorldSettings();
                customWorld.setSettings(new WorldSettings(defaultSettings));

                // Salva no banco de dados
                databaseManager.saveWorld(customWorld);

                // Adiciona aos mundos carregados
                loadedWorlds.put(worldName, customWorld);

                // Se estiver no modo cross-server, envia mensagem para criar o mundo no servidor de mundos
                if (configManager.isCrossServerMode()) {
                    // Verificação de null para evitar NullPointerException
                    if (messagingManager != null) {
                        messagingManager.sendCreateWorldMessage(customWorld, requester);

                        // Teleporta o jogador para o servidor de mundos se configurado
                        if (configManager.isAutoTeleport()) {
                            messagingManager.sendTeleportToWorldMessage(requester, worldName);
                        }
                    } else {
                        plugin.getLogger().severe("MessagingManager não foi inicializado! Não é possível enviar mensagem cross-server");
                        // Cria mundo localmente como fallback
                        createWorldLocally(worldName, customWorld);
                    }
                } else {
                    // Cria o mundo localmente
                    createWorldLocally(worldName, customWorld);
                }

                return customWorld;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao criar mundo", e);
                throw new RuntimeException("Falha ao criar mundo", e);
            }
        });
    }

    /**
     * Cria um mundo localmente usando Bukkit
     *
     * @param worldName Nome do mundo
     * @param customWorld Objeto CustomWorld associado
     * @throws IllegalStateException Se a criação falhar
     */
    private void createWorldLocally(String worldName, CustomWorld customWorld) throws IllegalStateException {
        World world = WorldCreationUtils.createWorld(worldName,
                configManager.getWorldType(),
                configManager.getWorldEnvironment(),
                configManager.isGenerateStructures());

        if (world == null) {
            throw new IllegalStateException("Falha ao criar mundo");
        }

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

                // Se estiver no modo cross-server e tiver um jogador que solicitou
                if (configManager.isCrossServerMode() && requester != null && messagingManager != null) {
                    // Envia mensagem para excluir o mundo no servidor de mundos
                    messagingManager.sendDeleteWorldMessage(worldName, requester);
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
                            throw new IllegalStateException("Falha ao descarregar mundo");
                        }
                    }

                    // Exclui os arquivos do mundo
                    File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                    try {
                        deleteFolder(worldFolder);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Falha ao excluir pasta do mundo", e);
                        return false;
                    }
                }

                // Remove do banco de dados
                databaseManager.deleteWorld(customWorld);

                // Remove dos mundos carregados
                loadedWorlds.remove(worldName);

                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao excluir mundo", e);
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

        World world = Bukkit.getWorld(customWorld.getWorldName());
        if (world == null) {
            world = WorldCreationUtils.loadWorld(customWorld.getWorldName());
            if (world != null) {
                applyWorldSettings(customWorld);
            }
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
        if (configManager.isCrossServerMode() && messagingManager != null) {
            // Envia mensagem para teleportar o jogador para o mundo no servidor de mundos
            return messagingManager.sendTeleportToWorldMessage(player, customWorld.getWorldName());
        } else {
            // Carrega o mundo se não estiver carregado
            World world = loadWorld(customWorld);
            if (world == null) {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-load-failed"));
                return false;
            }

            // Teleporta o jogador
            return customWorld.teleportPlayer(player);
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
            return;
        }

        WorldSettings settings = customWorld.getSettings();

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
        // Observe que isso só afeta novos jogadores que entram no mundo
        // Jogadores existentes mantêm seu modo de jogo
        if (settings.getGameMode() != null) {
            for (Player player : world.getPlayers()) {
                if (!player.hasPermission("worldsmanager.gamemode.bypass")) {
                    player.setGameMode(settings.getGameMode());
                }
            }
        }
    }

    /**
     * Atualiza as configurações de um mundo
     *
     * @param customWorld Mundo a ser atualizado
     * @param settings Novas configurações
     * @param requester Jogador que solicitou a atualização (pode ser null)
     */
    public void updateWorldSettings(CustomWorld customWorld, WorldSettings settings, Player requester) {
        // Atualiza as configurações no objeto
        customWorld.setSettings(settings);

        // Se estiver no modo cross-server e tiver um jogador que solicitou
        if (configManager.isCrossServerMode() && requester != null && messagingManager != null) {
            // Envia mensagem para atualizar as configurações no servidor de mundos
            messagingManager.sendUpdateWorldSettingsMessage(customWorld.getWorldName(), settings, requester);
        } else if (customWorld.isLoaded()) {
            // Aplica as configurações ao mundo
            applyWorldSettings(customWorld);
        }

        // Salva no banco de dados
        databaseManager.saveWorld(customWorld);
    }

    /**
     * Obtém todos os mundos de um jogador
     *
     * @param playerUUID UUID do jogador
     * @return Lista de mundos do jogador
     */
    public List<CustomWorld> getPlayerWorlds(UUID playerUUID) {
        return loadedWorlds.values().stream()
                .filter(world -> world.getOwnerUUID().equals(playerUUID))
                .collect(Collectors.toList());
    }

    /**
     * Obtém todos os mundos que um jogador tem acesso
     *
     * @param playerUUID UUID do jogador
     * @return Lista de mundos acessíveis
     */
    public List<CustomWorld> getAccessibleWorlds(UUID playerUUID) {
        return loadedWorlds.values().stream()
                .filter(world -> world.getOwnerUUID().equals(playerUUID) ||
                        world.getTrustedPlayers().contains(playerUUID))
                .collect(Collectors.toList());
    }

    /**
     * Obtém um mundo pelo nome
     *
     * @param worldName Nome do mundo
     * @return CustomWorld ou null se não encontrado
     */
    public CustomWorld getWorldByName(String worldName) {
        return loadedWorlds.get(worldName);
    }

    /**
     * Obtém todos os mundos carregados
     *
     * @return Coleção imutável de mundos
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
    }

    /**
     * Método auxiliar para descarregar um mundo
     *
     * @param worldName Nome do mundo
     * @param save Salvar o mundo antes de descarregar
     * @return true se o mundo foi descarregado com sucesso
     */
    private boolean unloadWorld(String worldName, boolean save) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return true;
        }

        // Teleporta jogadores para fora do mundo
        World defaultWorld = Bukkit.getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(defaultWorld.getSpawnLocation());
        }

        return Bukkit.unloadWorld(world, save);
    }

    /**
     * Método auxiliar para excluir uma pasta recursivamente
     *
     * @param folder Pasta a ser excluída
     * @throws IOException Se ocorrer um erro de IO
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
    }
}