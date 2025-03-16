package com.worldsmanager.gui;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Interface gráfica principal do plugin
 */
public class WorldsGUI {

    private final WorldsManager plugin;

    public WorldsGUI(WorldsManager plugin) {
        this.plugin = plugin;
    }

    /**
     * Abre o menu principal para o jogador
     *
     * @param player Jogador para abrir o menu
     */
    public void openMainMenu(Player player) {
        // Obtém mundos do jogador
        UUID playerUUID = player.getUniqueId();
        List<CustomWorld> playerWorlds = plugin.getWorldManager().getPlayerWorlds(playerUUID);
        List<CustomWorld> trustedWorlds = new ArrayList<>();

        for (CustomWorld world : plugin.getWorldManager().getAllWorlds()) {
            if (world.getTrustedPlayers().contains(playerUUID) && !world.getOwnerUUID().equals(playerUUID)) {
                trustedWorlds.add(world);
            }
        }

        // Cria inventário
        int rows = plugin.getConfigManager().getMainGUIRows();

        // Limita o tamanho para múltiplos de 9 (tamanho do inventário)
        rows = Math.min(6, Math.max(1, rows));

        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getMainGUITitle());

        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        // Adiciona mundos do jogador
        int slot = 0;
        for (CustomWorld world : playerWorlds) {
            if (slot >= inv.getSize() - 9) {
                break; // Deixa a última linha para botões
            }

            ItemStack item = new ItemBuilder(world.getIcon())
                    .name(ChatColor.GREEN + world.getName())
                    .lore(ChatColor.GRAY + "Clique para gerenciar")
                    .lore(ChatColor.GRAY + "ou teleportar")
                    .build();

            inv.setItem(slot++, item);
        }

        // Adiciona mundos confiados
        for (CustomWorld world : trustedWorlds) {
            if (slot >= inv.getSize() - 9) {
                break; // Deixa a última linha para botões
            }

            ItemStack item = new ItemBuilder(world.getIcon())
                    .name(ChatColor.AQUA + world.getName())
                    .lore(ChatColor.GRAY + "Mundo de " +
                            Bukkit.getOfflinePlayer(world.getOwnerUUID()).getName())
                    .lore(ChatColor.GRAY + "Clique para teleportar")
                    .build();

            inv.setItem(slot++, item);
        }

        // Adiciona botão de criação
        Material createButtonMaterial = plugin.getConfigManager().getCreateButtonMaterial();
        int createButtonSlot = plugin.getConfigManager().getCreateButtonSlot();

        // Verifica se o slot está dentro do inventário
        createButtonSlot = Math.min(inv.getSize() - 1, Math.max(0, createButtonSlot));

        ItemStack createButton = new ItemBuilder(createButtonMaterial)
                .name(ChatColor.GREEN + "Criar Novo Mundo")
                .lore(ChatColor.GRAY + "Clique para criar um novo mundo")
                .build();

        inv.setItem(createButtonSlot, createButton);

        // Abre o inventário para o jogador
        player.openInventory(inv);
    }

    /**
     * Obtém o título principal do menu
     *
     * @return Título formatado
     */
    public String getMainGUITitle() {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getMainGUITitle());
    }

    /**
     * Abre o menu de gerenciamento de mundo
     *
     * @param player Jogador
     * @param world Mundo a ser gerenciado
     */
    public void openWorldManagementMenu(Player player, CustomWorld world) {
        int rows = 4;
        String title = ChatColor.GREEN + world.getName() + " - " + ChatColor.GRAY + "Gerenciamento";
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        // Informações do mundo
        ItemStack infoItem = new ItemBuilder(world.getIcon())
                .name(ChatColor.GREEN + world.getName())
                .lore(ChatColor.GRAY + "Proprietário: " +
                        Bukkit.getOfflinePlayer(world.getOwnerUUID()).getName())
                .lore(ChatColor.GRAY + "Jogadores confiáveis: " + world.getTrustedPlayers().size())
                .build();

        inv.setItem(4, infoItem);

        // Botão de teleporte
        ItemStack teleportButton = new ItemBuilder(Material.ENDER_PEARL)
                .name(ChatColor.GREEN + "Teleportar")
                .lore(ChatColor.GRAY + "Clique para teleportar para este mundo")
                .build();

        inv.setItem(10, teleportButton);

        // Botão de configurações
        ItemStack settingsButton = new ItemBuilder(Material.REDSTONE)
                .name(ChatColor.YELLOW + "Configurações")
                .lore(ChatColor.GRAY + "Clique para modificar as configurações")
                .build();

        inv.setItem(12, settingsButton);

        // Botão de jogadores confiáveis
        ItemStack playersButton = new ItemBuilder(Material.PLAYER_HEAD)
                .name(ChatColor.AQUA + "Jogadores Confiáveis")
                .lore(ChatColor.GRAY + "Clique para gerenciar jogadores confiáveis")
                .build();

        inv.setItem(14, playersButton);

        // Botão de excluir mundo
        ItemStack deleteButton = new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Excluir Mundo")
                .lore(ChatColor.GRAY + "Clique para excluir este mundo")
                .lore(ChatColor.RED + "Esta ação é irreversível!")
                .build();

        inv.setItem(16, deleteButton);

        // Botão de voltar
        ItemStack backButton = new ItemBuilder(Material.ARROW)
                .name(ChatColor.YELLOW + "Voltar")
                .lore(ChatColor.GRAY + "Clique para voltar ao menu principal")
                .build();

        inv.setItem(inv.getSize() - 5, backButton);

        // Abre o inventário
        player.openInventory(inv);
    }
}