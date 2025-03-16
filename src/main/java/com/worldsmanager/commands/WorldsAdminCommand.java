package com.worldsmanager.commands;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.gui.AdminWorldsGUI;
import com.worldsmanager.models.CustomWorld;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * Executador de comandos admin para gerenciamento de mundos
 */
public class WorldsAdminCommand implements CommandExecutor {

    private final WorldsManager plugin;

    public WorldsAdminCommand(WorldsManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("general.player-only")));
            return true;
        }

        Player player = (Player) sender;

        // Verifica permissão
        if (!player.hasPermission("worldsmanager.admin")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("general.no-permission")));
            return true;
        }

        // Processa comandos
        if (args.length == 0) {
            // Abre GUI admin
            // AdminWorldsGUI seria implementado
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + "&aAbrindo menu administrativo..."));
            return true;
        }

        // Processa subcomandos
        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(player);
                break;

            case "list":
                handleList(player, args);
                break;

            case "tp":
            case "teleport":
                handleTeleport(player, args);
                break;

            case "delete":
                handleDelete(player, args);
                break;

            default:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getLanguageManager().getPrefix() + "&cSubcomando desconhecido: &e" + args[0]));
                sendUsage(player);
                break;
        }

        return true;
    }

    /**
     * Processa o comando de reload
     *
     * @param player Jogador executando o comando
     */
    private void handleReload(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + "&eRecarregando plugin..."));

        plugin.reload();

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("general.reload-success")));
    }

    /**
     * Processa o comando de lista
     *
     * @param player Jogador executando o comando
     * @param args Argumentos do comando
     */
    private void handleList(Player player, String[] args) {
        if (args.length >= 2) {
            // Lista mundos de um jogador específico
            @SuppressWarnings("deprecation")
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[1]);

            if (!targetPlayer.hasPlayedBefore()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("general.player-not-found")));
                return;
            }

            listPlayerWorlds(player, targetPlayer);
        } else {
            // Lista todos os mundos
            listAllWorlds(player);
        }
    }

    /**
     * Lista mundos de um jogador específico
     *
     * @param player Jogador executando o comando
     * @param targetPlayer Jogador alvo
     */
    private void listPlayerWorlds(Player player, OfflinePlayer targetPlayer) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + "&eMundos de &b" + targetPlayer.getName() + "&e:"));

        java.util.List<CustomWorld> worlds = plugin.getWorldManager().getPlayerWorlds(targetPlayer.getUniqueId());

        if (worlds.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + "&cNenhum mundo encontrado para este jogador"));
            return;
        }

        for (CustomWorld world : worlds) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a- &b" + world.getName() + " &7(&e" + world.getWorldName() + "&7)"));
        }
    }

    /**
     * Lista todos os mundos
     *
     * @param player Jogador executando o comando
     */
    private void listAllWorlds(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + "&eTodos os mundos:"));

        Collection<CustomWorld> worlds = plugin.getWorldManager().getAllWorlds();

        if (worlds.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + "&cNenhum mundo encontrado"));
            return;
        }

        for (CustomWorld world : worlds) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(world.getOwnerUUID());
            String ownerName = owner.getName() != null ? owner.getName() : "Desconhecido";

            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a- &b" + world.getName() + " &7(&eDono: &f" + ownerName + "&7)"));
        }
    }

    /**
     * Processa o comando de teleporte
     *
     * @param player Jogador executando o comando
     * @param args Argumentos do comando
     */
    private void handleTeleport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + "&cUso: /worldsadm teleport <nome_mundo>"));
            return;
        }

        String worldName = args[1];
        CustomWorld world = null;

        // Primeiro tenta encontrar pelo nome amigável
        for (CustomWorld w : plugin.getWorldManager().getAllWorlds()) {
            if (w.getName().equalsIgnoreCase(worldName)) {
                world = w;
                break;
            }
        }

        // Se não encontrou, tenta pelo nome interno
        if (world == null) {
            world = plugin.getWorldManager().getWorldByName(worldName);
        }

        if (world == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("general.world-not-found")));
            return;
        }

        // Teleporta jogador para o mundo
        plugin.getWorldManager().loadWorld(world);

        // Armazena as informações necessárias em variáveis finais para uso no lambda
        final String worldNameFinal = world.getName();
        final CustomWorld worldFinal = world;

        if (world.teleportPlayer(player)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("worlds.teleport.success", worldNameFinal)));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("worlds.teleport.failed", worldNameFinal)));
        }
    }

    /**
     * Processa o comando de deleção
     *
     * @param player Jogador executando o comando
     * @param args Argumentos do comando
     */
    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + "&cUso: /worldsadm delete <nome_mundo>"));
            return;
        }

        String worldName = args[1];
        CustomWorld world = null;

        // Primeiro tenta encontrar pelo nome amigável
        for (CustomWorld w : plugin.getWorldManager().getAllWorlds()) {
            if (w.getName().equalsIgnoreCase(worldName)) {
                world = w;
                break;
            }
        }

        // Se não encontrou, tenta pelo nome interno
        if (world == null) {
            world = plugin.getWorldManager().getWorldByName(worldName);
        }

        if (world == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("general.world-not-found")));
            return;
        }

        // Confirma deleção
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getLanguageManager().getPrefix() + "&cTem certeza que deseja deletar este mundo? Digite &e/worldsadm delete " + worldName + " confirm&c para confirmar."));
            return;
        }

        // Deleta o mundo
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + "&eDeletando mundo... Por favor, aguarde."));

        // Armazena as informações necessárias em variáveis finais para uso no lambda
        final String worldNameFinal = world.getName();
        final CustomWorld worldFinal = world;

        plugin.getWorldManager().deleteWorld(worldFinal).thenAccept(success -> {
            if (success) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("worlds.delete.success", worldNameFinal)));
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("worlds.delete.failed", worldNameFinal)));
            }
        });
    }

    /**
     * Envia informações de uso
     *
     * @param player Jogador para enviar
     */
    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getPrefix() + "&eComandos disponíveis:"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b/worldsadm &7- Abrir GUI administrativo"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b/worldsadm reload &7- Recarregar plugin"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b/worldsadm list [jogador] &7- Listar todos os mundos ou mundos de um jogador"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b/worldsadm teleport <mundo> &7- Teleportar para um mundo"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b/worldsadm delete <mundo> [confirm] &7- Deletar um mundo"));
    }
}