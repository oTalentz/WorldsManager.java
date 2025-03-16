package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gerenciador de mensagens entre servidores
 */
public class MessagingManager {

    private final WorldsManager plugin;
    private final String BUNGEE_CHANNEL = "BungeeCord";
    private final String PLUGIN_CHANNEL = "WorldsManager";

    // Controle de tentativas de teleporte para retry
    private final Map<UUID, Integer> teleportAttempts = new HashMap<>();
    private final int MAX_TELEPORT_ATTEMPTS = 3;

    public MessagingManager(WorldsManager plugin) {
        this.plugin = plugin;

        // Log para ajudar na depuração
        plugin.getLogger().info("MessagingManager inicializado.");
    }

    /**
     * Envia uma mensagem para criar um mundo no servidor de mundos
     *
     * @param world CustomWorld a ser criado
     * @param requester Jogador que solicitou a criação
     * @return true se a mensagem foi enviada com sucesso
     */
    public boolean sendCreateWorldMessage(CustomWorld world, Player requester) {
        try {
            if (requester == null || !requester.isOnline()) {
                plugin.getLogger().warning("Tentativa de enviar mensagem com jogador offline ou nulo");
                return false;
            }

            // Log para debug
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Preparando mensagem de criação de mundo: " + world.getWorldName());
            }

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            // Formato do PluginMessage do BungeeCord
            out.writeUTF("Forward");
            out.writeUTF(plugin.getConfigManager().getWorldsServerName()); // Nome do servidor destino
            out.writeUTF(PLUGIN_CHANNEL);

            // Escrever dados específicos
            ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
            DataOutputStream msgout = new DataOutputStream(msgbytes);

            msgout.writeUTF("CreateWorld");
            msgout.writeUTF(world.getWorldName());
            msgout.writeUTF(world.getName());
            msgout.writeUTF(world.getOwnerUUID().toString());
            msgout.writeUTF(world.getIcon().name());

            // Escrever configurações do mundo
            WorldSettings settings = world.getSettings();
            writeWorldSettings(msgout, settings);

            // Coloque os bytes da mensagem no plugin message
            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            // Enviar a mensagem
            requester.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

            // Notificar o jogador
            requester.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-creation-requested"));

            plugin.getLogger().info("Mensagem de criação de mundo enviada para o servidor: " +
                    plugin.getConfigManager().getWorldsServerName());

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem de criação de mundo", e);
            return false;
        }
    }

    /**
     * Envia uma mensagem para teleportar um jogador para um mundo específico no servidor de mundos
     *
     * @param player Jogador a ser teleportado
     * @param worldName Nome do mundo para teleportar
     * @return true se a mensagem foi enviada com sucesso
     */
    public boolean sendTeleportToWorldMessage(Player player, String worldName) {
        try {
            if (player == null || !player.isOnline()) {
                plugin.getLogger().warning("Tentativa de teleportar jogador offline ou nulo");
                return false;
            }

            plugin.getLogger().info("Iniciando processo de teleporte para " + player.getName() + " para o mundo " + worldName);

            // Primeiro envie a mensagem de teleporte para o servidor de destino
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);

            msgOut.writeUTF("TeleportToWorld");
            msgOut.writeUTF(player.getUniqueId().toString());
            msgOut.writeUTF(worldName);

            byte[] msgData = msgBytes.toByteArray();

            // Então, prepare a mensagem para o BungeeCord
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            // Envie a mensagem para o servidor de mundos com o canal do plugin
            out.writeUTF("Forward");
            out.writeUTF(plugin.getConfigManager().getWorldsServerName());
            out.writeUTF(PLUGIN_CHANNEL);
            out.writeShort(msgData.length);
            out.write(msgData);

            // Envie a mensagem usando o canal BungeeCord
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

            plugin.getLogger().info("Enviada mensagem de preparação para teleporte");

            // Após preparar o teleporte, conecte o jogador ao servidor de mundos
            ByteArrayOutputStream connectBytes = new ByteArrayOutputStream();
            DataOutputStream connectOut = new DataOutputStream(connectBytes);

            connectOut.writeUTF("Connect");
            connectOut.writeUTF(plugin.getConfigManager().getWorldsServerName());

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, connectBytes.toByteArray());

            plugin.getLogger().info("Jogador " + player.getName() + " enviado para o servidor de mundos");

            // Controle de tentativa de teleporte
            UUID playerUUID = player.getUniqueId();
            teleportAttempts.put(playerUUID, 0);

            // Inicie um loop para verificar se o jogador foi teleportado com sucesso
            scheduleFollowUpMessage(player.getUniqueId(), worldName);

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem de teleporte", e);
            return false;
        }
    }

    /**
     * Agenda mensagens adicionais de follow-up para garantir que o teleporte funcione
     *
     * @param playerUUID UUID do jogador
     * @param worldName Nome do mundo
     */
    private void scheduleFollowUpMessage(UUID playerUUID, String worldName) {
        int delay = plugin.getConfigManager().getTeleportDelay();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int attempts = teleportAttempts.getOrDefault(playerUUID, 0);

            if (attempts < MAX_TELEPORT_ATTEMPTS) {
                teleportAttempts.put(playerUUID, attempts + 1);

                // Verifica se há jogadores online para enviar a mensagem
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    Player anyPlayer = Bukkit.getOnlinePlayers().iterator().next();

                    try {
                        // Envia uma nova mensagem de teleporte
                        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
                        DataOutputStream msgOut = new DataOutputStream(msgBytes);

                        msgOut.writeUTF("TeleportToWorld");
                        msgOut.writeUTF(playerUUID.toString());
                        msgOut.writeUTF(worldName);

                        byte[] msgData = msgBytes.toByteArray();

                        // Prepara a mensagem para o BungeeCord
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(b);

                        out.writeUTF("Forward");
                        out.writeUTF(plugin.getConfigManager().getWorldsServerName());
                        out.writeUTF(PLUGIN_CHANNEL);
                        out.writeShort(msgData.length);
                        out.write(msgData);

                        // Envia a mensagem
                        anyPlayer.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

                        plugin.getLogger().info("Enviada tentativa adicional de teleporte: " + (attempts + 1));

                        // Agenda a próxima tentativa se necessário
                        scheduleFollowUpMessage(playerUUID, worldName);

                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Erro ao enviar tentativa adicional de teleporte", e);
                    }
                }
            } else {
                // Removemos da lista de tentativas quando atingir o limite
                teleportAttempts.remove(playerUUID);
                plugin.getLogger().warning("Número máximo de tentativas de teleporte atingido para " + playerUUID);
            }
        }, delay); // Usa o delay configurado
    }

    /**
     * Envia uma mensagem para excluir um mundo no servidor de mundos
     *
     * @param worldName Nome do mundo a ser excluído
     * @param player Jogador que solicitou a exclusão
     * @return true se a mensagem foi enviada com sucesso
     */
    public boolean sendDeleteWorldMessage(String worldName, Player player) {
        try {
            if (player == null || !player.isOnline()) {
                plugin.getLogger().warning("Tentativa de enviar mensagem com jogador offline ou nulo");
                return false;
            }

            // Log para debug
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Preparando mensagem de exclusão para o mundo: " + worldName);
            }

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Forward");
            out.writeUTF(plugin.getConfigManager().getWorldsServerName());
            out.writeUTF(PLUGIN_CHANNEL);

            ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
            DataOutputStream msgout = new DataOutputStream(msgbytes);

            msgout.writeUTF("DeleteWorld");
            msgout.writeUTF(worldName);

            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

            plugin.getLogger().info("Mensagem de exclusão de mundo enviada para: " + worldName);

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem de exclusão de mundo", e);
            return false;
        }
    }

    /**
     * Envia uma mensagem para atualizar as configurações de um mundo no servidor de mundos
     *
     * @param worldName Nome do mundo
     * @param settings Configurações a serem aplicadas
     * @param player Jogador que solicitou a atualização
     * @return true se a mensagem foi enviada com sucesso
     */
    public boolean sendUpdateWorldSettingsMessage(String worldName, WorldSettings settings, Player player) {
        try {
            if (player == null || !player.isOnline()) {
                plugin.getLogger().warning("Tentativa de enviar mensagem com jogador offline ou nulo");
                return false;
            }

            // Log para debug
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Preparando mensagem de atualização de configurações para: " + worldName);
            }

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Forward");
            out.writeUTF(plugin.getConfigManager().getWorldsServerName());
            out.writeUTF(PLUGIN_CHANNEL);

            ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
            DataOutputStream msgout = new DataOutputStream(msgbytes);

            msgout.writeUTF("UpdateWorldSettings");
            msgout.writeUTF(worldName);

            // Escrever configurações do mundo
            writeWorldSettings(msgout, settings);

            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

            plugin.getLogger().info("Mensagem de atualização de configurações enviada para: " + worldName);

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem de atualização de configurações", e);
            return false;
        }
    }

    /**
     * Método auxiliar para escrever as configurações de um mundo em um stream
     *
     * @param out Stream de dados
     * @param settings Configurações a serem escritas
     * @throws IOException Se ocorrer um erro de IO
     */
    private void writeWorldSettings(DataOutputStream out, WorldSettings settings) throws IOException {
        // Escreve configurações básicas
        out.writeBoolean(settings.isPvpEnabled());
        out.writeBoolean(settings.isMobSpawning());
        out.writeBoolean(settings.isTimeCycle());
        out.writeLong(settings.getFixedTime());
        out.writeBoolean(settings.isWeatherEnabled());
        out.writeBoolean(settings.isPhysicsEnabled());
        out.writeBoolean(settings.isRedstoneEnabled());
        out.writeBoolean(settings.isFluidFlow());
        out.writeInt(settings.getTickSpeed());

        // Escreve configurações adicionais
        out.writeBoolean(settings.isKeepInventory());
        out.writeBoolean(settings.isAnnounceDeaths());
        out.writeBoolean(settings.isFallDamage());
        out.writeBoolean(settings.isHungerDepletion());
        out.writeBoolean(settings.isFireSpread());
        out.writeBoolean(settings.isLeafDecay());
        out.writeBoolean(settings.isBlockUpdates());

        // Escreve o modo de jogo
        out.writeUTF(settings.getGameMode() != null ? settings.getGameMode().name() : "SURVIVAL");
    }
}