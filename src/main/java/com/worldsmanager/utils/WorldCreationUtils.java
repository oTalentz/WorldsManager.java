package com.worldsmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitários para criação e gerenciamento de mundos
 */
public class WorldCreationUtils {

    private static final Logger logger = Logger.getLogger("WorldsManager");
    private static Plugin plugin;

    /**
     * Inicializa a classe com a instância do plugin
     * @param plugin Instância do plugin
     */
    public static void init(Plugin plugin) {
        WorldCreationUtils.plugin = plugin;

        // Garante que a pasta de mundos existe e cria se não existir
        File worldsFolder = getWorldsBaseFolder();
        if (!worldsFolder.exists()) {
            boolean created = worldsFolder.mkdirs();
            if (created) {
                logger.info("Pasta base de mundos criada: " + worldsFolder.getAbsolutePath());
            } else {
                logger.severe("FALHA CRÍTICA: Não foi possível criar a pasta base de mundos: " + worldsFolder.getAbsolutePath());
                // Log detalhado para debug
                logger.severe("Permissões de diretório pai: " + worldsFolder.getParentFile().canWrite());
                logger.severe("Diretório pai existe: " + worldsFolder.getParentFile().exists());
            }
        } else {
            logger.info("Pasta de mundos existente confirmada: " + worldsFolder.getAbsolutePath());
        }
    }

    /**
     * Obtém a pasta base onde os mundos dos jogadores são armazenados
     * @return Pasta base dos mundos
     */
    public static File getWorldsBaseFolder() {
        if (plugin == null) {
            throw new IllegalStateException("WorldCreationUtils não foi inicializado corretamente. Chame init() primeiro.");
        }

        // Garante que o caminho seja absoluto e bem formado
        File pluginDataFolder = plugin.getDataFolder();
        File worldsFolder = new File(pluginDataFolder, "mundos-jogadores");

        try {
            // Convertendo para caminho canônico para resolver possíveis problemas com ".." no caminho
            return worldsFolder.getCanonicalFile();
        } catch (IOException e) {
            logger.warning("Não foi possível resolver caminho canônico: " + e.getMessage());
            return worldsFolder.getAbsoluteFile();
        }
    }

    /**
     * Cria um novo mundo dentro da pasta do plugin
     *
     * @param worldName Nome do mundo
     * @param worldType Tipo de mundo
     * @param environment Ambiente do mundo
     * @param generateStructures Gerar estruturas
     * @return O mundo criado ou null se falhar
     */
    public static World createWorld(String worldName, WorldType worldType, World.Environment environment, boolean generateStructures) {
        try {
            // Verifica se o mundo já existe
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                logger.info("Mundo já existe, retornando o mundo existente: " + worldName);
                return existingWorld;
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

                // Após criar o mundo, copia-o para a pasta do plugin
                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                File targetFolder = new File(getWorldsBaseFolder(), worldName);

                // Copia os arquivos do mundo para a pasta personalizada
                try {
                    if (worldFolder.exists() && worldFolder.isDirectory()) {
                        copyDirectory(worldFolder, targetFolder);
                        logger.info("Mundo copiado para pasta personalizada: " + targetFolder.getAbsolutePath());
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Não foi possível copiar o mundo para a pasta personalizada", e);
                }
            } else {
                logger.severe("Falha ao criar mundo: " + worldName);
            }

            return world;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao criar mundo: " + worldName, e);
            return null;
        }
    }

