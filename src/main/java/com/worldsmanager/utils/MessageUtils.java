package com.worldsmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;

/**
 * Utilitários para envio de mensagens
 */
public class MessageUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Logger logger = Logger.getLogger("WorldsManager");

    /**
     * Transforma códigos de cores em uma string
     *
     * @param text Texto a ser colorido
     * @return Texto colorido
     */
    public static String colorize(String text) {
        if (text == null) {
            return "";
        }

        // Processa cores hexadecimais (versões mais recentes do Spigot)
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + group).toString());
        }

        matcher.appendTail(buffer);
        text = buffer.toString();

        // Processa cores antigas
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Envia uma mensagem para um receptor com prefixo
     *
     * @param sender Receptor da mensagem
     * @param prefix Prefixo da mensagem
     * @param message Mensagem
     */
    public static void sendMessage(CommandSender sender, String prefix, String message) {
        if (sender == null || message == null) {
            return;
        }

        sender.sendMessage(colorize(prefix + message));
    }

    // Outros métodos permanecem inalterados...

    /**
     * Cria um componente de chat clicável
     *
     * @param text Texto do componente
     * @param command Comando a ser executado ao clicar
     * @param hoverText Texto ao passar o mouse
     * @return Componente de chat
     */
    public static TextComponent createClickableText(String text, String command, String hoverText) {
        TextComponent component = new TextComponent(colorize(text));

        // Define o comando a ser executado
        component.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, command));

        // Define o texto ao passar o mouse
        if (hoverText != null && !hoverText.isEmpty()) {
            component.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(colorize(hoverText)).create()
            ));
        }

        return component;
    }


    /**
     * Envia uma mensagem para o BungeeCord
     *
     * @param plugin Plugin
     * @param player Jogador para enviar
     * @param channel Canal
     * @param subChannel Subcanal
     * @param data Dados
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean sendPluginMessage(Plugin plugin, Player player, String channel, String subChannel, byte[] data) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            out.writeUTF(subChannel);
            out.write(data);

            player.sendPluginMessage(plugin, channel, stream.toByteArray());
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erro ao enviar mensagem de plugin", e);
            return false;
        }
    }

    /**
     * Envia uma mensagem de plugin para o BungeeCord
     *
     * @param plugin Plugin
     * @param player Jogador para enviar
     * @param subChannel Subcanal
     * @param data Dados
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean sendBungeeMessage(Plugin plugin, Player player, String subChannel, byte[] data) {
        return sendPluginMessage(plugin, player, "BungeeCord", subChannel, data);
    }

    /**
     * Envia uma mensagem para o BungeeCord com subcanal Forward
     *
     * @param plugin Plugin
     * @param player Jogador para enviar
     * @param server Servidor de destino
     * @param channel Canal do plugin
     * @param data Dados
     * @return true se a mensagem foi enviada com sucesso
     */
    public static boolean sendForwardMessage(Plugin plugin, Player player, String server, String channel, byte[] data) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            out.writeUTF("Forward");
            out.writeUTF(server);
            out.writeUTF(channel);
            out.writeShort(data.length);
            out.write(data);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erro ao enviar mensagem para outro servidor", e);
            return false;
        }
    }

    /**
     * Conecta um jogador a outro servidor
     *
     * @param plugin Plugin
     * @param player Jogador para conectar
     * @param server Servidor de destino
     * @return true se o comando foi enviado com sucesso
     */
    public static boolean connectToServer(Plugin plugin, Player player, String server) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            out.writeUTF("Connect");
            out.writeUTF(server);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erro ao conectar jogador a outro servidor", e);
            return false;
        }
    }

    /**
     * Envia uma mensagem com atraso
     *
     * @param plugin Plugin
     * @param sender Receptor da mensagem
     * @param prefix Prefixo da mensagem
     * @param message Mensagem
     * @param delay Atraso em ticks
     */
    public static void sendDelayedMessage(Plugin plugin, CommandSender sender, String prefix, String message, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                sendMessage(sender, prefix, message), delay);
    }

    /**
     * Agrupa um array de strings a partir de um índice
     *
     * @param args Array de strings
     * @param startIndex Índice inicial
     * @param separator Separador
     * @return String agrupada
     */
    public static String joinStrings(String[] args, int startIndex, String separator) {
        if (args == null || args.length <= startIndex) {
            return "";
        }

        return String.join(separator, Arrays.copyOfRange(args, startIndex, args.length));
    }

    /**
     * Envia mensagem de erro para um receptor
     *
     * @param sender Receptor da mensagem
     * @param prefix Prefixo da mensagem
     * @param message Mensagem
     */
    public static void sendErrorMessage(CommandSender sender, String prefix, String message) {
        sendMessage(sender, prefix, "&c" + message);
    }

    /**
     * Envia mensagem de sucesso para um receptor
     *
     * @param sender Receptor da mensagem
     * @param prefix Prefixo da mensagem
     * @param message Mensagem
     */
    public static void sendSuccessMessage(CommandSender sender, String prefix, String message) {
        sendMessage(sender, prefix, "&a" + message);
    }

    /**
     * Cria uma barra de progresso
     *
     * @param current Valor atual
     * @param max Valor máximo
     * @param totalBars Número total de barras
     * @param symbol Símbolo a ser usado
     * @param completedColor Cor da parte completa
     * @param notCompletedColor Cor da parte incompleta
     * @return String com a barra de progresso
     */
    public static String createProgressBar(double current, double max, int totalBars,
                                           String symbol, String completedColor, String notCompletedColor) {
        int progressBars = (int) ((current / max) * totalBars);
        StringBuilder builder = new StringBuilder();

        builder.append(completedColor);
        for (int i = 0; i < progressBars; i++) {
            builder.append(symbol);
        }

        builder.append(notCompletedColor);
        for (int i = 0; i < totalBars - progressBars; i++) {
            builder.append(symbol);
        }

        return builder.toString();
    }

    /**
     * Centraliza uma string em um comprimento específico
     *
     * @param text Texto a ser centralizado
     * @param length Comprimento total
     * @return String centralizada
     */
    public static String centerText(String text, int length) {
        if (text == null || text.length() >= length) {
            return text;
        }

        int spaces = (length - ChatColor.stripColor(text).length()) / 2;
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < spaces; i++) {
            builder.append(" ");
        }

        builder.append(text);

        return builder.toString();
    }

    /**
     * Converte milissegundos em formato legível
     *
     * @param milliseconds Tempo em milissegundos
     * @return String formatada
     */
    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder builder = new StringBuilder();

        if (days > 0) {
            builder.append(days).append("d ");
        }

        if (hours > 0 || days > 0) {
            builder.append(hours).append("h ");
        }

        if (minutes > 0 || hours > 0 || days > 0) {
            builder.append(minutes).append("m ");
        }

        builder.append(seconds).append("s");

        return builder.toString();
    }
}