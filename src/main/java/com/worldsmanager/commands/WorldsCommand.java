package com.worldsmanager.commands;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.gui.WorldCreateGUI;
import com.worldsmanager.gui.WorldsGUI;
import com.worldsmanager.managers.LanguageManager;
import com.worldsmanager.models.CustomWorld;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Comandos principais do plugin para jogadores
 */
public class WorldsCommand implements CommandExecutor {

    private final WorldsManager plugin;
    private final LanguageManager languageManager;
    private WorldsGUI worldsGUI;
    private WorldCreateGUI worldCreateGUI;

    public WorldsCommand(WorldsManager plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.worldsGUI = new WorldsGUI(plugin);
        this.worldCreateGUI = new WorldCreateGUI(plugin);

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] WorldsCommand inicializado, WorldCreateGUI criado");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + languageManager.getMessage("players-only"));
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Comando recebido: " + label + " " + String.join(" ", args) +
                    " por " + player.getName());
        }

        // Verifica permissão
        if (!player.hasPermission("worldsmanager.use")) {
            player.sendMessage(ChatColor.RED + languageManager.getMessage("no-permission"));
            return true;
        }

        // Sem argumentos - abre o menu principal
        if (args.length == 0) {
            openMainMenu(player);
            return true;
        }

        // Processa subcomandos
        switch (args[0].toLowerCase()) {
            case "create":
                handleCreateCommand(player, args);
                break;
            case "tp":
            case "teleport":
                handleTeleportCommand(player, args);
                break;
            case "list":
                handleListCommand(player);
                break;
            case "help":
                sendHelpMessage(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + languageManager.getMessage("unknown-command"));
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    /**
     * Abre o menu principal para o jogador
     *
     * @param player Jogador
     */
    private void openMainMenu(Player player) {
        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Abrindo menu principal para " + player.getName());
        }

        worldsGUI.openMainMenu(player);
        player.sendMessage(ChatColor.GREEN + languageManager.getMessage("opening-menu"));
    }

    /**
     * Trata o comando de criação de mundo
     *
     * @param player Jogador
     * @param args Argumentos do comando
     */
    private void handleCreateCommand(Player player, String[] args) {
        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Comando de criação de mundo por " + player.getName() +
                    ", argumentos: " + (args.length > 1 ? args[1] : "nenhum"));
        }

        // Verifica permissão
        if (!player.hasPermission("worldsmanager.create") &&
                !plugin.getConfigManager().get("permissions.allow-all-players-to-create", true).equals(true)) {
            player.sendMessage(ChatColor.RED + languageManager.getMessage("no-permission"));
            return;
        }

        // Verifica limite de mundos
        List<CustomWorld> playerWorlds = plugin.getWorldManager().getPlayerWorlds(player.getUniqueId());
        int maxWorlds = plugin.getConfigManager().getMaxWorldsPerPlayer();

        if (maxWorlds > 0 && playerWorlds.size() >= maxWorlds && !player.hasPermission("worldsmanager.create.unlimited")) {
            player.sendMessage(ChatColor.RED + languageManager.getMessage("world-limit-reached")
                    .replace("%limit%", String.valueOf(maxWorlds)));
            return;
        }

        // Verifica economia
        if (plugin.getConfigManager().isEconomyEnabled()) {
            double cost = plugin.getConfigManager().getWorldCreationCost();
            // Implementar verificação de economia
            // if (!economyHook.has(player, cost)) {
            //     player.sendMessage(ChatColor.RED + languageManager.getMessage("not-enough-money")
            //             .replace("%cost%", String.valueOf(cost)));
            //     return;
            // }
        }

        // Se tiver nome como argumento, cria diretamente
        if (args.length >= 2) {
            String worldName = args[1];

            // Verifica se contém apenas caracteres válidos
            if (!worldName.matches("[a-zA-Z0-9_]+")) {
                player.sendMessage(ChatColor.RED + languageManager.getMessage("invalid-world-name-chars"));
                return;
            }

            // Verifica se o nome é válido
            if (worldName.length() < 3 || worldName.length() > 16) {
                player.sendMessage(ChatColor.RED + languageManager.getMessage("invalid-world-name-length"));
                return;
            }

            // Verifica se o nome já existe
            boolean nameExists = plugin.getWorldManager().getAllWorlds().stream()
                    .anyMatch(world -> world.getName().equalsIgnoreCase(worldName));

            if (nameExists) {
                player.sendMessage(ChatColor.RED + languageManager.getMessage("world-name-exists"));
                return;
            }

            // Usar o método de criação direta da WorldCreateGUI para manter consistência
            worldCreateGUI.createWorldDirectly(player, worldName);
        } else {
            // Abre GUI de criação - usa a instância atual
            // IMPORTANTE: Limpe qualquer estado anterior do jogador
            worldCreateGUI.clearPlayerData(player);
            worldCreateGUI.open(player);
            player.sendMessage(ChatColor.GREEN + languageManager.getMessage("opening-create-gui"));
        }
    }

    /**
     * Trata o comando de teleporte para um mundo
     *
     * @param player Jogador
     * @param args Argumentos do comando
     */
    private void handleTeleportCommand(Player player, String[] args) {
        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Comando de teleporte por " + player.getName() +
                    ", argumentos: " + (args.length > 1 ? args[1] : "nenhum"));
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + languageManager.getMessage("specify-world"));
            return;
        }

        String worldName = args[1];

        // Se o mundo começar com "wm_", é um nome interno
        CustomWorld world = null;
        if (worldName.startsWith("wm_")) {
            world = plugin.getWorldManager().getWorldByName(worldName);
        } else {
            // Procura por nome de exibição
            world = plugin.getWorldManager().getAllWorlds().stream()
                    .filter(w -> w.getName().equalsIgnoreCase(worldName))
                    .findFirst()
                    .orElse(null);
        }

        if (world == null) {
            player.sendMessage(ChatColor.RED + languageManager.getMessage("world-not-found"));
            return;
        }

        // Verifica permissão
        if (!world.canAccess(player) && !player.hasPermission("worldsmanager.teleport.others")) {
            player.sendMessage(ChatColor.RED + languageManager.getMessage("no-access-to-world"));
            return;
        }

        // Verifica economia para teleporte
        if (plugin.getConfigManager().isEconomyEnabled() && !player.hasPermission("worldsmanager.teleport.free")) {
            double cost = plugin.getConfigManager().getWorldTeleportCost();
            // Implementar verificação de economia
            // if (!economyHook.has(player, cost)) {
            //     player.sendMessage(ChatColor.RED + languageManager.getMessage("not-enough-money-teleport")
            //             .replace("%cost%", String.valueOf(cost)));
            //     return;
            // }
            // economyHook.withdraw(player, cost);
        }

        // Teleporta o jogador
        player.sendMessage(ChatColor.GREEN + languageManager.getMessage("teleporting")
                .replace("%world%", world.getName()));

        plugin.getWorldManager().teleportPlayerToWorld(player, world);
    }

    /**
     * Trata o comando de listagem de mundos
     *
     * @param player Jogador
     */
    private void handleListCommand(Player player) {
        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Comando de listagem de mundos por " + player.getName());
        }

        List<CustomWorld> accessibleWorlds = plugin.getWorldManager().getAccessibleWorlds(player.getUniqueId());

        if (accessibleWorlds.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + languageManager.getMessage("no-worlds"));
            return;
        }

        player.sendMessage(ChatColor.GREEN + languageManager.getMessage("world-list-header"));

        for (CustomWorld world : accessibleWorlds) {
            String ownerStatus = world.getOwnerUUID().equals(player.getUniqueId()) ?
                    ChatColor.GOLD + " (" + languageManager.getMessage("owner") + ")" : "";

            player.sendMessage(ChatColor.GREEN + "- " + world.getName() + ownerStatus);
        }
    }

    /**
     * Envia a mensagem de ajuda ao jogador
     *
     * @param player Jogador
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== " + languageManager.getMessage("help-header") + " ===");
        player.sendMessage(ChatColor.GREEN + "/worlds " + ChatColor.GRAY + "- " + languageManager.getMessage("help-main"));
        player.sendMessage(ChatColor.GREEN + "/worlds create [nome] " + ChatColor.GRAY + "- " + languageManager.getMessage("help-create"));
        player.sendMessage(ChatColor.GREEN + "/worlds tp <nome> " + ChatColor.GRAY + "- " + languageManager.getMessage("help-teleport"));
        player.sendMessage(ChatColor.GREEN + "/worlds list " + ChatColor.GRAY + "- " + languageManager.getMessage("help-list"));
        player.sendMessage(ChatColor.GREEN + "/worlds help " + ChatColor.GRAY + "- " + languageManager.getMessage("help-help"));
    }

    /**
     * Obtém a GUI principal do comando Worlds
     *
     * @return Interface GUI principal
     */
    public WorldsGUI getGUI() {
        return worldsGUI;
    }

    /**
     * Obtém a instância de WorldCreateGUI
     *
     * @return Instância de WorldCreateGUI
     */
    public WorldCreateGUI getWorldCreateGUI() {
        return worldCreateGUI;
    }
}