    /**
     * Cria um mundo em um caminho personalizado dentro da pasta de mundos do plugin
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
            // Verifica se o mundo já existe
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                logger.info("Mundo já existe, retornando o mundo existente: " + worldName);
                return existingWorld;
            }

            // Configura o diretório personalizado dentro da pasta do plugin
            File worldsBaseFolder = getWorldsBaseFolder();
            File playerDir = new File(worldsBaseFolder, playerFolder);

            // Logs detalhados para debug
            logger.info("Tentando criar mundo em:");
            logger.info("- Base folder: " + worldsBaseFolder.getAbsolutePath());
            logger.info("- Player dir: " + playerDir.getAbsolutePath());
            logger.info("- World name: " + worldName);

            if (!playerDir.exists()) {
                boolean created = playerDir.mkdirs();
                if (created) {
                    logger.info("Diretório do jogador criado com sucesso: " + playerDir.getAbsolutePath());
                } else {
                    logger.severe("ERRO CRÍTICO: Não foi possível criar o diretório do jogador: " + playerDir.getAbsolutePath());
                    // Tenta criar com permissões explícitas
                    try {
                        Files.createDirectories(playerDir.toPath());
                        logger.info("Diretório criado usando Files.createDirectories: " + playerDir.getAbsolutePath());
                    } catch (IOException e) {
                        logger.severe("Falha completa ao criar diretório: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            // Cria o diretório onde o mundo será armazenado
            File worldDir = new File(playerDir, worldName);
            if (!worldDir.exists()) {
                boolean created = worldDir.mkdirs();
                if (created) {
                    logger.info("Diretório do mundo criado com sucesso: " + worldDir.getAbsolutePath());
                } else {
                    logger.severe("ERRO CRÍTICO: Não foi possível criar o diretório do mundo: " + worldDir.getAbsolutePath());
                    try {
                        Files.createDirectories(worldDir.toPath());
                        logger.info("Diretório do mundo criado usando Files.createDirectories: " + worldDir.getAbsolutePath());
                    } catch (IOException e) {
                        logger.severe("Falha completa ao criar diretório do mundo: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            logger.info("Criando mundo em caminho personalizado: " + worldDir.getAbsolutePath());

            // Primeiro, crie o mundo normalmente (no local padrão do servidor)
            WorldCreator creator = new WorldCreator(worldName);
            creator.type(worldType);
            creator.environment(environment);
            creator.generateStructures(generateStructures);

            // Cria o mundo
            logger.info("Gerando mundo " + worldName + " com WorldCreator");
            World world = creator.createWorld();

            if (world != null) {
                logger.info("Mundo criado com sucesso: " + worldName);

                // Depois, copie o mundo para o diretório personalizado
                File originalWorldFolder = new File(Bukkit.getWorldContainer(), worldName);
                logger.info("Mundo criado em: " + originalWorldFolder.getAbsolutePath());
                logger.info("Copiando para: " + worldDir.getAbsolutePath());

                // Copie recursivamente os arquivos do mundo para o diretório personalizado
                try {
                    // Primeiro, verifique se o diretório de origem existe
                    if (!originalWorldFolder.exists() || !originalWorldFolder.isDirectory()) {
                        logger.severe("ERRO CRÍTICO: Pasta do mundo de origem não existe: " + originalWorldFolder.getAbsolutePath());
                        // Tentar criar algum conteúdo para garantir que o diretório exista
                        if (!worldDir.exists()) {
                            worldDir.mkdirs();
                        }
                        File levelDat = new File(worldDir, "level.dat");
                        if (!levelDat.exists()) {
                            try {
                                levelDat.createNewFile();
                                logger.info("Arquivo level.dat criado como marcador");
                            } catch (IOException e) {
                                logger.severe("Não foi possível criar arquivo level.dat: " + e.getMessage());
                            }
                        }
                    } else {
                        copyDirectory(originalWorldFolder, worldDir);
                        logger.info("Mundo copiado para a pasta personalizada: " + worldDir.getAbsolutePath());

                        // Verifica se a cópia foi bem-sucedida
                        File[] files = worldDir.listFiles();
                        if (files != null) {
                            logger.info("Conteúdo da pasta de destino:");
                            for (File file : files) {
                                logger.info("- " + file.getName() + " (" + file.length() + " bytes)");
                            }
                        } else {
                            logger.severe("Pasta de destino vazia ou não pode ser listada!");
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Erro ao copiar mundo para pasta personalizada", e);
                }

                return world;
            } else {
                logger.severe("Falha ao criar mundo: " + worldName);
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao criar mundo no caminho: " + playerFolder + "/" + worldName, e);
            return null;
        }
    }

    /**
     * Verifica se um mundo existe no sistema de arquivos
     *
     * @param worldName Nome do mundo
     * @return true se o mundo existir
     */
    public static boolean worldExists(String worldName) {
        // Verifica se o mundo está carregado
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }

        // Verifica se o diretório do mundo existe na pasta do plugin
        File worldDir = new File(getWorldsBaseFolder(), worldName);
        if (worldDir.exists() && worldDir.isDirectory()) {
            return true;
        }

        // Procura no diretório mundos-jogadores/player/worldname
        File worldsBaseFolder = getWorldsBaseFolder();
        File[] playerDirs = worldsBaseFolder.listFiles(File::isDirectory);

        if (playerDirs != null) {
            for (File playerDir : playerDirs) {
                File possibleWorldDir = new File(playerDir, worldName);
                if (possibleWorldDir.exists() && possibleWorldDir.isDirectory()) {
                    return true;
                }
            }
        }

