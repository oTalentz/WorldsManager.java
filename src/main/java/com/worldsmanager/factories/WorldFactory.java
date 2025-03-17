package com.worldsmanager.factories;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import com.worldsmanager.services.ConfigService;
import com.worldsmanager.utils.ImprovedWorldCreationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory para criação de mundos personalizados
 * Implementa o padrão Factory para encapsular a lógica de criação de objetos complexos
 */
public class WorldFactory {

    private final WorldsManager plugin;
    private final ConfigService configService;
    private final Logger logger;

    /**
     * Construtor
     *
     * @param plugin Instância do plugin
     */
    public WorldFactory(WorldsManager plugin) {
        this.plugin = plugin;
        this.configService = plugin.getConfigManager();
        this.logger = plugin.getLogger();
    }

    /**
     * Cria um objeto CustomWorld com as configurações padrão
     *
     * @param displayName Nome de exibição do mundo
     * @param ownerUUID UUID do proprietário
     * @param worldName Nome interno do mundo
     * @param icon Ícone do mundo
     * @return Objeto CustomWorld configurado
     */
    public CustomWorld createCustomWorld(String displayName, UUID ownerUUID, String worldName, Material icon) {
        CustomWorld world = new CustomWorld(displayName, ownerUUID, worldName, icon);

        // Aplica configurações padrão
        WorldSettings defaultSettings = configService.getDefaultWorldSettings();
        world.setSettings(new WorldSettings(defaultSettings));

        // Define o caminho personalizado com base no nome do jogador
        String playerName = "unknown";
        if (Bukkit.getOfflinePlayer(ownerUUID).getName() != null) {
            playerName = Bukkit.getOfflinePlayer(ownerUUID).getName().toLowerCase();
        }
        world.setWorldPath(playerName);

        return world;
    }

    /**
     * Cria um nome único para o mundo com base no prefixo e UUID aleatório
     *
     * @param prefix Prefixo para o nome do mundo
     * @return Nome único para o mundo
     */
    public String generateUniqueWorldName(String prefix) {
        String worldId = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "_" + worldId;
    }

