package com.worldsmanager.listeners;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import com.worldsmanager.utils.WorldCreationUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener para processamento de mensagens entre servidores
 */
public class WorldsMessageListener implements PluginMessageListener, Listener {

    private final WorldsManager plugin;
    private final String PLUGIN_CHANNEL = "WorldsManager";

    public WorldsMessageListener(WorldsManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String subchannel = in.readUTF();

            if (!subchannel.equals("Forward")) {
                return;
            }

            // Lê o servidor de destino (ignoramos, pois já chegou ao destino)
            in.readUTF();

            // Lê o canal do plugin
            String pluginChannel = in.readUTF();
            if (!pluginChannel.equals(PLUGIN_CHANNEL)) {
                return;
            }

            // Lê o tamanho do array de dados
            short dataLength = in.readShort();
            byte[] data = new byte[dataLength];
            in.readFully(data);

            // Processa a mensagem
            processPluginMessage(data);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao processar mensagem do BungeeCord", e);
        }
    }

    /**
     * Processa uma mensagem do plugin
     *
     * @param data Dados da mensagem
     */
    private void processPluginMessage(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            String action = in.readUTF();

            switch (action) {
                case "CreateWorld":
                    handleCreateWorld(in);
                    break;
                case "TeleportToWorld":
                    handleTeleportToWorld(in);
                    break;
                case "DeleteWorld":
                    handleDeleteWorld(in);
                    break;
                case "UpdateWorldSettings":
                    handleUpdateWorldSettings(in);
                    break;
                default:
                    plugin.getLogger().warning("Ação desconhecida recebida: " + action);
                    break;
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao processar dados da mensagem", e);
        }
    }

    /**
     * Trata o comando de criação de mundo
     *
     * @param in Stream de dados
     * @throws IOException Se ocorrer um erro de IO
     */
    private void handleCreateWorld(DataInputStream in) throws IOException {
        // Lê os parâmetros do mundo
        String worldName = in.readUTF();
        String displayName = in.readUTF();
        UUID ownerUUID = UUID.fromString(in.readUTF());
        Material icon = Material.valueOf(in.readUTF());

        // Lê as configurações do mundo
        WorldSettings settings = readWorldSettings(in);

        // Verifica se o mundo já existe
        if (Bukkit.getWorld(worldName) != null) {
            plugin.getLogger().warning("Tentativa de criar um mundo que já existe: " + worldName);
            return;
        }

        // Executa a criação de mundo de forma assíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Cria o mundo usando bukkit
                World world = WorldCreationUtils.createWorld(worldName,
                        plugin.getConfigManager().getWorldType(),
                        plugin.getConfigManager().getWorldEnvironment(),
                        plugin.getConfigManager().isGenerateStructures());

                if (world == null) {
                    plugin.getLogger().severe("Falha ao criar o mundo: " + worldName);
                    return;
                }

                // Cria o objeto CustomWorld
                CustomWorld customWorld = new CustomWorld(displayName, ownerUUID, worldName, icon);
                customWorld.setSettings(settings);

                // Aplica as configurações ao mundo na próxima tick (thread principal)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getWorldManager().applyWorldSettings(customWorld);

                    // Salva no banco de dados
                    plugin.getDatabaseManager().saveWorld(customWorld);

                    // Adiciona aos mundos carregados
                    plugin.getWorldManager().addLoadedWorld(customWorld);

                    plugin.getLogger().info("Mundo criado com sucesso: " + worldName);

                    // Notifica o proprietário se estiver online
                    Player owner = Bukkit.getPlayer(ownerUUID);
                    if (owner != null) {
                        owner.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-created-success"));
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao criar mundo via mensagem cross-server", e);
            }
        });
    }

    /**
     * Trata o comando de teleporte para um mundo
     *
     * @param in Stream de dados
     * @throws IOException Se ocorrer um erro de IO
     */
    private void handleTeleportToWorld(DataInputStream in) throws IOException {
        UUID playerUUID = UUID.fromString(in.readUTF());
        String worldName = in.readUTF();

        // Programar para executar na próxima tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) {
                plugin.getLogger().warning("Jogador não encontrado para teleporte: " + playerUUID);
                return;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-not-found"));
                return;
            }

            Location spawnLocation = world.getSpawnLocation();
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("teleported-to-world")
                    .replace("%world%", worldName));

            // Aplica modo de jogo se necessário
            CustomWorld customWorld = plugin.getWorldManager().getWorldByName(worldName);
            if (customWorld != null && !player.hasPermission("worldsmanager.gamemode.bypass")) {
                GameMode gameMode = customWorld.getSettings().getGameMode();
                if (gameMode != null) {
                    player.setGameMode(gameMode);
                }
            }
        });
    }

    /**
     * Trata o comando de exclusão de mundo
     *
     * @param in Stream de dados
     * @throws IOException Se ocorrer um erro de IO
     */
    private void handleDeleteWorld(DataInputStream in) throws IOException {
        String worldName = in.readUTF();

        // Programar para executar na próxima tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            CustomWorld customWorld = plugin.getWorldManager().getWorldByName(worldName);
            if (customWorld == null) {
                plugin.getLogger().warning("Mundo não encontrado para exclusão: " + worldName);
                return;
            }

            plugin.getWorldManager().deleteWorld(customWorld).thenAccept(success -> {
                if (success) {
                    plugin.getLogger().info("Mundo excluído com sucesso: " + worldName);
                } else {
                    plugin.getLogger().warning("Falha ao excluir mundo: " + worldName);
                }
            });
        });
    }

    /**
     * Trata o comando de atualização de configurações de mundo
     *
     * @param in Stream de dados
     * @throws IOException Se ocorrer um erro de IO
     */
    private void handleUpdateWorldSettings(DataInputStream in) throws IOException {
        String worldName = in.readUTF();
        WorldSettings settings = readWorldSettings(in);

        // Programar para executar na próxima tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            CustomWorld customWorld = plugin.getWorldManager().getWorldByName(worldName);
            if (customWorld == null) {
                plugin.getLogger().warning("Mundo não encontrado para atualização: " + worldName);
                return;
            }

            customWorld.setSettings(settings);
            plugin.getWorldManager().applyWorldSettings(customWorld);
            plugin.getDatabaseManager().saveWorld(customWorld);

            plugin.getLogger().info("Configurações do mundo atualizadas: " + worldName);
        });
    }

    /**
     * Método auxiliar para ler as configurações de um mundo de um stream
     *
     * @param in Stream de dados
     * @return Configurações do mundo
     * @throws IOException Se ocorrer um erro de IO
     */
    private WorldSettings readWorldSettings(DataInputStream in) throws IOException {
        WorldSettings settings = new WorldSettings();

        // Lê configurações básicas
        settings.setPvpEnabled(in.readBoolean());
        settings.setMobSpawning(in.readBoolean());
        settings.setTimeCycle(in.readBoolean());
        settings.setFixedTime(in.readLong());
        settings.setWeatherEnabled(in.readBoolean());
        settings.setPhysicsEnabled(in.readBoolean());
        settings.setRedstoneEnabled(in.readBoolean());
        settings.setFluidFlow(in.readBoolean());
        settings.setTickSpeed(in.readInt());

        // Lê configurações adicionais (se disponíveis)
        try {
            settings.setKeepInventory(in.readBoolean());
            settings.setAnnounceDeaths(in.readBoolean());
            settings.setFallDamage(in.readBoolean());
            settings.setHungerDepletion(in.readBoolean());
            settings.setFireSpread(in.readBoolean());
            settings.setLeafDecay(in.readBoolean());
            settings.setBlockUpdates(in.readBoolean());

            // Lê o modo de jogo
            String gameModeStr = in.readUTF();
            try {
                settings.setGameMode(GameMode.valueOf(gameModeStr));
            } catch (IllegalArgumentException e) {
                settings.setGameMode(GameMode.SURVIVAL);
                plugin.getLogger().warning("Modo de jogo inválido recebido: " + gameModeStr);
            }
        } catch (IOException e) {
            // Ignora erro - pode ser uma versão antiga da mensagem
            plugin.getLogger().info("Usando configurações padrão para campos adicionais");
        }

        return settings;
    }
}