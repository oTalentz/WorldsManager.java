package com.worldsmanager.gui;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * GUI para criação de mundos
 */
public class WorldCreateGUI implements Listener {

    private final WorldsManager plugin;
    private final Map<UUID, CreationStage> playerStages = new HashMap<>();
    private final Map<UUID, String> pendingNames = new HashMap<>();
    private final Map<UUID, Material> pendingIcons = new HashMap<>();

    // Enum para monitorar o estágio de criação
    private enum CreationStage {
        NONE,
        AWAITING_NAME,
        SELECTING_ICON
    }

    /**
     * Construtor
     *
     * @param plugin Instância do plugin
     */
    public WorldCreateGUI(WorldsManager plugin) {
        this.plugin = plugin;

        // Registra como listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Abre a GUI de criação para o jogador
     *
     * @param player Jogador
     */
    public void open(Player player) {
        // Cancela qualquer criação pendente
        playerStages.put(player.getUniqueId(), CreationStage.NONE);
        pendingNames.remove(player.getUniqueId());
        pendingIcons.remove(player.getUniqueId());

        // Solicita o nome do mundo via chat
        playerStages.put(player.getUniqueId(), CreationStage.AWAITING_NAME);

        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("enter-world-name"));
        player.sendMessage(ChatColor.GRAY + plugin.getLanguageManager().getMessage("cancel-prompt"));
    }

    /**
     * Abre a GUI para seleção de ícone
     *
     * @param player Jogador
     * @param worldName Nome do mundo
     */
    private void openIconSelection(Player player, String worldName) {
        pendingNames.put(player.getUniqueId(), worldName);
        playerStages.put(player.getUniqueId(), CreationStage.SELECTING_ICON);

        // Cria o inventário
        Inventory inv = Bukkit.createInventory(null, 54,
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfigManager().getCreateGUITitle()));

        // Adiciona ícones comuns
        addIcon(inv, 10, Material.GRASS_BLOCK, "Terrestre");
        addIcon(inv, 11, Material.STONE, "Caverna");
        addIcon(inv, 12, Material.SAND, "Deserto");
        addIcon(inv, 13, Material.SNOW_BLOCK, "Neve");
        addIcon(inv, 14, Material.JUNGLE_LOG, "Selva");
        addIcon(inv, 15, Material.WATER_BUCKET, "Oceano");
        addIcon(inv, 16, Material.LAVA_BUCKET, "Nether");

        addIcon(inv, 19, Material.NETHERRACK, "Nether");
        addIcon(inv, 20, Material.END_STONE, "End");
        addIcon(inv, 21, Material.DIAMOND_BLOCK, "Criativo");
        addIcon(inv, 22, Material.OAK_SAPLING, "Survival");
        addIcon(inv, 23, Material.COBWEB, "Skyblock");
        addIcon(inv, 24, Material.DRAGON_EGG, "Dragon");
        addIcon(inv, 25, Material.BEACON, "Build");

        // Adiciona botão de cancelar
        ItemStack cancelButton = new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Cancelar")
                .build();

        inv.setItem(49, cancelButton);

