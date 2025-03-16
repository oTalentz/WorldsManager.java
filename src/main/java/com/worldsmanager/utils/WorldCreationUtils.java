package com.worldsmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.File;
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
     * Obtém o caminho para o diretório de um mundo
     *
     * @param worldName Nome do mundo
     * @return File representando o diretório do mundo
     */
    public static File getWorldDirectory(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName);
    }
}