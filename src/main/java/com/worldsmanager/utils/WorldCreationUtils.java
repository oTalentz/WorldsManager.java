package com.worldsmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.File;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitários para criação e gerenciamento de mundos
 */
public class WorldCreationUtils {

    private static final Logger logger = Logger.getLogger("WorldsManager");

    /**
     * Cria um novo mundo
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

            // Cria o mundo
            WorldCreator creator = new WorldCreator(worldName);
            creator.type(worldType);
            creator.environment(environment);
            creator.generateStructures(generateStructures);

            // Tenta criar o mundo
            World world = creator.createWorld();
            if (world != null) {
                logger.info("Mundo criado com sucesso: " + worldName);
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
     * Cria um mundo em um caminho personalizado
     *
     * @param worldName Nome do mundo
     * @param worldPath Caminho personalizado
     * @param worldType Tipo de mundo
     * @param environment Ambiente do mundo
     * @param generateStructures Gerar estruturas
     * @return O mundo criado ou null se falhar
     */
    public static World createWorldInPath(String worldName, String worldPath, WorldType worldType,
                                          World.Environment environment, boolean generateStructures) {
        try {
            // Verifica se o mundo já existe
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                logger.info("Mundo já existe, retornando o mundo existente: " + worldName);
                return existingWorld;
            }

            // Configura o diretório personalizado
            File customDir = new File(Bukkit.getWorldContainer().getParentFile(), worldPath);
            if (!customDir.exists()) {
                boolean created = customDir.mkdirs();
                if (created) {
                    logger.info("Diretório criado com sucesso: " + customDir.getAbsolutePath());
                } else {
                    logger.warning("Não foi possível criar o diretório: " + customDir.getAbsolutePath());
                }
            }

            // Cria o diretório específico do mundo
            File worldDir = new File(customDir, worldName);
            if (!worldDir.exists()) {
                boolean created = worldDir.mkdirs();
                if (created) {
                    logger.info("Diretório do mundo criado: " + worldDir.getAbsolutePath());
                }
            }

            // Configura o WorldCreator com a pasta personalizada
            WorldCreator creator = WorldCreator.name(worldName);
            creator.type(worldType);
            creator.environment(environment);
            creator.generateStructures(generateStructures);

            // Define o diretório personalizado através de uma propriedade do servidor
            String originalWorldContainer = System.getProperty("user.dir");
            System.setProperty("user.dir", customDir.getAbsolutePath());

            // Cria o mundo
            World world = creator.createWorld();

            // Restaura a propriedade original
            System.setProperty("user.dir", originalWorldContainer);

            if (world != null) {
                logger.info("Mundo criado com sucesso em caminho personalizado: " + worldPath + "/" + worldName);
            } else {
                logger.severe("Falha ao criar mundo em caminho personalizado: " + worldPath + "/" + worldName);
            }

            return world;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao criar mundo em caminho personalizado: " + worldPath + "/" + worldName, e);
            return null;
        }
    }

    /**
     * Carrega um mundo existente
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

            // Verifica se o diretório do mundo existe
            File worldDir = new File(Bukkit.getWorldContainer(), worldName);
            if (!worldDir.exists() || !worldDir.isDirectory()) {
                logger.warning("Diretório do mundo não existe: " + worldName);
                return null;
            }

            // Cria o criador de mundo
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
     * Carrega um mundo existente de um caminho personalizado
     *
     * @param worldName Nome do mundo
     * @param worldPath Caminho personalizado
     * @return O mundo carregado ou null se falhar
     */
    public static World loadWorldFromPath(String worldName, String worldPath) {
        try {
            // Verifica se o mundo já está carregado
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                logger.info("Mundo já está carregado: " + worldName);
                return existingWorld;
            }

            // Verifica se o diretório do mundo existe no caminho personalizado
            File customDir = new File(Bukkit.getWorldContainer().getParentFile(), worldPath);
            File worldDir = new File(customDir, worldName);

            if (!worldDir.exists() || !worldDir.isDirectory()) {
                logger.warning("Diretório do mundo não existe no caminho personalizado: " + worldPath + "/" + worldName);
                return null;
            }

            // Configura o WorldCreator
            WorldCreator creator = WorldCreator.name(worldName);

            // Define o diretório personalizado através de uma propriedade do servidor
            String originalWorldContainer = System.getProperty("user.dir");
            System.setProperty("user.dir", customDir.getAbsolutePath());

            // Carrega o mundo
            World world = creator.createWorld();

            // Restaura a propriedade original
            System.setProperty("user.dir", originalWorldContainer);

            if (world != null) {
                logger.info("Mundo carregado com sucesso do caminho personalizado: " + worldPath + "/" + worldName);
            } else {
                logger.severe("Falha ao carregar mundo do caminho personalizado: " + worldPath + "/" + worldName);
            }

            return world;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao carregar mundo do caminho personalizado: " + worldPath + "/" + worldName, e);
            return null;
        }
    }

    /**
     * Verifica se um mundo existe (mesmo que não esteja carregado)
     *
     * @param worldName Nome do mundo
     * @return true se o mundo existir
     */
    public static boolean worldExists(String worldName) {
        // Verifica se o mundo está carregado
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }

        // Verifica se o diretório do mundo existe
        File worldDir = new File(Bukkit.getWorldContainer(), worldName);
        return worldDir.exists() && worldDir.isDirectory();
    }

    /**
     * Verifica se um mundo existe em um caminho personalizado
     *
     * @param worldName Nome do mundo
     * @param worldPath Caminho personalizado
     * @return true se o mundo existir
     */
    public static boolean worldExistsInPath(String worldName, String worldPath) {
        // Verifica se o mundo está carregado
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }

        // Verifica se o diretório do mundo existe no caminho personalizado
        File customDir = new File(Bukkit.getWorldContainer().getParentFile(), worldPath);
        File worldDir = new File(customDir, worldName);
        return worldDir.exists() && worldDir.isDirectory();
    }

    /**
     * Obtém o caminho para o diretório de um mundo
     *
     * @param worldName Nome do mundo
     * @return File representando o diretório do mundo
     */
    public static File getWorldDirectory(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName);
    }

    /**
     * Obtém o caminho para o diretório de um mundo em um caminho personalizado
     *
     * @param worldName Nome do mundo
     * @param worldPath Caminho personalizado
     * @return File representando o diretório do mundo
     */
    public static File getWorldDirectoryInPath(String worldName, String worldPath) {
        File customDir = new File(Bukkit.getWorldContainer().getParentFile(), worldPath);
        return new File(customDir, worldName);
    }
}