        // Abre o inventário
        player.openInventory(inv);
    }

    /**
     * Adiciona um ícone ao inventário
     *
     * @param inv Inventário
     * @param slot Slot
     * @param material Material
     * @param type Tipo de mundo
     */
    private void addIcon(Inventory inv, int slot, Material material, String type) {
        ItemStack icon = new ItemBuilder(material)
                .name(ChatColor.GREEN + "Mundo " + type)
                .lore(ChatColor.GRAY + "Clique para selecionar")
                .build();

        inv.setItem(slot, icon);
    }

    /**
     * Manipula eventos de clique no inventário
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        // Ignora se não está no estágio de seleção de ícone
        if (!playerStages.containsKey(playerUUID) ||
                playerStages.get(playerUUID) != CreationStage.SELECTING_ICON) {
            return;
        }

        // Verifica se é o inventário correto
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getCreateGUITitle());

        if (!event.getView().getTitle().equals(title)) {
            return;
        }

        event.setCancelled(true);

        // Se clicou fora ou é slot inválido
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        // Se clicou no botão de cancelar
        if (event.getCurrentItem().getType() == Material.BARRIER) {
            player.closeInventory();
            playerStages.put(playerUUID, CreationStage.NONE);
            pendingNames.remove(playerUUID);
            player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-creation-cancelled"));
            return;
        }

        // Seleciona o ícone
        Material selectedIcon = event.getCurrentItem().getType();

        // Verifica se o ícone é válido
        if (!plugin.getConfigManager().isValidIconMaterial(selectedIcon)) {
            player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("invalid-icon"));
            return;
        }

        // Armazena o ícone selecionado
        pendingIcons.put(playerUUID, selectedIcon);

        // Fecha o inventário
        player.closeInventory();

        // Cria o mundo
        createWorld(player);
    }

    /**
     * Manipula eventos de fechamento do inventário
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Ignora se não está no estágio de seleção de ícone
        if (!playerStages.containsKey(playerUUID) ||
                playerStages.get(playerUUID) != CreationStage.SELECTING_ICON) {
            return;
        }

        // Verifica se é o inventário correto
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getCreateGUITitle());

        if (!event.getView().getTitle().equals(title)) {
            return;
        }

        // Se tem ícone, está criando. Senão, cancelou.
        if (!pendingIcons.containsKey(playerUUID)) {
            // Cancela se fechou sem selecionar ícone
            playerStages.put(playerUUID, CreationStage.NONE);
            pendingNames.remove(playerUUID);

            // Agenda mensagem para o próximo tick para garantir que será exibida após o fechamento
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-creation-cancelled"));
            });
        }
    }

    /**
     * Manipula eventos de chat para capturar o nome do mundo
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Ignora se não está aguardando nome
        if (!playerStages.containsKey(playerUUID) ||
                playerStages.get(playerUUID) != CreationStage.AWAITING_NAME) {
            return;
        }

        event.setCancelled(true);

        String worldName = event.getMessage();

        // Verifica se o jogador cancelou
        if (worldName.equalsIgnoreCase("cancel")) {
            playerStages.put(playerUUID, CreationStage.NONE);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-creation-cancelled"));
            });
            return;
        }

        // Verifica se o nome é válido
        if (worldName.length() < 3 || worldName.length() > 16) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("invalid-world-name-length"));
            });
            return;
        }

        // Verifica se contém apenas caracteres válidos
        if (!worldName.matches("[a-zA-Z0-9_]+")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("invalid-world-name-chars"));
            });
            return;
        }

        // Verifica se o nome já existe
        boolean nameExists = plugin.getWorldManager().getAllWorlds().stream()
                .anyMatch(world -> world.getName().equalsIgnoreCase(worldName));

        if (nameExists) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-name-exists"));
            });
            return;
        }

        // Abre a GUI de seleção de ícone
        Bukkit.getScheduler().runTask(plugin, () -> {
            openIconSelection(player, worldName);
        });
    }

    /**
     * Cria o mundo após a seleção de nome e ícone
     *
     * @param player Jogador
     */
    private void createWorld(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Verifica se tem nome e ícone
        if (!pendingNames.containsKey(playerUUID) || !pendingIcons.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-creation-error"));
            playerStages.put(playerUUID, CreationStage.NONE);
            return;
        }

        String worldName = pendingNames.get(playerUUID);
        Material selectedIcon = pendingIcons.get(playerUUID);

        // Limpa os dados pendentes
        playerStages.put(playerUUID, CreationStage.NONE);
        pendingNames.remove(playerUUID);
        pendingIcons.remove(playerUUID);

        // Mensagem de criação
        player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("creating-world")
                .replace("%name%", worldName));

        // Cria o mundo
        plugin.getWorldManager().createWorld(worldName, player.getUniqueId(), selectedIcon, player)
                .thenAccept(customWorld -> {
                    if (customWorld != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-created-success"));
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-creation-failed"));
                        });
                    }
                });
    }
}