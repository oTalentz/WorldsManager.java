package com.worldsmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utilitários melhorados para criação e gerenciamento de mundos
 * Implementa verificações de segurança e operações assíncronas para melhor performance
 */
public class ImprovedWorldCreationUtils {

    private static final Logger logger = Logger.getLogger("WorldsManager");
    private static Plugin plugin;
    private static File worldsBaseFolder;

    // Padrão de segurança para nomes de mundos
    private static final Pattern SAFE_WORLD_NAME = Pattern.compile("^[a-zA-Z0-9_.-]+$");
    private static final Pattern SAFE_PATH = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    /**
     * Inicializa a classe com a instância do plugin
     *
     * @param pluginInstance Instância do plugin
     */
    public static void init(Plugin pluginInstance) {
        plugin = pluginInstance;
        worldsBaseFolder = new File(plugin.getDataFolder(), "mundos-jogadores");

        if (!worldsBaseFolder.exists()) {
            boolean created = worldsBaseFolder.mkdirs();
            if (created) {
                logger.info("Pasta base de mundos criada: " + worldsBaseFolder.getAbsolutePath());
            } else {
                logger.severe("FALHA CRÍTICA: Não foi possível criar a pasta base de mundos: " + worldsBaseFolder.getAbsolutePath());
                logger.severe("Permissões de diretório pai: " + worldsBaseFolder.getParentFile().canWrite());
                logger.severe("Diretório pai existe: " + worldsBaseFolder.getParentFile().exists());
            }
        } else {
            logger.info("Pasta de mundos existente confirmada: " + worldsBaseFolder.getAbsolutePath());
        }
    }

    /**
     * Obtém a pasta base onde os mundos dos jogadores são armazenados
     *
     * @return Pasta base dos mundos
     */
    public static File getWorldsBaseFolder() {
        if (plugin == null) {
            throw new IllegalStateException("ImprovedWorldCreationUtils não foi inicializado corretamente. Chame init() primeiro.");
        }

        return worldsBaseFolder;
    }

    /**
     * Valida um nome de mundo para garantir que é seguro
     *
     * @param worldName Nome do mundo a ser validado
     * @return true se o nome for seguro
     */
    public static boolean isValidWorldName(String worldName) {
        return worldName != null && SAFE_WORLD_NAME.matcher(worldName).matches();
    }

    /**
     * Valida um caminho para garantir que é seguro
     *
     * @param path Caminho a ser validado
     * @return true se o caminho for seguro
     */
    public static boolean isValidPath(String path) {
        return path != null && SAFE_PATH.matcher(path).matches();
    }

    /**
     * Cria um novo mundo de forma assíncrona
     *
     * @param worldName Nome do mundo
     * @param worldType Tipo de mundo
     * @param environment Ambiente do mundo
     * @param generateStructures Gerar estruturas
     * @return CompletableFuture com o mundo criado ou null se falhar
     */
    public static CompletableFuture<World> createWorldAsync(String worldName, WorldType worldType,
                                                            World.Environment environment, boolean generateStructures) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<World> future = new CompletableFuture<>();

        // Realiza a criação real do mundo na thread principal, mas após a preparação
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Verifica se o mundo já existe
                    World existingWorld = Bukkit.getWorld(worldName);
                    if (existingWorld != null) {
                        logger.info("Mundo já existe, retornando o mundo existente: " + worldName);
                        future.complete(existingWorld);
                        return;
                    }

                    // Cria o mundo usando o método padrão do Bukkit
                    WorldCreator creator = new WorldCreator(worldName);
                    creator.type(worldType);
                    creator.environment(environment);
                    creator.generateStructures(generateStructures);

                    logger.info("Criando novo mundo: " + worldName);
                    World world = creator.createWorld();

