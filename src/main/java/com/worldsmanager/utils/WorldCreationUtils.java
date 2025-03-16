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
    }

    /**
     * Obtém a pasta base onde os mundos dos jogadores são armazenados
     * @return Pasta base dos mundos
     */
    public static File getWorldsBaseFolder() {
        if (plugin == null) {
            throw new IllegalStateException("WorldCreationUtils não foi inicializado corretamente. Chame init() primeiro.");
        }
        File worldsFolder = new File(plugin.getDataFolder(), "mundos-jogadores");
        if (!worldsFolder.exists()) {
            worldsFolder.mkdirs();
        }
        return worldsFolder;
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

            if (!playerDir.exists()) {
                boolean created = playerDir.mkdirs();
                if (created) {
                    logger.info("Diretório do jogador criado com sucesso: " + playerDir.getAbsolutePath());
                } else {
                    logger.warning("Não foi possível criar o diretório do jogador: " + playerDir.getAbsolutePath());
                }
            }

            // Primeiro, crie o mundo normalmente (no local padrão do servidor)
            WorldCreator creator = new WorldCreator(worldName);
            creator.type(worldType);
            creator.environment(environment);
            creator.generateStructures(generateStructures);

            World world = creator.createWorld();

            if (world != null) {
                logger.info("Mundo criado com sucesso: " + worldName);

                // Depois, copie o mundo para o diretório personalizado
                File originalWorldFolder = new File(Bukkit.getWorldContainer(), worldName);
                File targetWorldFolder = new File(playerDir, worldName);

                // Copie recursivamente os arquivos do mundo para o diretório personalizado
                try {
                    copyDirectory(originalWorldFolder, targetWorldFolder);
                    logger.info("Mundo copiado para a pasta personalizada: " + targetWorldFolder.getAbsolutePath());
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Erro ao copiar mundo para pasta personalizada", e);
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
                    logger.warning("Diretório do mundo não encontrado: " + worldName);
                    return null;
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

            if (!worldDir.exists() || !worldDir.isDirectory()) {
                logger.warning("Diretório do mundo não existe no caminho personalizado: " +
                        playerFolder + "/" + worldName);
                return null;
            }

            // Copiar o mundo para o diretório padrão para carregamento
            File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);
            try {
                // Se já existir, remova primeiro
                if (defaultWorldDir.exists()) {
                    deleteDirectory(defaultWorldDir);
                }
                copyDirectory(worldDir, defaultWorldDir);
                logger.info("Mundo copiado do caminho personalizado para carregamento: " + worldName);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Erro ao copiar mundo do caminho personalizado", e);
                return null;
            }

            // Criar o criador de mundo
            WorldCreator creator = new WorldCreator(worldName);

            // Carrega o mundo
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
     * Verifica se um mundo existe no sistema de arquivos (no diretório do plugin)
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

        // Verifica também no diretório padrão como fallback
        File defaultWorldDir = new File(Bukkit.getWorldContainer(), worldName);
        return defaultWorldDir.exists() && defaultWorldDir.isDirectory();
    }

    /**
     * Verifica se um mundo existe em um caminho personalizado dentro da pasta do plugin
     *
     * @param worldName Nome do mundo
     * @param playerFolder Pasta do jogador
     * @return true se o mundo existir
     */
    public static boolean worldExistsInPath(String worldName, String playerFolder) {
        // Verifica se o mundo está carregado
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }

        // Verifica se o diretório do mundo existe no caminho personalizado
        File worldsBaseFolder = getWorldsBaseFolder();
        File playerDir = new File(worldsBaseFolder, playerFolder);
        File worldDir = new File(playerDir, worldName);
        return worldDir.exists() && worldDir.isDirectory();
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

        // Copia todos os arquivos e subdiretórios
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(destDir, file.getName());

                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    copyFile(file, destFile);
                }
            }
        }
    }

    /**
     * Copia um arquivo de origem para um arquivo de destino
     *
     * @param sourceFile Arquivo de origem
     * @param destFile Arquivo de destino
     * @throws IOException Se ocorrer um erro de IO
     */
    private static void copyFile(File sourceFile, File destFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destFile);
             FileChannel source = fis.getChannel();
             FileChannel destination = fos.getChannel()) {

            destination.transferFrom(source, 0, source.size());
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

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Não foi possível excluir o arquivo: " + file);
                    }
                }
            }
        }

        if (!directory.delete()) {
            throw new IOException("Não foi possível excluir o diretório: " + directory);
        }
    }
}