    /**
     * Cria o mundo Bukkit e retorna em um CompletableFuture
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return CompletableFuture com o mundo criado ou null se falhar
     */
    public CompletableFuture<World> createBukkitWorld(String worldName, String playerFolder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Criando mundo " + worldName + " na pasta " + playerFolder);

                // Cria o mundo no caminho personalizado
                World world = ImprovedWorldCreationUtils.createWorldInPath(
                        worldName,
                        playerFolder,
                        configService.getWorldType(),
                        configService.getWorldEnvironment(),
                        configService.isGenerateStructures()
                );

                if (world != null) {
                    logger.info("Mundo criado com sucesso: " + worldName);
                    return world;
                } else {
                    logger.severe("Falha ao criar mundo: " + worldName);
                    return null;
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao criar mundo " + worldName, e);
                return null;
            }
        });
    }

    /**
     * Carrega um mundo existente
     *
     * @param customWorld Objeto CustomWorld a ser carregado
     * @return CompletableFuture com o mundo carregado ou null se falhar
     */
    public CompletableFuture<World> loadBukkitWorld(CustomWorld customWorld) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String worldName = customWorld.getWorldName();

                // Verifica se o mundo já está carregado
                World existingWorld = Bukkit.getWorld(worldName);
                if (existingWorld != null) {
                    logger.info("Mundo já está carregado: " + worldName);
                    return existingWorld;
                }

                logger.info("Carregando mundo: " + worldName);

                // Tenta carregar do caminho personalizado na pasta do plugin
                World world = null;
                if (customWorld.getWorldPath() != null && !customWorld.getWorldPath().isEmpty()) {
                    world = ImprovedWorldCreationUtils.loadWorldFromPath(
                            worldName,
                            customWorld.getWorldPath()
                    );
                }

                // Se ainda não conseguiu, tenta o método normal
                if (world == null) {
                    world = ImprovedWorldCreationUtils.loadWorld(worldName);
                }

                if (world != null) {
                    logger.info("Mundo carregado com sucesso: " + worldName);
                } else {
                    logger.warning("Falha ao carregar mundo: " + worldName);

                    // Tentativa final usando WorldCreator
                    WorldCreator creator = new WorldCreator(worldName);
                    creator.type(configService.getWorldType());
                    creator.environment(configService.getWorldEnvironment());
                    creator.generateStructures(configService.isGenerateStructures());

                    world = creator.createWorld();

                    if (world != null) {
                        logger.info("Mundo criado com criador padrão: " + worldName);
                    } else {
                        logger.severe("Todas as tentativas de carregar o mundo falharam: " + worldName);
                    }
                }

                return world;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao carregar mundo " + customWorld.getWorldName(), e);
                return null;
            }
        });
    }

    /**
     * Exclui um mundo
     *
     * @param customWorld Objeto CustomWorld a ser excluído
     * @return CompletableFuture com o resultado da operação
     */
    public CompletableFuture<Boolean> deleteBukkitWorld(CustomWorld customWorld) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String worldName = customWorld.getWorldName();
                logger.info("Iniciando exclusão do mundo: " + worldName);

                // Teleporta todos os jogadores para fora deste mundo
                World world = customWorld.getWorld();
                if (world != null) {
                    World defaultWorld = Bukkit.getWorld("world");
                    for (org.bukkit.entity.Player player : world.getPlayers()) {
                        player.teleport(defaultWorld.getSpawnLocation());
                    }

                    // Descarrega o mundo
                    if (!Bukkit.unloadWorld(worldName, false)) {
                        logger.warning("Falha ao descarregar mundo: " + worldName);
                    }
                }

                // Remove do Multiverse se estiver registrado
                if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                    boolean registered = isRegisteredInMultiverse(worldName);
                    if (registered) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
                    }
                }

                // Exclui os arquivos do mundo da pasta do plugin
                boolean filesDeleted = false;
                if (customWorld.getWorldPath() != null && !customWorld.getWorldPath().isEmpty()) {
                    // Implementação manual de deleção de arquivos
                    File worldDir = ImprovedWorldCreationUtils.getWorldDirectoryInPath(worldName, customWorld.getWorldPath());
                    if (worldDir != null && worldDir.exists()) {
                        deleteDirectory(worldDir);
                        filesDeleted = true;
                    }
                } else {
                    // Implementação manual de deleção de arquivos
                    File worldDir = new File(ImprovedWorldCreationUtils.getWorldsBaseFolder(), worldName);
                    if (worldDir.exists()) {
                        deleteDirectory(worldDir);
                        filesDeleted = true;
                    }
                }

                if (filesDeleted) {
                    logger.info("Arquivos do mundo excluídos com sucesso: " + worldName);
                } else {
                    logger.warning("Falha ao excluir arquivos do mundo: " + worldName);
                }

                return true;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao excluir mundo " + customWorld.getWorldName(), e);
                return false;
            }
        });
    }

    /**
     * Deleta um diretório recursivamente
     *
     * @param directory Diretório a ser deletado
     * @throws IOException Se ocorrer um erro de IO
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        Files.delete(file.toPath());
                    }
                }
            }
            Files.delete(directory.toPath());
        }
    }

    /**
     * Verifica se um mundo está registrado no MultiVerse-Core
     *
     * @param worldName Nome do mundo a verificar
     * @return true se o mundo estiver registrado
     */
    private boolean isRegisteredInMultiverse(String worldName) {
        try {
            // Se o Multiverse não estiver instalado, retorna false
            if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
                return false;
            }

            // Tenta acessar a API do Multiverse usando reflexão
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

            return false;
        } catch (Exception e) {
            logger.warning("Erro ao verificar mundo no Multiverse: " + e.getMessage());
            return false;
        }
    }

    /**
     * Registra um mundo no MultiVerse-Core
     *
     * @param worldName Nome do mundo a registrar
     * @return true se o registro foi bem-sucedido
     */
    public boolean registerWithMultiverse(String worldName) {
        try {
            // Verifica se o MultiVerse-Core está presente
            if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
                logger.warning("MultiVerse-Core não encontrado. O mundo não será registrado no MultiVerse.");
                return false;
            }

            // Verifica se o mundo já está registrado
            if (isRegisteredInMultiverse(worldName)) {
                logger.info("Mundo já registrado no MultiVerse-Core: " + worldName);
                return true;
            }

            // Executa o comando multiverse para importar o mundo
            String command = "mv import " + worldName + " normal";
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            if (success) {
                logger.info("Mundo " + worldName + " registrado com sucesso no MultiVerse-Core");

                // Configura propriedades do mundo no MultiVerse
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify set generator flat " + worldName);
                return true;
            } else {
                logger.warning("Falha ao registrar mundo " + worldName + " no MultiVerse-Core");
                return false;
            }
        } catch (Exception e) {
            logger.severe("Erro ao integrar com MultiVerse-Core: " + e.getMessage());
            return false;
        }
    }
}