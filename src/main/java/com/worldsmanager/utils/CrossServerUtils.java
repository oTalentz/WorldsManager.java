package com.worldsmanager.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Utilitários para comunicação entre servidores
 */
public class CrossServerUtils {

    /**
     * Envia uma mensagem para o BungeeCord para conectar um jogador a outro servidor
     *
     * @param plugin Plugin
     * @param player Jogador a ser conectado
     * @param server Servidor de destino
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean connectToServer(Plugin plugin, Player player, String server) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(server);

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar jogador para outro servidor", e);
            return false;
        }
    }

    /**
     * Envia uma mensagem para o BungeeCord para ser repassada a outro servidor
     *
     * @param plugin Plugin
     * @param player Jogador para enviar a mensagem (precisa ser um jogador online)
     * @param server Servidor de destino
     * @param channel Canal do plugin
     * @param data Dados a serem enviados
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean sendPluginMessage(Plugin plugin, Player player, String server, String channel, byte[] data) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Forward");
            out.writeUTF(server);
            out.writeUTF(channel);

            out.writeShort(data.length);
            out.write(data);

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem para outro servidor", e);
            return false;
        }
    }

    /**
     * Envia uma mensagem global para todos os servidores
     *
     * @param plugin Plugin
     * @param player Jogador para enviar a mensagem (precisa ser um jogador online)
     * @param channel Canal do plugin
     * @param data Dados a serem enviados
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean sendGlobalPluginMessage(Plugin plugin, Player player, String channel, byte[] data) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(channel);

            out.writeShort(data.length);
            out.write(data);

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao enviar mensagem global", e);
            return false;
        }
    }

    /**
     * Solicita informações do servidor
     *
     * @param plugin Plugin
     * @param player Jogador para enviar a mensagem (precisa ser um jogador online)
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean getServerInfo(Plugin plugin, Player player) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("GetServer");

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao solicitar informações do servidor", e);
            return false;
        }
    }

    /**
     * Solicita lista de jogadores de um servidor específico
     *
     * @param plugin Plugin
     * @param player Jogador para enviar a mensagem (precisa ser um jogador online)
     * @param server Servidor alvo
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean getPlayersOnServer(Plugin plugin, Player player, String server) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("PlayerList");
            out.writeUTF(server);

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao solicitar lista de jogadores", e);
            return false;
        }
    }
}