        // Verifica também no diretório padrão como fallback
        File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);
        return defaultWorldDir.exists() && defaultWorldDir.isDirectory();
    }

    /**
     * Obtém o caminho para o diretório de um mundo dentro da pasta do plugin
     *
     * @param worldName Nome do mundo
     * @return File representando o diretório do mundo
     */
    public static File getWorldDirectory(String worldName) {
        return new File(getWorldsBaseFolder(), worldName);
    }

    /**
     * Obtém o caminho para o diretório de um mundo em um caminho personalizado
     * dentro da pasta do plugin
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return File representando o diretório do mundo
     */
    public static File getWorldDirectoryInPath(String worldName, String playerFolder) {
        File worldsBaseFolder = getWorldsBaseFolder();
        File playerDir = new File(worldsBaseFolder, playerFolder);
        return new File(playerDir, worldName);
    }

    /**
     * Carrega um mundo existente da pasta de mundos do plugin
     *
     * @param worldName Nome do mundo
     * @return O mundo carregado ou null se falhar
     */
    public static World loadWorld(String worldName) {
        try {
            // Verifica se o mundo já está carregado
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                logger.info("Mundo já está carregado: " + worldName);
                return existingWorld;
            }

            // Verificar primeiro no diretório padrão
            File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);
            if (!defaultWorldDir.exists() || !defaultWorldDir.isDirectory()) {
                // Verificar na pasta personalizada
                File worldDir = new File(getWorldsBaseFolder(), worldName);
                if (worldDir.exists() && worldDir.isDirectory()) {
                    // Copiar o mundo para o diretório padrão para carregamento
                    try {
                        copyDirectory(worldDir, defaultWorldDir);
                        logger.info("Mundo copiado da pasta personalizada para carregamento: " + worldName);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Erro ao copiar mundo da pasta personalizada", e);
                        return null;
                    }
                } else {
                    // Procurar no diretório mundos-jogadores/player/worldname
                    File worldsBaseFolder = getWorldsBaseFolder();
                    File[] playerDirs = worldsBaseFolder.listFiles(File::isDirectory);

                    if (playerDirs != null) {
                        for (File playerDir : playerDirs) {
                            File possibleWorldDir = new File(playerDir, worldName);
                            if (possibleWorldDir.exists() && possibleWorldDir.isDirectory()) {
                                try {
                                    copyDirectory(possibleWorldDir, defaultWorldDir);
                                    logger.info("Mundo encontrado em " + playerDir.getName() +
                                            " e copiado para carregamento: " + worldName);
                                    break;
                                } catch (IOException e) {
                                    logger.log(Level.SEVERE, "Erro ao copiar mundo da pasta personalizada", e);
                                    return null;
                                }
                            }
                        }
                    }

                    // Se ainda não encontrou, falha
                    if (!defaultWorldDir.exists()) {
                        logger.warning("Diretório do mundo não encontrado: " + worldName);
                        return null;
                    }
                }
            }

            // Criar o criador de mundo
            WorldCreator creator = new WorldCreator(worldName);

            // Carrega o mundo
            World world = creator.createWorld();

            if (world != null) {
                logger.info("Mundo carregado com sucesso: " + worldName);
            } else {
                logger.severe("Falha ao carregar mundo: " + worldName);
            }

            return world;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao carregar mundo: " + worldName, e);
            return null;
        }
    }

    /**
     * Carrega um mundo existente de um caminho personalizado dentro da pasta do plugin
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return O mundo carregado ou null se falhar
     */
    public static World loadWorldFromPath(String worldName, String playerFolder) {
        try {
            // Verifica se o mundo já está carregado
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                logger.info("Mundo já está carregado: " + worldName);
                return existingWorld;
            }

            // Verifica se o diretório do mundo existe no caminho personalizado
            File worldsBaseFolder = getWorldsBaseFolder();
            File playerDir = new File(worldsBaseFolder, playerFolder);
            File worldDir = new File(playerDir, worldName);

            // Log detalhado para diagnosticar problemas
            logger.info("Tentando carregar mundo de: " + worldDir.getAbsolutePath());
            if (!worldDir.exists()) {
                logger.warning("Diretório do mundo não existe no caminho especificado: " + worldDir.getAbsolutePath());

                // Tentar encontrar o mundo procurando em todas as subpastas
                logger.info("Tentando encontrar mundo em todas as subpastas de " + worldsBaseFolder.getAbsolutePath());
                WorldLocator locator = new WorldLocator(worldName);
                try {
                    Files.walkFileTree(worldsBaseFolder.toPath(), locator);
                    if (locator.getFoundWorldPath() != null) {
                        worldDir = locator.getFoundWorldPath().toFile();
                        logger.info("Mundo encontrado em: " + worldDir.getAbsolutePath());
                    } else {
                        logger.warning("Mundo não encontrado em nenhuma subpasta.");
                        return null;
                    }
                } catch (IOException e) {
                    logger.severe("Erro ao procurar mundo: " + e.getMessage());
                    return null;
                }
            }

            // Copiar o mundo para o diretório padrão para carregamento
            File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);
            try {
                // Se já existir, remova primeiro
                if (defaultWorldDir.exists()) {
                    deleteDirectory(defaultWorldDir);
                }

                logger.info("Copiando mundo de " + worldDir.getAbsolutePath() +
                        " para " + defaultWorldDir.getAbsolutePath());

                copyDirectory(worldDir, defaultWorldDir);
                logger.info("Mundo copiado do caminho personalizado para carregamento: " + worldName);

                // Verifica conteúdo
                File[] files = defaultWorldDir.listFiles();
                if (files != null) {
                    logger.info("Arquivos copiados para pasta de carregamento:");
                    for (File file : files) {
                        logger.info("- " + file.getName());
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Erro ao copiar mundo do caminho personalizado", e);
                return null;
            }

            // Criar o criador de mundo
            WorldCreator creator = new WorldCreator(worldName);

            // Carrega o mundo
            logger.info("Carregando mundo: " + worldName);
            World world = creator.createWorld();

            if (world != null) {
                logger.info("Mundo carregado com sucesso do caminho personalizado: " +
                        playerFolder + "/" + worldName);
            } else {
                logger.severe("Falha ao carregar mundo do caminho personalizado: " +
                        playerFolder + "/" + worldName);
            }

            return world;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao carregar mundo do caminho personalizado: " +
                    playerFolder + "/" + worldName, e);
            return null;
        }
    }

    /**
     * Classe auxiliar para localizar um mundo em subpastas
     */
    private static class WorldLocator extends SimpleFileVisitor<Path> {
        private final String worldName;
        private Path foundWorldPath;

        public WorldLocator(String worldName) {
            this.worldName = worldName;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (dir.getFileName().toString().equals(worldName)) {
                // Verifica se é realmente um mundo checando o level.dat
                if (Files.exists(dir.resolve("level.dat"))) {
                    foundWorldPath = dir;
                    return FileVisitResult.TERMINATE;
                }
            }
            return FileVisitResult.CONTINUE;
        }

        public Path getFoundWorldPath() {
            return foundWorldPath;
        }
    }

    /**
     * Copia recursivamente um diretório e seu conteúdo
     *
     * @param sourceDir Diretório de origem
     * @param destDir Diretório de destino
     * @throws IOException Se ocorrer um erro de IO
     */
    private static void copyDirectory(File sourceDir, File destDir) throws IOException {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new IOException("Diretório de origem não existe ou não é um diretório: " + sourceDir);
        }

        if (!destDir.exists()) {
            if (!destDir.mkdirs()) {
                throw new IOException("Não foi possível criar o diretório de destino: " + destDir);
            }
        }

        // Tenta usar NIO2 para cópia mais rápida
        try {
            Path sourcePath = sourceDir.toPath();
            Path destPath = destDir.toPath();

            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = destPath.resolve(sourcePath.relativize(dir));
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
                    Files.copy(file, destPath.resolve(sourcePath.relativize(file)),
                            StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            logger.warning("Falha ao usar NIO2 para cópia, usando método alternativo: " + e.getMessage());

            // Método tradicional como fallback
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    File destFile = new File(destDir, file.getName());

                    if (file.isDirectory()) {
                        copyDirectory(file, destFile);
                    } else {
                        try (FileInputStream fis = new FileInputStream(file);
                             FileOutputStream fos = new FileOutputStream(destFile);
                             FileChannel source = fis.getChannel();
                             FileChannel destination = fos.getChannel()) {

                            destination.transferFrom(source, 0, source.size());
                        }
                    }
                }
            }
        }
    }

    /**
     * Exclui recursivamente um diretório e seu conteúdo
     *
     * @param directory Diretório a ser excluído
     * @throws IOException Se ocorrer um erro de IO
     */
    private static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        try {
            // Tenta usar NIO2 para exclusão
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
        } catch (Exception e) {
            logger.warning("Falha ao usar NIO2 para exclusão, usando método alternativo: " + e.getMessage());

            // Método tradicional como fallback
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            logger.warning("Não foi possível excluir o arquivo: " + file);
                        }
                    }
                }
            }

            if (!directory.delete()) {
                logger.warning("Não foi possível excluir o diretório: " + directory);
            }
        }
    }
}