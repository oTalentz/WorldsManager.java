package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gerenciador para teleportes com verificação de servidor
 */
public class TeleportManager {

    private final WorldsManager plugin;
    private final String BUNGEE_CHANNEL = "BungeeCord";

    // Mapeamento de jogadores para servidor atual
    private final Map<UUID, String> playerServers = new HashMap<>();

    // Mundos pendentes para teleporte após mudança de servidor
    private final Map<UUID, String> pendingWorldTeleports = new HashMap<>();

    public TeleportManager(WorldsManager plugin) {
        this.plugin = plugin;

        // Registrar canal para receber informações do servidor
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        }

        if (!plugin.getServer().getMessenger().isIncomingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL,
                    (channel, player, message) -> {
                        if (!channel.equals(BUNGEE_CHANNEL)) return;

                        try {
                            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(message));
                            String subchannel = in.readUTF();

                            if (subchannel.equals("GetServer")) {
                                String serverName = in.readUTF();
                                playerServers.put(player.getUniqueId(), serverName);
                                plugin.getLogger().info("[TELEPORTE] Jogador " + player.getName() +
                                        " está no servidor " + serverName);

                                // Verificar teleportes pendentes
                                checkPendingTeleport(player);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "Erro ao processar mensagem do BungeeCord", e);
                        }
                    });
        }
    }

    /**
     * Teleporta um jogador para um mundo com verificação de servidor
     *
     * @param player Jogador a ser teleportado
     * @param worldName Nome do mundo
     * @return true se o processo de teleporte foi iniciado
     */
    public boolean teleportToWorld(Player player, String worldName) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Tentativa de teleportar jogador offline");
            return false;
        }

        plugin.getLogger().info("[TELEPORTE] Iniciando teleporte para " + player.getName() +
                " para mundo " + worldName);

        // Verificar servidor atual
        getPlayerServer(player);

        // Armazenar teleporte pendente
        pendingWorldTeleports.put(player.getUniqueId(), worldName);

        // Adicionar ao pendingTeleports do WorldManager para ser processado após a entrada no servidor
        plugin.getWorldManager().addPendingTeleport(player.getUniqueId(), worldName);

        // Verificar servidor atual (resposta assíncrona)
        String currentServer = playerServers.get(player.getUniqueId());

        // Aguardar resposta GetServer (máximo 2 segundos)
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts && currentServer == null; i++) {
            try {
                Thread.sleep(200);
                currentServer = playerServers.get(player.getUniqueId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (currentServer == null) {
            plugin.getLogger().warning("Não foi possível determinar o servidor do jogador, assumindo que não é o servidor de mundos");
            // Como fallback, teleporta para o servidor de mundos
            teleportToWorldsServer(player);
            return true;
        }

        String worldsServer = plugin.getConfigManager().getWorldsServerName();

        if (currentServer.equalsIgnoreCase(worldsServer)) {
            // Jogador já está no servidor correto, teleportar diretamente
            plugin.getLogger().info("[TELEPORTE] Jogador já está no servidor de mundos, teleportando diretamente");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CustomWorld world = plugin.getWorldManager().getWorldByName(worldName);
                if (world != null) {
                    // Tentar carregar e então teleportar
                    plugin.getWorldManager().loadWorld(world);
                    world.teleportPlayer(player);
                    player.sendMessage(ChatColor.GREEN + "Teleportado para o mundo " + world.getName());
                } else {
                    player.sendMessage(ChatColor.RED + "Mundo não encontrado: " + worldName);
                }
            }, 10L); // Delay curto para garantir processamento do evento atual

            return true;
        } else {
            // Jogador está em outro servidor, teleportar para o servidor de mundos primeiro
            plugin.getLogger().info("[TELEPORTE] Jogador está no servidor " + currentServer +
                    ", teleportando para o servidor de mundos");

            player.sendMessage(ChatColor.YELLOW + "Teleportando para o servidor de mundos...");
            return teleportToWorldsServer(player);
        }
    }

    /**
     * Teleporta o jogador para o servidor de mundos
     *
     * @param player Jogador a teleportar
     * @return true se o comando foi enviado com sucesso
     */
    public boolean teleportToWorldsServer(Player player) {
        try {
            String worldsServer = plugin.getConfigManager().getWorldsServerName();

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(worldsServer);

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

            plugin.getLogger().info("[TELEPORTE] Jogador " + player.getName() +
                    " enviado para o servidor " + worldsServer);

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao teleportar para servidor de mundos", e);
            return false;
        }
    }

    /**
     * Solicita informações sobre o servidor atual do jogador
     *
     * @param player Jogador
     */
    public void getPlayerServer(Player player) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("GetServer");

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao solicitar servidor do jogador", e);
        }
    }

    /**
     * Verifica e processa teleportes pendentes
     *
     * @param player Jogador que entrou no servidor
     */
    public void checkPendingTeleport(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (pendingWorldTeleports.containsKey(playerUUID)) {
            String worldName = pendingWorldTeleports.get(playerUUID);
            String currentServer = playerServers.get(playerUUID);
            String worldsServer = plugin.getConfigManager().getWorldsServerName();

            plugin.getLogger().info("[TELEPORTE] Verificando teleporte pendente para " + player.getName() +
                    " para mundo " + worldName + " (servidor atual: " + currentServer + ")");

            if (currentServer.equalsIgnoreCase(worldsServer)) {
                // Jogador está no servidor correto, teleportar para o mundo
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    CustomWorld world = plugin.getWorldManager().getWorldByName(worldName);
                    if (world != null) {
                        plugin.getWorldManager().loadWorld(world);
                        world.teleportPlayer(player);
                        player.sendMessage(ChatColor.GREEN + "Teleportado para o mundo " + world.getName());
                    } else {
                        player.sendMessage(ChatColor.RED + "Mundo não encontrado: " + worldName);
                    }

                    // Remover teleporte pendente
                    pendingWorldTeleports.remove(playerUUID);
                }, 40L); // 2 segundos de delay para carregamento completo do jogador
            }
        }
    }

    /**
     * Método chamado quando um jogador entra no servidor
     */
    public void onPlayerJoin(Player player) {
        // Solicitar servidor atual
        getPlayerServer(player);
    }
}