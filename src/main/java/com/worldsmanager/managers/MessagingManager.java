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

    // Rastreamento de mundos pendentes para criação
    private final Map<UUID, String> pendingWorldCreations = new HashMap<>();

    public MessagingManager(WorldsManager plugin) {
        this.plugin = plugin;

        // Registrar canais imediatamente ao inicializar
        if (plugin.getConfigManager().isCrossServerMode()) {
            // Verificar se já está registrado
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
                plugin.getLogger().info("Canal BungeeCord registrado pelo MessagingManager");
            }
        }

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
            plugin.getLogger().info("[MUNDO] Preparando mensagem de criação de mundo: " + world.getWorldName());
            plugin.getLogger().info("[MUNDO] Servidor de destino: " + plugin.getConfigManager().getWorldsServerName());

            // Verificar se o canal está registrado
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                plugin.getLogger().severe("Canal BungeeCord não está registrado! Registrando agora...");
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            }

            // Verificar se há jogadores online para enviar a mensagem
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                plugin.getLogger().severe("Não há jogadores online para enviar a mensagem!");
                return false;
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

            // Escrever o caminho personalizado do mundo
            String worldPath = world.getWorldPath() != null ? world.getWorldPath() : "";
            msgout.writeUTF(worldPath);

            // Escrever configurações do mundo
            WorldSettings settings = world.getSettings();
            writeWorldSettings(msgout, settings);

            // Coloque os bytes da mensagem no plugin message
            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            // Enviar a mensagem
            try {
                requester.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
            } catch (Exception e) {
                plugin.getLogger().severe("Erro ao enviar mensagem através do jogador: " + e.getMessage());

                // Tenta usar outro jogador online se o primeiro falhar
                Player alternativePlayer = null;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(requester)) {
                        alternativePlayer = p;
                        break;
                    }
                }

                if (alternativePlayer != null) {
                    plugin.getLogger().info("Tentando enviar mensagem através de jogador alternativo: " + alternativePlayer.getName());
                    alternativePlayer.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
                } else {
                    throw new IllegalStateException("Não há jogadores alternativos online para enviar a mensagem");
                }
            }

            // Adicionar ao mapa de mundos pendentes
            pendingWorldCreations.put(requester.getUniqueId(), world.getWorldName());

            // Armazenar também como teleporte pendente para quando o jogador mudar de servidor
            plugin.getWorldManager().addPendingTeleport(requester.getUniqueId(), world.getWorldName());

            // Notificar o jogador
            requester.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-creation-requested"));
            requester.sendMessage(ChatColor.YELLOW + "Preparando mundo... Por favor aguarde.");

            plugin.getLogger().info("[MUNDO] Mensagem de criação de mundo enviada para o servidor: " +
                    plugin.getConfigManager().getWorldsServerName());

            // Agendar teleporte após um delay para permitir que o mundo seja criado primeiro
            int teleportDelay = plugin.getConfigManager().getTeleportDelay();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (requester.isOnline() && pendingWorldCreations.containsKey(requester.getUniqueId())) {
                    requester.sendMessage(ChatColor.GREEN + "Mundo pronto! Teleportando...");
                    teleportPlayerToWorldsServer(requester);
                }
            }, teleportDelay); // Usar o delay configurado

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem de criação de mundo", e);
            // Notifica o jogador sobre a falha
            requester.sendMessage(ChatColor.RED + "Erro ao enviar mensagem para o servidor de mundos: " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erro inesperado ao enviar mensagem de criação de mundo", e);
            requester.sendMessage(ChatColor.RED + "Erro inesperado: " + e.getMessage());
            return false;
        }
    }

    /**
     * Teleporta o jogador para o servidor de mundos
     *
     * @param player Jogador a ser teleportado
     * @return true se o teleporte foi iniciado com sucesso
     */
    private boolean teleportPlayerToWorldsServer(Player player) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(plugin.getConfigManager().getWorldsServerName());

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

            plugin.getLogger().info("[MUNDO] Jogador " + player.getName() +
                    " enviado para o servidor " + plugin.getConfigManager().getWorldsServerName());

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao teleportar jogador para servidor de mundos", e);
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

            plugin.getLogger().info("[MUNDO] Iniciando processo de teleporte para " + player.getName() +
                    " para o mundo " + worldName);

            // Verificar se o canal está registrado
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                plugin.getLogger().severe("Canal BungeeCord não está registrado! Registrando agora...");
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            }

            // Adiciona o teleporte pendente ANTES do envio da mensagem
            plugin.getWorldManager().addPendingTeleport(player.getUniqueId(), worldName);
            plugin.getLogger().info("[MUNDO] Teleporte pendente adicionado para " +
                    player.getName() + " ao mundo " + worldName);

            // Cria uma mensagem para alertar o servidor de destino sobre o teleporte pendente
            ByteArrayOutputStream alertBytes = new ByteArrayOutputStream();
            DataOutputStream alertOut = new DataOutputStream(alertBytes);

            alertOut.writeUTF("Forward");
            alertOut.writeUTF(plugin.getConfigManager().getWorldsServerName());
            alertOut.writeUTF(PLUGIN_CHANNEL);

            ByteArrayOutputStream alertMsgBytes = new ByteArrayOutputStream();
            DataOutputStream alertMsgOut = new DataOutputStream(alertMsgBytes);

            alertMsgOut.writeUTF("TeleportToWorld");
            alertMsgOut.writeUTF(player.getUniqueId().toString());
            alertMsgOut.writeUTF(worldName);

            alertOut.writeShort(alertMsgBytes.toByteArray().length);
            alertOut.write(alertMsgBytes.toByteArray());

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, alertBytes.toByteArray());
            plugin.getLogger().info("[MUNDO] Mensagem de preparação para teleporte enviada");

            // Pequeno delay antes de conectar ao servidor para garantir que a mensagem de preparação chegue primeiro
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Conecta o jogador ao servidor de mundos
                    ByteArrayOutputStream connectBytes = new ByteArrayOutputStream();
                    DataOutputStream connectOut = new DataOutputStream(connectBytes);

                    connectOut.writeUTF("Connect");
                    connectOut.writeUTF(plugin.getConfigManager().getWorldsServerName());

                    if (player.isOnline()) {
                        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, connectBytes.toByteArray());
                        plugin.getLogger().info("[MUNDO] Jogador " + player.getName() + " enviado para o servidor " +
                                plugin.getConfigManager().getWorldsServerName());

                        // Controle de tentativa de teleporte
                        UUID playerUUID = player.getUniqueId();
                        teleportAttempts.put(playerUUID, 0);

                        // Inicie um loop para verificar se o jogador foi teleportado com sucesso
                        scheduleFollowUpMessage(player.getUniqueId(), worldName);
                    } else {
                        plugin.getLogger().warning("Jogador desconectou antes do teleporte: " + player.getName());
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Erro ao conectar jogador ao servidor de mundos", e);
                }
            }, 10L); // 0.5 segundos de delay

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem de teleporte", e);
            // Notifica o jogador sobre a falha
            player.sendMessage(ChatColor.RED + "Erro ao teleportar para o servidor de mundos: " + e.getMessage());
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
                        // Verifica se o canal está registrado
                        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                            plugin.getLogger().severe("Canal BungeeCord não está registrado! Registrando novamente...");
                            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
                        }

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

                        plugin.getLogger().info("[MUNDO] Enviada tentativa adicional de teleporte: " + (attempts + 1));

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

                // Tenta notificar o jogador se ele estiver online
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Falha ao completar o teleporte após " +
                            MAX_TELEPORT_ATTEMPTS + " tentativas.");
                    player.sendMessage(ChatColor.YELLOW + "Tente novamente ou contate um administrador.");
                }
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

            // Verificar se o canal está registrado
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                plugin.getLogger().severe("Canal BungeeCord não está registrado! Registrando agora...");
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
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
            // Notifica o jogador sobre a falha
            player.sendMessage(ChatColor.RED + "Erro ao enviar comando de exclusão: " + e.getMessage());
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

            // Verificar se o canal está registrado
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                plugin.getLogger().severe("Canal BungeeCord não está registrado! Registrando agora...");
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
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
            // Notifica o jogador sobre a falha
            player.sendMessage(ChatColor.RED + "Erro ao atualizar configurações: " + e.getMessage());
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

    /**
     * Verifica se os canais necessários estão registrados
     * @return true se tudo estiver corretamente registrado
     */
    public boolean checkChannelsRegistered() {
        boolean outgoing = plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL);
        boolean incoming = plugin.getServer().getMessenger().isIncomingChannelRegistered(plugin, BUNGEE_CHANNEL);

        if (!outgoing) {
            plugin.getLogger().severe("Canal BungeeCord não está registrado para saída!");
            return false;
        }

        if (!incoming) {
            plugin.getLogger().severe("Canal BungeeCord não está registrado para entrada!");
            return false;
        }

        return true;
    }

    /**
     * Envia um jogador para outro servidor no BungeeCord
     *
     * @param player Jogador a ser enviado
     * @param server Nome do servidor de destino
     * @return true se a mensagem foi enviada com sucesso
     */
    public boolean sendPlayerToServer(Player player, String server) {
        try {
            if (player == null || !player.isOnline()) {
                plugin.getLogger().warning("Tentativa de enviar jogador offline ou nulo para outro servidor");
                return false;
            }

            plugin.getLogger().info("Enviando jogador " + player.getName() + " para o servidor: " + server);

            // Verificar se o canal está registrado
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                plugin.getLogger().severe("Canal BungeeCord não está registrado! Registrando agora...");
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            }

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(server);

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
            plugin.getLogger().info("Jogador " + player.getName() + " enviado para o servidor: " + server);

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar jogador para outro servidor", e);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.RED + "Erro ao conectar ao servidor de mundos: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Remove um mundo dos mundos pendentes quando a criação for concluída
     *
     * @param playerUUID UUID do jogador
     */
    public void removePendingWorldCreation(UUID playerUUID) {
        pendingWorldCreations.remove(playerUUID);
    }

    /**
     * Verifica se existe uma criação de mundo pendente para o jogador
     *
     * @param playerUUID UUID do jogador
     * @return true se existir uma criação pendente
     */
    public boolean hasPendingWorldCreation(UUID playerUUID) {
        return pendingWorldCreations.containsKey(playerUUID);
    }

    /**
     * Obtém o nome do mundo pendente para o jogador
     *
     * @param playerUUID UUID do jogador
     * @return nome do mundo pendente ou null se não houver
     */
    public String getPendingWorldName(UUID playerUUID) {
        return pendingWorldCreations.get(playerUUID);
    }
}