                    if (world != null) {
                        logger.info("Mundo criado com sucesso: " + worldName);

                        // Agenda a cópia do mundo para a pasta do plugin de forma assíncrona
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            try {
                                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                                File targetFolder = new File(getWorldsBaseFolder(), worldName);

                                if (worldFolder.exists() && worldFolder.isDirectory()) {
                                    copyDirectoryAsync(worldFolder, targetFolder);
                                    logger.info("Mundo copiado para pasta personalizada: " + targetFolder.getAbsolutePath());
                                }
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Não foi possível copiar o mundo para a pasta personalizada", e);
                            }
                        });

                        future.complete(world);
                    } else {
                        logger.severe("Falha ao criar mundo: " + worldName);
                        future.complete(null);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Erro ao criar mundo: " + worldName, e);
                    future.completeExceptionally(e);
                }
            }
        }.runTask(plugin);

        return future;
    }

    /**
     * Cria um mundo em um caminho personalizado de forma assíncrona
     *
     * @param worldName Nome do mundo
     * @param playerFolder Nome da pasta do jogador
     * @param worldType Tipo de mundo
     * @param environment Ambiente do mundo
     * @param generateStructures Gerar estruturas
     * @return CompletableFuture com o mundo criado ou null se falhar
     */
    public static CompletableFuture<World> createWorldInPathAsync(String worldName, String playerFolder, WorldType worldType,
                                                                  World.Environment environment, boolean generateStructures) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return CompletableFuture.completedFuture(null);
        }

        if (!isValidPath(playerFolder)) {
            logger.warning("Caminho inválido ou potencialmente perigoso: " + playerFolder);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<World> future = new CompletableFuture<>();

        // Prepara diretórios de forma assíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Configura o diretório personalizado dentro da pasta do plugin
                File playerDir = new File(getWorldsBaseFolder(), playerFolder);

                // Logs detalhados para debug
                logger.info("Preparando para criar mundo em:");
                logger.info("- Base folder: " + getWorldsBaseFolder().getAbsolutePath());
                logger.info("- Player dir: " + playerDir.getAbsolutePath());
                logger.info("- World name: " + worldName);

                if (!playerDir.exists()) {
                    Files.createDirectories(playerDir.toPath());
                    logger.info("Diretório do jogador criado com sucesso: " + playerDir.getAbsolutePath());
                }

                // Cria o diretório onde o mundo será armazenado
                File worldDir = new File(playerDir, worldName);
                if (!worldDir.exists()) {
                    Files.createDirectories(worldDir.toPath());
                    logger.info("Diretório do mundo criado com sucesso: " + worldDir.getAbsolutePath());
                }

                // Agora, crie o mundo na thread principal
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            // Verifica se o mundo já existe
                            World existingWorld = Bukkit.getWorld(worldName);
                            if (existingWorld != null) {
                                logger.info("Mundo já existe, retornando o mundo existente: " + worldName);
                                future.complete(existingWorld);
                                return;
                            }

                            // Cria o mundo com o WorldCreator
                            WorldCreator creator = new WorldCreator(worldName);
                            creator.type(worldType);
                            creator.environment(environment);
                            creator.generateStructures(generateStructures);

                            logger.info("Gerando mundo " + worldName + " com WorldCreator");
                            World world = creator.createWorld();

                            if (world != null) {
                                logger.info("Mundo criado com sucesso: " + worldName);

                                // Copia os arquivos do mundo para o diretório personalizado de forma assíncrona
                                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                    try {
                                        File originalWorldFolder = new File(Bukkit.getWorldContainer(), worldName);
                                        logger.info("Copiando de: " + originalWorldFolder.getAbsolutePath());
                                        logger.info("Para: " + worldDir.getAbsolutePath());

                                        if (originalWorldFolder.exists() && originalWorldFolder.isDirectory()) {
                                            copyDirectoryAsync(originalWorldFolder, worldDir);
                                            logger.info("Mundo copiado para pasta personalizada com sucesso");
                                        } else {
                                            logger.warning("Pasta do mundo de origem não existe: " + originalWorldFolder.getAbsolutePath());
                                        }
                                    } catch (Exception e) {
                                        logger.log(Level.SEVERE, "Erro ao copiar mundo para pasta personalizada", e);
                                    }
                                });

                                future.complete(world);
                            } else {
                                logger.severe("Falha ao criar mundo: " + worldName);
                                future.complete(null);
                            }
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Erro ao criar mundo no caminho personalizado", e);
                            future.completeExceptionally(e);
                        }
                    }
                }.runTask(plugin);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao preparar diretórios para o mundo", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Carrega um mundo existente de forma assíncrona
     *
     * @param worldName Nome do mundo
     * @return CompletableFuture com o mundo carregado ou null se falhar
     */
    public static CompletableFuture<World> loadWorldAsync(String worldName) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<World> future = new CompletableFuture<>();

        // Verifica se o mundo já está carregado na thread principal
        new BukkitRunnable() {
            @Override
            public void run() {
                World existingWorld = Bukkit.getWorld(worldName);
                if (existingWorld != null) {
                    logger.info("Mundo já está carregado: " + worldName);
                    future.complete(existingWorld);
                    return;
                }

                // Prepara a cópia assíncrona se necessário
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // Verificar primeiro no diretório padrão
                        File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);
                        boolean needsCopy = false;

                        if (!defaultWorldDir.exists() || !defaultWorldDir.isDirectory()) {
                            // Verificar na pasta personalizada
                            File worldDir = new File(getWorldsBaseFolder(), worldName);
                            if (worldDir.exists() && worldDir.isDirectory()) {
                                // Copiar o mundo para o diretório padrão para carregamento
                                copyDirectoryAsync(worldDir, defaultWorldDir);
                                logger.info("Mundo copiado da pasta personalizada para carregamento: " + worldName);
                                needsCopy = true;
                            } else {
                                // Procurar no diretório mundos-jogadores/player/worldname
                                AtomicBoolean found = new AtomicBoolean(false);
                                searchForWorldAsync(worldName).thenAccept(worldPath -> {
                                    if (worldPath != null) {
                                        try {
                                            copyDirectoryAsync(worldPath.toFile(), defaultWorldDir);
                                            logger.info("Mundo encontrado em pasta personalizada e copiado para carregamento: " + worldName);
                                            found.set(true);

                                            // Carrega o mundo na thread principal após a cópia
                                            new BukkitRunnable() {
                                                @Override
                                                public void run() {
                                                    loadWorldInMainThread(worldName, future);
                                                }
                                            }.runTask(plugin);
                                        } catch (Exception e) {
                                            logger.log(Level.SEVERE, "Erro ao copiar mundo da pasta personalizada", e);
                                            future.complete(null);
                                        }
                                    } else {
                                        // Não foi possível encontrar o mundo em nenhum lugar
                                        logger.warning("Diretório do mundo não encontrado: " + worldName);
                                        future.complete(null);
                                    }
                                });

                                if (found.get()) {
                                    return; // Já está sendo tratado pelo callback acima
                                }
                            }
                        }

                        // Se não precisar de cópia ou a cópia foi concluída, carrega na thread principal
                        if (!needsCopy) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    loadWorldInMainThread(worldName, future);
                                }
                            }.runTask(plugin);
                        } else {
                            // Aguarda um pouco para garantir que a cópia assíncrona termine
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    loadWorldInMainThread(worldName, future);
                                }
                            }.runTaskLater(plugin, 20L); // 1 segundo de delay
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Erro ao preparar mundo para carregamento: " + worldName, e);
                        future.completeExceptionally(e);
                    }
                });
            }
        }.runTask(plugin);

        return future;
    }

    /**
     * Carrega um mundo em um caminho personalizado de forma assíncrona
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return CompletableFuture com o mundo carregado ou null se falhar
     */
    public static CompletableFuture<World> loadWorldFromPathAsync(String worldName, String playerFolder) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return CompletableFuture.completedFuture(null);
        }

        if (!isValidPath(playerFolder)) {
            logger.warning("Caminho inválido ou potencialmente perigoso: " + playerFolder);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<World> future = new CompletableFuture<>();

        // Verifica se o mundo já está carregado na thread principal
        new BukkitRunnable() {
            @Override
            public void run() {
                World existingWorld = Bukkit.getWorld(worldName);
                if (existingWorld != null) {
                    logger.info("Mundo já está carregado: " + worldName);
                    future.complete(existingWorld);
                    return;
                }

                // Realiza a verificação e cópia assíncrona
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // Verifica se o diretório do mundo existe no caminho personalizado
                        File worldsBaseFolder = getWorldsBaseFolder();
                        File playerDir = new File(worldsBaseFolder, playerFolder);
                        File worldDir = new File(playerDir, worldName);

                        logger.info("Tentando carregar mundo de: " + worldDir.getAbsolutePath());

                        if (!worldDir.exists() || !worldDir.isDirectory()) {
                            logger.warning("Diretório do mundo não existe no caminho especificado: " + worldDir.getAbsolutePath());
                            future.complete(null);
                            return;
                        }

                        // Copiar o mundo para o diretório padrão para carregamento
                        File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);

                        // Se já existir, remova primeiro
                        if (defaultWorldDir.exists()) {
                            deleteDirectoryAsync(defaultWorldDir);
                        }

                        logger.info("Copiando mundo de " + worldDir.getAbsolutePath() +
                                " para " + defaultWorldDir.getAbsolutePath());

                        copyDirectoryAsync(worldDir, defaultWorldDir);
                        logger.info("Mundo copiado do caminho personalizado para carregamento: " + worldName);

                        // Carrega o mundo na thread principal após um delay para garantir que a cópia termine
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                loadWorldInMainThread(worldName, future);
                            }
                        }.runTaskLater(plugin, 20L); // 1 segundo de delay
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Erro ao preparar mundo para carregamento: " + worldName, e);
                        future.completeExceptionally(e);
                    }
                });
            }
        }.runTask(plugin);

        return future;
    }

    /**
     * Carrega o mundo na thread principal
     *
     * @param worldName Nome do mundo
     * @param future CompletableFuture para completar com o resultado
     */
    private static void loadWorldInMainThread(String worldName, CompletableFuture<World> future) {
        try {
            // Criar o criador de mundo
            WorldCreator creator = new WorldCreator(worldName);

            // Carrega o mundo
            logger.info("Carregando mundo: " + worldName);
            World world = creator.createWorld();

            if (world != null) {
                logger.info("Mundo carregado com sucesso: " + worldName);
                future.complete(world);
            } else {
                logger.severe("Falha ao carregar mundo: " + worldName);
                future.complete(null);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao carregar mundo na thread principal: " + worldName, e);
            future.completeExceptionally(e);
        }
    }

    /**
     * Exclui um mundo
     *
     * @param worldName Nome do mundo
     * @return true se a exclusão foi bem-sucedida
     */
    public static boolean deleteWorldFiles(String worldName) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return false;
        }

        try {
            File worldFile = new File(getWorldsBaseFolder(), worldName);
            if (worldFile.exists()) {
                deleteDirectoryAsync(worldFile);
                logger.info("Arquivos do mundo excluídos: " + worldFile.getAbsolutePath());
                return true;
            } else {
                logger.warning("Pasta do mundo não encontrada: " + worldFile.getAbsolutePath());
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao excluir pasta do mundo", e);
            return false;
        }
    }

    /**
     * Exclui um mundo em um caminho personalizado
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return true se a exclusão foi bem-sucedida
     */
    public static boolean deleteWorldFiles(String worldName, String playerFolder) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return false;
        }

        if (!isValidPath(playerFolder)) {
            logger.warning("Caminho inválido ou potencialmente perigoso: " + playerFolder);
            return false;
        }

        try {
            File worldsBaseFolder = getWorldsBaseFolder();
            File playerDir = new File(worldsBaseFolder, playerFolder);
            File worldFile = new File(playerDir, worldName);

            if (worldFile.exists()) {
                deleteDirectoryAsync(worldFile);
                logger.info("Arquivos do mundo excluídos: " + worldFile.getAbsolutePath());
                return true;
            } else {
                logger.warning("Pasta do mundo não encontrada: " + worldFile.getAbsolutePath());
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao excluir pasta do mundo", e);
            return false;
        }
    }

    /**
     * Copia um diretório recursivamente usando NIO para melhor performance
     *
     * @param source Diretório de origem
     * @param destination Diretório de destino
     * @throws IOException Se ocorrer um erro durante a cópia
     */
    public static void copyDirectoryAsync(File source, File destination) throws IOException {
        if (!source.exists() || !source.isDirectory()) {
            throw new IOException("Diretório de origem não existe ou não é um diretório: " + source);
        }

        if (!destination.exists()) {
            Files.createDirectories(destination.toPath());
        }

        Files.walkFileTree(source.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = destination.toPath().resolve(source.toPath().relativize(dir));
                try {
                    Files.createDirectories(targetDir);
                } catch (FileAlreadyExistsException e) {
                    if (!Files.isDirectory(targetDir)) {
                        throw e;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destination.toPath().resolve(source.toPath().relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Exclui um diretório recursivamente usando NIO
     *
     * @param directory Diretório a ser excluído
     * @throws IOException Se ocorrer um erro durante a exclusão
     */
    public static void deleteDirectoryAsync(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Verifica se um mundo existe no sistema de arquivos
     *
     * @param worldName Nome do mundo
     * @return true se o mundo existir
     */
    public static boolean worldExists(String worldName) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return false;
        }

        // Verifica se o mundo está carregado
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }

        // Verifica se o diretório do mundo existe na pasta do plugin
        File worldDir = new File(getWorldsBaseFolder(), worldName);
        if (worldDir.exists() && worldDir.isDirectory()) {
            return true;
        }

        // Verifica no diretório padrão
        File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);
        if (defaultWorldDir.exists() && defaultWorldDir.isDirectory()) {
            return true;
        }

        // Verifica em caminhos personalizados de forma assíncrona
        try {
            return searchForWorldAsync(worldName).get() != null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro ao procurar mundo em caminhos personalizados", e);
            return false;
        }
    }

    /**
     * Procura um mundo em todas as subpastas do diretório base
     *
     * @param worldName Nome do mundo
     * @return CompletableFuture com o caminho do mundo encontrado ou null se não encontrado
     */
    public static CompletableFuture<Path> searchForWorldAsync(String worldName) {
        CompletableFuture<Path> future = new CompletableFuture<>();

        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final List<Path> foundPaths = new ArrayList<>();

                Files.walkFileTree(getWorldsBaseFolder().toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (dir.getFileName().toString().equals(worldName)) {
                            // Verifica se é realmente um mundo checando o level.dat
                            if (Files.exists(dir.resolve("level.dat"))) {
                                foundPaths.add(dir);
                                return FileVisitResult.TERMINATE;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

                if (!foundPaths.isEmpty()) {
                    future.complete(foundPaths.get(0));
                } else {
                    future.complete(null);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao procurar mundo: " + worldName, e);
                future.complete(null);
            }
        });

        return future;
    }

    /**
     * Cria o mundo no diretório padrão e copia para o diretório personalizado
     * Método de compatibilidade para a versão antiga
     *
     * @param worldName Nome do mundo
     * @param worldType Tipo de mundo
     * @param environment Ambiente do mundo
     * @param generateStructures Gerar estruturas
     * @return O mundo criado ou null se falhar
     */
    public static World createWorld(String worldName, WorldType worldType,
                                    World.Environment environment, boolean generateStructures) {
        try {
            return createWorldAsync(worldName, worldType, environment, generateStructures).get();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao criar mundo", e);
            return null;
        }
    }

    /**
     * Cria um mundo em um caminho personalizado
     * Método de compatibilidade para a versão antiga
     *
     * @param worldName Nome do mundo
     * @param playerFolder Nome da pasta do jogador
     * @param worldType Tipo de mundo
     * @param environment Ambiente do mundo
     * @param generateStructures Gerar estruturas
     * @return O mundo criado ou null se falhar
     */
    public static World createWorldInPath(String worldName, String playerFolder, WorldType worldType,
                                          World.Environment environment, boolean generateStructures) {
        try {
            return createWorldInPathAsync(worldName, playerFolder, worldType,
                    environment, generateStructures).get();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao criar mundo em caminho personalizado", e);
            return null;
        }
    }

    /**
     * Carrega um mundo existente
     * Método de compatibilidade para a versão antiga
     *
     * @param worldName Nome do mundo
     * @return O mundo carregado ou null se falhar
     */
    public static World loadWorld(String worldName) {
        try {
            return loadWorldAsync(worldName).get();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao carregar mundo", e);
            return null;
        }
    }

    /**
     * Carrega um mundo existente de um caminho personalizado
     * Método de compatibilidade para a versão antiga
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return O mundo carregado ou null se falhar
     */
    public static World loadWorldFromPath(String worldName, String playerFolder) {
        try {
            return loadWorldFromPathAsync(worldName, playerFolder).get();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao carregar mundo de caminho personalizado", e);
            return null;
        }
    }

    /**
     * Obtém o diretório de um mundo em um caminho personalizado
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return File representando o diretório do mundo
     */
    public static File getWorldDirectoryInPath(String worldName, String playerFolder) {
        // Validação de segurança
        if (!isValidWorldName(worldName)) {
            logger.warning("Nome de mundo inválido ou potencialmente perigoso: " + worldName);
            return null;
        }

        if (!isValidPath(playerFolder)) {
            logger.warning("Caminho inválido ou potencialmente perigoso: " + playerFolder);
            return null;
        }

        File worldsBaseFolder = getWorldsBaseFolder();
        File playerDir = new File(worldsBaseFolder, playerFolder);
        return new File(playerDir, worldName);
    }
}