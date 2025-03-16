package com.worldsmanager.listeners;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.gui.WorldCreateGUI;
import com.worldsmanager.gui.WorldsGUI;
import com.worldsmanager.models.CustomWorld;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener para eventos de clique em menus
 */
public class MenuClickListener implements Listener {

    private final WorldsManager plugin;
    private final WorldsGUI worldsGUI;
    private final Map<UUID, CustomWorld> managingWorld = new HashMap<>();
    private final Map<UUID, Boolean> confirmingDeletion = new HashMap<>();

    public MenuClickListener(WorldsManager plugin) {
        this.plugin = plugin;
        this.worldsGUI = new WorldsGUI(plugin);

        // Registra este listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getLogger().info("MenuClickListener inicializado");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Se o jogador está no modo de criação de mundo no WorldCreateGUI,
        // não devemos processar esses eventos em outro lugar
        WorldCreateGUI createGUI = plugin.getWorldsCommand().getWorldCreateGUI();
        if (createGUI != null && createGUI.getPlayerStage(player) != WorldCreateGUI.CreationStage.NONE) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Evento de chat ignorado - jogador está no processo de criação de mundo");
            return;
        }

        // Processa outros eventos de chat se necessário
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        int slot = event.getRawSlot();

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Clique no inventário por " + player.getName() +
                    ", título: " + title + ", slot: " + slot);
        }

        // Verifica se é um inventário válido do plugin
        if (title.equals(worldsGUI.getMainGUITitle()) || title.contains("Seus Mundos")) {
            event.setCancelled(true);
            handleMainGUIClick(player, slot);
            return;
        }

        // Verifica se é o menu de criação de mundo - verificação mais flexível
        String createTitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getCreateGUITitle());

        if (title.equals(createTitle) || title.contains("Criar Novo Mundo")) {
            event.setCancelled(true);
            handleWorldCreateGUIClick(player, slot, event.getClickedInventory());
            return;
        }

        // Verifica se é o menu de gerenciamento de mundo
        if (title.startsWith(ChatColor.GREEN.toString()) && title.contains(" - " + ChatColor.GRAY + "Gerenciamento")) {
            event.setCancelled(true);
            handleWorldManagementGUIClick(player, slot);
            return;
        }

        // Verifica se é o menu de confirmação de exclusão
        String confirmTitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getConfirmGUITitle());

        if (title.equals(confirmTitle) || title.contains("Confirmar Exclusão")) {
            event.setCancelled(true);
            handleDeletionConfirmation(player, slot);
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String title = event.getView().getTitle();

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Inventário fechado por " + player.getName() +
                    ", título: " + title);
        }

        // Se estava confirmando uma exclusão e fechou o inventário, cancela
        String confirmTitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getConfirmGUITitle());

        if (title.equals(confirmTitle) || title.contains("Confirmar Exclusão")) {
            if (confirmingDeletion.containsKey(playerUUID) && confirmingDeletion.get(playerUUID)) {
                confirmingDeletion.remove(playerUUID);
                player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-deletion-cancelled"));
            }
        }
        // Limpa dados se o jogador está fechando um menu do plugin
        else if (title.equals(worldsGUI.getMainGUITitle()) || title.contains("Seus Mundos") ||
                (title.startsWith(ChatColor.GREEN.toString()) && title.contains(" - " + ChatColor.GRAY + "Gerenciamento"))) {
            managingWorld.remove(playerUUID);
        }
    }

    /**
     * Trata os cliques no menu principal
     *
     * @param player Jogador que clicou
     * @param slot Slot clicado
     */
    private void handleMainGUIClick(Player player, int slot) {
        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Tratando clique no menu principal, slot: " + slot);
        }

        // Verifica se é o botão de criar mundo
        int createButtonSlot = plugin.getConfigManager().getCreateButtonSlot();
        if (slot == createButtonSlot) {
            player.closeInventory();
            // Abre a GUI de criação
            plugin.getWorldsCommand().getWorldCreateGUI().open(player);
            return;
        }

        // Verifica se é um mundo
        ItemStack clickedItem = player.getOpenInventory().getItem(slot);
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Obtém o nome do mundo a partir do item
        String worldName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Clique em mundo: " + worldName);
        }

        // Procura o mundo clicado
        CustomWorld world = null;
        for (CustomWorld w : plugin.getWorldManager().getAllWorlds()) {
            if (w.getName().equals(worldName)) {
                world = w;
                break;
            }
        }

        if (world == null) {
            player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-not-found"));
            return;
        }

        // Se é o dono, abre o menu de gerenciamento
        if (world.getOwnerUUID().equals(player.getUniqueId())) {
            player.closeInventory();
            managingWorld.put(player.getUniqueId(), world);
            worldsGUI.openWorldManagementMenu(player, world);
        } else {
            // Senão, teleporta direto
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("teleporting")
                    .replace("%world%", world.getName()));
            plugin.getWorldManager().teleportPlayerToWorld(player, world);
        }
    }

    /**
     * Trata os cliques no menu de criação de mundo
     *
     * @param player Jogador que clicou
     * @param slot Slot clicado
     * @param inventory Inventário clicado
     */
    private void handleWorldCreateGUIClick(Player player, int slot, Inventory inventory) {
        // Verifica se o slot clicado está dentro do inventário
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Tratando clique no menu de criação, slot: " + slot);
        }

        ItemStack clickedItem = inventory.getItem(slot);
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Se clicou no botão de cancelar
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-creation-cancelled"));

            // Garante que o estado do jogador é resetado
            WorldCreateGUI createGUI = plugin.getWorldsCommand().getWorldCreateGUI();
            if (createGUI != null) {
                createGUI.resetPlayerState(player);
            }

            return;
        }

        // Deixe o WorldCreateGUI lidar com seleção de ícones
        // Este método não fará mais validações próprias de ícone
    }

    /**
     * Trata os cliques no menu de gerenciamento de mundo
     *
     * @param player Jogador que clicou
     * @param slot Slot clicado
     */
    private void handleWorldManagementGUIClick(Player player, int slot) {
        CustomWorld world = managingWorld.get(player.getUniqueId());
        if (world == null) {
            player.closeInventory();
            return;
        }

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Tratando clique no menu de gerenciamento, slot: " +
                    slot + ", mundo: " + world.getName());
        }

        switch (slot) {
            case 10: // Teleporte
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("teleporting")
                        .replace("%world%", world.getName()));
                plugin.getWorldManager().teleportPlayerToWorld(player, world);
                break;

            case 12: // Configurações
                player.closeInventory();
                // Abrir menu de configurações (pode ser implementado como uma classe separada)
                player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("settings-menu-not-implemented"));
                break;

            case 14: // Jogadores
                player.closeInventory();
                // Abrir menu de jogadores (pode ser implementado como uma classe separada)
                player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("players-menu-not-implemented"));
                break;

            case 16: // Excluir
                player.closeInventory();
                confirmWorldDeletion(player, world);
                break;

            case 31: // Voltar
            case 40: // Também pode ser usado como botão de voltar
                player.closeInventory();
                worldsGUI.openMainMenu(player);
                break;
        }
    }

    /**
     * Exibe o menu de confirmação de exclusão de mundo
     *
     * @param player Jogador
     * @param world Mundo a ser excluído
     */
    private void confirmWorldDeletion(Player player, CustomWorld world) {
        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Exibindo confirmação de exclusão para " +
                    world.getName());
        }

        // Cria um inventário menor para a confirmação
        String confirmTitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getConfirmGUITitle());
        Inventory inv = Bukkit.createInventory(null, 9, confirmTitle);

        // Adiciona o item de informação do mundo no meio
        ItemStack worldInfo = new ItemBuilder(world.getIcon())
                .name(ChatColor.RED + world.getName())
                .lore(ChatColor.RED + plugin.getLanguageManager().getMessage("confirm-world-deletion-warning"))
                .build();
        inv.setItem(4, worldInfo);

        // Adiciona o botão de confirmação
        ItemStack confirmButton = new ItemBuilder(Material.LIME_WOOL)
                .name(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageManager().getMessage("confirm-yes")))
                .lore(ChatColor.RED + plugin.getLanguageManager().getMessage("confirm-world-deletion-warning"))
                .build();
        inv.setItem(2, confirmButton);

        // Adiciona o botão de cancelamento
        ItemStack cancelButton = new ItemBuilder(Material.RED_WOOL)
                .name(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageManager().getMessage("confirm-no")))
                .build();
        inv.setItem(6, cancelButton);

        // Guarda o mundo que está sendo excluído para uso posterior
        managingWorld.put(player.getUniqueId(), world);
        confirmingDeletion.put(player.getUniqueId(), true);

        // Abre o inventário
        player.openInventory(inv);
    }

    /**
     * Processa a confirmação de exclusão de mundo
     *
     * @param player Jogador
     * @param slot Slot clicado
     */
    private void handleDeletionConfirmation(Player player, int slot) {
        UUID playerUUID = player.getUniqueId();
        CustomWorld world = managingWorld.get(playerUUID);

        if (world == null || !confirmingDeletion.containsKey(playerUUID) || !confirmingDeletion.get(playerUUID)) {
            player.closeInventory();
            return;
        }

        // DEBUG log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] MenuClickListener: Tratando confirmação de exclusão, slot: " +
                    slot + ", mundo: " + world.getName());
        }

        // Reseta o estado de confirmação
        confirmingDeletion.remove(playerUUID);

        // Slot 2 = Botão de confirmar
        if (slot == 2) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-deleted-processing"));

            // Executa a exclusão do mundo
            plugin.getWorldManager().deleteWorld(world, player).thenAccept(success -> {
                if (success) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-deleted-success"));
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-deletion-failed"));
                    });
                }
            });
        }
        // Slot 6 = Botão de cancelar
        else if (slot == 6) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-deletion-cancelled"));
        }
    }

    /**
     * Classe auxiliar para construir ItemStacks facilmente
     */
    private static class ItemBuilder {
        private final ItemStack item;

        public ItemBuilder(Material material) {
            this.item = new ItemStack(material);
        }

        public ItemBuilder name(String name) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            item.setItemMeta(meta);
            return this;
        }

        public ItemBuilder lore(String line) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            java.util.List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new java.util.ArrayList<>();
            }
            lore.add(line);
            meta.setLore(lore);
            item.setItemMeta(meta);
            return this;
        }

        public ItemStack build() {
            return item;
        }
    }
}