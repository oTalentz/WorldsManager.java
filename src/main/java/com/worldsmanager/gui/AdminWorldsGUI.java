package com.worldsmanager.gui;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * GUI para administração de mundos
 */
public class AdminWorldsGUI implements Listener {

    private final WorldsManager plugin;
    private final Logger logger;

    // Mapa para rastrear inventários abertos
    private final Map<UUID, AdminGUIType> openInventories = new HashMap<>();

    // Mapa para rastrear mundos selecionados
    private final Map<UUID, CustomWorld> selectedWorlds = new HashMap<>();

    // Mapa para rastrear jogadores selecionados
    private final Map<UUID, UUID> selectedPlayers = new HashMap<>();

    // Armazena mapeamentos entre slots e mundos/jogadores
    private final Map<UUID, Map<Integer, CustomWorld>> playerWorldSlotMap = new HashMap<>();
    private final Map<UUID, Map<Integer, OfflinePlayer>> playerPlayerSlotMap = new HashMap<>();

    // Armazena filtros de pesquisa
    private final Map<UUID, String> searchFilters = new HashMap<>();

    // Armazena páginas atuais
    private final Map<UUID, Integer> currentPages = new HashMap<>();

    // Constantes
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int PAGE_INDICATOR_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    private InventoryClickEvent event;

    // Tipos de inventário admin
    public enum AdminGUIType {
        MAIN,
        ALL_WORLDS,
        PLAYER_WORLDS,
        WORLD_DETAILS,
        PLAYER_MANAGEMENT,
        CONFIRM_DELETE
    }

    public AdminWorldsGUI(WorldsManager plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Registra como listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Abre o menu principal de administração
     *
     * @param player Jogador admin
     */
    public void openMainMenu(Player player) {
        String title = plugin.getLanguageManager().getMessage("gui.admin.title");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Reset de estados
        playerWorldSlotMap.put(player.getUniqueId(), new HashMap<>());
        playerPlayerSlotMap.put(player.getUniqueId(), new HashMap<>());
        currentPages.put(player.getUniqueId(), 0);

        // Adiciona item para ver todos os mundos
        ItemStack allWorldsItem = new ItemBuilder(Material.COMPASS)
                .name(ChatColor.GREEN + plugin.getLanguageManager().getMessage("gui.admin.all-worlds"))
                .lore(ChatColor.GRAY + "Clique para ver todos os mundos")
                .lore(ChatColor.GRAY + "Total: " + plugin.getWorldManager().getAllWorlds().size())
                .build();
        inv.setItem(20, allWorldsItem);

        // Adiciona item para pesquisar por jogador
        ItemStack playerSearchItem = new ItemBuilder(Material.PLAYER_HEAD)
                .name(ChatColor.GREEN + plugin.getLanguageManager().getMessage("gui.admin.player-search"))
                .lore(ChatColor.GRAY + "Clique para buscar mundos por jogador")
                .build();
        inv.setItem(22, playerSearchItem);

        // Adiciona item de estatísticas
        ItemStack statsItem = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name(ChatColor.AQUA + plugin.getLanguageManager().getMessage("gui.admin.stats"))
                .lore(ChatColor.GRAY + "Total de mundos: " + plugin.getWorldManager().getAllWorlds().size())
                .lore(ChatColor.GRAY + "Mundos carregados: " + Bukkit.getWorlds().size())
                .lore(ChatColor.GRAY + "Jogadores com mundos: " + getUniqueWorldOwners().size())
                .build();
        inv.setItem(24, statsItem);

        // Adiciona item de reload
        ItemStack reloadItem = new ItemBuilder(Material.REDSTONE)
                .name(ChatColor.GOLD + plugin.getLanguageManager().getMessage("gui.admin.reload"))
                .lore(ChatColor.GRAY + "Clique para recarregar o plugin")
                .build();
        inv.setItem(40, reloadItem);

        // Abre o inventário
        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), AdminGUIType.MAIN);
    }

    /**
     * Abre a tela de todos os mundos
     *
     * @param player Jogador admin
     */
    public void openAllWorldsMenu(Player player) {
        openAllWorldsMenu(player, 0);
    }

    /**
     * Abre a tela de todos os mundos em uma página específica
     *
     * @param player Jogador admin
     * @param page Página a ser exibida
     */
    public void openAllWorldsMenu(Player player, int page) {
        String title = plugin.getLanguageManager().getMessage("gui.admin.all-worlds-title");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Obtém todos os mundos
        List<CustomWorld> allWorlds = new ArrayList<>(plugin.getWorldManager().getAllWorlds());

        // Aplica filtro se existir
        String filter = searchFilters.getOrDefault(player.getUniqueId(), "");
        if (!filter.isEmpty()) {
            allWorlds = allWorlds.stream()
                    .filter(world -> world.getName().toLowerCase().contains(filter.toLowerCase()) ||
                            world.getWorldName().toLowerCase().contains(filter.toLowerCase()) ||
                            Bukkit.getOfflinePlayer(world.getOwnerUUID()).getName() != null &&
                                    Bukkit.getOfflinePlayer(world.getOwnerUUID()).getName().toLowerCase().contains(filter.toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Ordena por nome
        allWorlds.sort(Comparator.comparing(CustomWorld::getName));

        // Calcula páginas
        int totalPages = (int) Math.ceil((double) allWorlds.size() / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        currentPages.put(player.getUniqueId(), page);

        // Exibe mundos da página atual
        Map<Integer, CustomWorld> slotMap = new HashMap<>();
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allWorlds.size());

        for (int i = startIndex; i < endIndex; i++) {
            CustomWorld world = allWorlds.get(i);
            int slot = i - startIndex;

            // Obtém nome do proprietário
            OfflinePlayer owner = Bukkit.getOfflinePlayer(world.getOwnerUUID());
            String ownerName = owner.getName() != null ? owner.getName() : "Desconhecido";

            // Cria o item
            ItemStack worldItem = new ItemBuilder(world.getIcon())
                    .name(ChatColor.GREEN + world.getName())
                    .lore(ChatColor.GRAY + "ID: " + world.getWorldName())
                    .lore(ChatColor.GRAY + "Proprietário: " + ownerName)
                    .lore(ChatColor.GRAY + "Status: " + (world.isLoaded() ? ChatColor.GREEN + "Carregado" : ChatColor.RED + "Não carregado"))
                    .lore("")
                    .lore(ChatColor.YELLOW + "Clique para gerenciar")
                    .build();

            inv.setItem(slot, worldItem);
            slotMap.put(slot, world);
        }

        // Atualiza o mapa de slots
        playerWorldSlotMap.put(player.getUniqueId(), slotMap);

        // Adiciona botões de navegação
        if (page > 0) {
            ItemStack prevPage = new ItemBuilder(Material.ARROW)
                    .name(ChatColor.YELLOW + "Página Anterior")
                    .build();
            inv.setItem(PREV_PAGE_SLOT, prevPage);
        }

        ItemStack pageIndicator = new ItemBuilder(Material.PAPER)
                .name(ChatColor.GOLD + "Página " + (page + 1) + " de " + Math.max(1, totalPages))
                .build();
        inv.setItem(PAGE_INDICATOR_SLOT, pageIndicator);

        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemBuilder(Material.ARROW)
                    .name(ChatColor.YELLOW + "Próxima Página")
                    .build();
            inv.setItem(NEXT_PAGE_SLOT, nextPage);
        }

        // Adiciona botão de voltar
        ItemStack backButton = new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Voltar")
                .build();
        inv.setItem(48, backButton);

        // Adiciona botão de filtro
        ItemStack filterButton = new ItemBuilder(Material.HOPPER)
                .name(ChatColor.AQUA + "Filtrar Mundos")
                .lore(ChatColor.GRAY + "Filtro atual: " + (filter.isEmpty() ? "Nenhum" : filter))
                .build();
        inv.setItem(50, filterButton);

        // Abre o inventário
        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), AdminGUIType.ALL_WORLDS);
    }

    /**
     * Abre a tela de mundos de um jogador específico
     *
     * @param player Jogador admin
     * @param targetPlayer Jogador alvo
     */
    public void openPlayerWorldsMenu(Player player, OfflinePlayer targetPlayer) {
        String title = plugin.getLanguageManager().getMessage("gui.admin.player-worlds-title", targetPlayer.getName());
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Obtém mundos do jogador
        List<CustomWorld> playerWorlds = plugin.getWorldManager().getPlayerWorlds(targetPlayer.getUniqueId());

        // Exibe mundos do jogador
        Map<Integer, CustomWorld> slotMap = new HashMap<>();
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, playerWorlds.size()); i++) {
            CustomWorld world = playerWorlds.get(i);

            // Cria o item
            ItemStack worldItem = new ItemBuilder(world.getIcon())
                    .name(ChatColor.GREEN + world.getName())
                    .lore(ChatColor.GRAY + "ID: " + world.getWorldName())
                    .lore(ChatColor.GRAY + "Status: " + (world.isLoaded() ? ChatColor.GREEN + "Carregado" : ChatColor.RED + "Não carregado"))
                    .lore("")
                    .lore(ChatColor.YELLOW + "Clique para gerenciar")
                    .build();

            inv.setItem(i, worldItem);
            slotMap.put(i, world);
        }

        // Atualiza o mapa de slots
        playerWorldSlotMap.put(player.getUniqueId(), slotMap);
        selectedPlayers.put(player.getUniqueId(), targetPlayer.getUniqueId());

        // Adiciona item do jogador
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(targetPlayer);
            meta.setDisplayName(ChatColor.GOLD + targetPlayer.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "UUID: " + targetPlayer.getUniqueId());
            lore.add(ChatColor.GRAY + "Total de mundos: " + playerWorlds.size());
            meta.setLore(lore);
            playerHead.setItemMeta(meta);
        }
        inv.setItem(49, playerHead);

        // Adiciona botão de voltar
        ItemStack backButton = new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Voltar")
                .build();
        inv.setItem(45, backButton);

        // Abre o inventário
        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), AdminGUIType.PLAYER_WORLDS);
    }

    /**
     * Abre a tela de detalhes de um mundo
     *
     * @param player Jogador admin
     * @param world Mundo a ser exibido
     */
    public void openWorldDetailsMenu(Player player, CustomWorld world) {
        String title = plugin.getLanguageManager().getMessage("gui.admin.world-details-title", world.getName());
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Informações do mundo
        OfflinePlayer owner = Bukkit.getOfflinePlayer(world.getOwnerUUID());
        String ownerName = owner.getName() != null ? owner.getName() : "Desconhecido";

        // Item do mundo
        ItemStack worldItem = new ItemBuilder(world.getIcon())
                .name(ChatColor.GREEN + world.getName())
                .lore(ChatColor.GRAY + "ID: " + world.getWorldName())
                .lore(ChatColor.GRAY + "Proprietário: " + ownerName)
                .lore(ChatColor.GRAY + "Status: " + (world.isLoaded() ? ChatColor.GREEN + "Carregado" : ChatColor.RED + "Não carregado"))
                .lore(ChatColor.GRAY + "Jogadores confiados: " + world.getTrustedPlayers().size())
                .build();
        inv.setItem(4, worldItem);

        // Botão de teleporte
        ItemStack teleportButton = new ItemBuilder(Material.ENDER_PEARL)
                .name(ChatColor.GREEN + "Teleportar")
                .lore(ChatColor.GRAY + "Clique para teleportar para este mundo")
                .build();
        inv.setItem(19, teleportButton);

        // Botão de carregar/descarregar
        if (world.isLoaded()) {
            ItemStack unloadButton = new ItemBuilder(Material.REDSTONE_TORCH)
                    .name(ChatColor.YELLOW + "Descarregar Mundo")
                    .lore(ChatColor.GRAY + "Clique para descarregar este mundo")
                    .build();
            inv.setItem(21, unloadButton);
        } else {
            ItemStack loadButton = new ItemBuilder(Material.TORCH)
                    .name(ChatColor.GREEN + "Carregar Mundo")
                    .lore(ChatColor.GRAY + "Clique para carregar este mundo")
                    .build();
            inv.setItem(21, loadButton);
        }

        // Botão de gerenciar jogadores
        ItemStack playersButton = new ItemBuilder(Material.PLAYER_HEAD)
                .name(ChatColor.AQUA + "Gerenciar Jogadores")
                .lore(ChatColor.GRAY + "Clique para gerenciar jogadores confiados")
                .build();
        inv.setItem(23, playersButton);

        // Botão de excluir mundo
        ItemStack deleteButton = new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Excluir Mundo")
                .lore(ChatColor.GRAY + "Clique para excluir este mundo")
                .lore(ChatColor.RED + "Esta ação não pode ser desfeita!")
                .build();
        inv.setItem(25, deleteButton);

        // Botão de voltar
        ItemStack backButton = new ItemBuilder(Material.ARROW)
                .name(ChatColor.YELLOW + "Voltar")
                .build();
        inv.setItem(49, backButton);

        // Salva o mundo selecionado
        selectedWorlds.put(player.getUniqueId(), world);

        // Abre o inventário
        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), AdminGUIType.WORLD_DETAILS);
    }

    /**
     * Abre a tela de confirmação de exclusão
     *
     * @param player Jogador admin
     * @param world Mundo a ser excluído
     */
    public void openConfirmDeleteMenu(Player player, CustomWorld world) {
        String title = plugin.getLanguageManager().getMessage("gui.admin.confirm-delete-title");
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Informações do mundo
        OfflinePlayer owner = Bukkit.getOfflinePlayer(world.getOwnerUUID());
        String ownerName = owner.getName() != null ? owner.getName() : "Desconhecido";

        // Item do mundo
        ItemStack worldItem = new ItemBuilder(world.getIcon())
                .name(ChatColor.RED + world.getName())
                .lore(ChatColor.GRAY + "ID: " + world.getWorldName())
                .lore(ChatColor.GRAY + "Proprietário: " + ownerName)
                .lore(ChatColor.RED + "ATENÇÃO!")
                .lore(ChatColor.RED + "Esta ação não pode ser desfeita!")
                .build();
        inv.setItem(13, worldItem);

        // Botão de confirmação
        ItemStack confirmButton = new ItemBuilder(Material.LIME_WOOL)
                .name(ChatColor.GREEN + "Confirmar Exclusão")
                .lore(ChatColor.RED + "Clique para confirmar a exclusão")
                .build();
        inv.setItem(11, confirmButton);

        // Botão de cancelamento
        ItemStack cancelButton = new ItemBuilder(Material.RED_WOOL)
                .name(ChatColor.RED + "Cancelar")
                .lore(ChatColor.GRAY + "Clique para cancelar a exclusão")
                .build();
        inv.setItem(15, cancelButton);

        // Salva o mundo selecionado
        selectedWorlds.put(player.getUniqueId(), world);

        // Abre o inventário
        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), AdminGUIType.CONFIRM_DELETE);
    }

    /**
     * Abre o menu de gerenciamento de jogadores de um mundo
     *
     * @param player Jogador admin
     * @param world Mundo
     */
    public void openPlayerManagementMenu(Player player, CustomWorld world) {
        String title = plugin.getLanguageManager().getMessage("gui.admin.player-management-title", world.getName());
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Exibe jogadores confiados
        List<UUID> trustedPlayers = world.getTrustedPlayers();
        Map<Integer, OfflinePlayer> playerMap = new HashMap<>();

        for (int i = 0; i < Math.min(36, trustedPlayers.size()); i++) {
            UUID playerUUID = trustedPlayers.get(i);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);

            // Cria item do jogador
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(offlinePlayer);
                meta.setDisplayName(ChatColor.AQUA + (offlinePlayer.getName() != null ? offlinePlayer.getName() : "Jogador Desconhecido"));
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "UUID: " + playerUUID,
                        ChatColor.RED + "Clique para remover"
                ));
                playerHead.setItemMeta(meta);
            }

            inv.setItem(i, playerHead);
            playerMap.put(i, offlinePlayer);
        }

        // Adiciona botão para adicionar jogador
        ItemStack addButton = new ItemBuilder(Material.EMERALD)
                .name(ChatColor.GREEN + "Adicionar Jogador")
                .lore(ChatColor.GRAY + "Clique para adicionar um jogador")
                .build();
        inv.setItem(49, addButton);

        // Adiciona botão de voltar
        ItemStack backButton = new ItemBuilder(Material.ARROW)
                .name(ChatColor.YELLOW + "Voltar")
                .build();
        inv.setItem(45, backButton);

        // Salva o mundo selecionado e mapa de jogadores
        selectedWorlds.put(player.getUniqueId(), world);
        playerPlayerSlotMap.put(player.getUniqueId(), playerMap);

        // Abre o inventário
        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), AdminGUIType.PLAYER_MANAGEMENT);
    }

    /**
     * Solicita um nome de jogador para adicionar à lista de confiados
     *
     * @param player Jogador admin
     * @param world Mundo
     */
    public void promptForPlayerName(Player player, CustomWorld world) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Digite o nome do jogador que você deseja adicionar:");
        player.sendMessage(ChatColor.GRAY + "Digite 'cancel' para cancelar");

        // Armazena o mundo para uso posterior
        selectedWorlds.put(player.getUniqueId(), world);
    }

    /**
     * Handler para eventos de clique em inventário
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (!openInventories.containsKey(playerUUID)) {
            return;
        }

        // Cancela o evento para evitar modificações no inventário
        event.setCancelled(true);

        // Verifica se é um clique válido
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        int slot = event.getRawSlot();
        AdminGUIType guiType = openInventories.get(playerUUID);

        switch (guiType) {
            case MAIN:
                handleMainMenuClick(player, slot);
                break;

            case ALL_WORLDS:
                handleAllWorldsMenuClick(player, slot);
                break;

            case PLAYER_WORLDS:
                handlePlayerWorldsMenuClick(player, slot);
                break;

            case WORLD_DETAILS:
                handleWorldDetailsMenuClick(player, slot);
                break;

            case CONFIRM_DELETE:
                handleConfirmDeleteMenuClick(player, slot);
                break;

            case PLAYER_MANAGEMENT:
                handlePlayerManagementMenuClick(player, slot);
                break;
        }
    }

    /**
     * Handler para eventos de fechamento de inventário
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Remove do mapa de inventários abertos
        openInventories.remove(playerUUID);
    }

    /**
     * Handler para cliques no menu principal
     */
    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 20: // Todos os mundos
                openAllWorldsMenu(player);
                break;

            case 22: // Pesquisar por jogador
                promptForPlayerSearch(player);
                break;

            case 40: // Recarregar plugin
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Recarregando plugin...");
                plugin.reload();
                player.sendMessage(ChatColor.GREEN + "Plugin recarregado com sucesso!");
                break;
        }
    }

    /**
     * Handler para cliques no menu de todos os mundos
     */
    private void handleAllWorldsMenuClick(Player player, int slot) {
        UUID playerUUID = player.getUniqueId();

        // Verifica se é um botão de navegação
        if (slot == PREV_PAGE_SLOT && event.getCurrentItem().getType() == Material.ARROW) {
            int currentPage = currentPages.getOrDefault(playerUUID, 0);
            openAllWorldsMenu(player, currentPage - 1);
            return;
        }

        if (slot == NEXT_PAGE_SLOT && event.getCurrentItem().getType() == Material.ARROW) {
            int currentPage = currentPages.getOrDefault(playerUUID, 0);
            openAllWorldsMenu(player, currentPage + 1);
            return;
        }

        // Verifica se é o botão de voltar
        if (slot == 48 && event.getCurrentItem().getType() == Material.BARRIER) {
            openMainMenu(player);
            return;
        }

        // Verifica se é o botão de filtro
        if (slot == 50 && event.getCurrentItem().getType() == Material.HOPPER) {
            promptForFilter(player);
            return;
        }

        // Verifica se é um mundo
        Map<Integer, CustomWorld> slotMap = playerWorldSlotMap.getOrDefault(playerUUID, new HashMap<>());
        if (slotMap.containsKey(slot)) {
            CustomWorld world = slotMap.get(slot);
            openWorldDetailsMenu(player, world);
        }
    }

    /**
     * Handler para cliques no menu de mundos do jogador
     */
    private void handlePlayerWorldsMenuClick(Player player, int slot) {
        UUID playerUUID = player.getUniqueId();

        // Verifica se é o botão de voltar
        if (slot == 45 && event.getCurrentItem().getType() == Material.BARRIER) {
            openMainMenu(player);
            return;
        }

        // Verifica se é um mundo
        Map<Integer, CustomWorld> slotMap = playerWorldSlotMap.getOrDefault(playerUUID, new HashMap<>());
        if (slotMap.containsKey(slot)) {
            CustomWorld world = slotMap.get(slot);
            openWorldDetailsMenu(player, world);
        }
    }

    /**
     * Handler para cliques no menu de detalhes do mundo
     */
    private void handleWorldDetailsMenuClick(Player player, int slot) {
        UUID playerUUID = player.getUniqueId();
        CustomWorld world = selectedWorlds.get(playerUUID);

        if (world == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 19: // Teleportar
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Teleportando para " + world.getName() + "...");
                plugin.getWorldManager().teleportPlayerToWorld(player, world);
                break;

            case 21: // Carregar/Descarregar
                if (world.isLoaded()) {
                    // Descarregar mundo
                    if (Bukkit.getWorld(world.getWorldName()) != null) {
                        for (Player p : Bukkit.getWorld(world.getWorldName()).getPlayers()) {
                            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                        }
                        boolean success = Bukkit.unloadWorld(world.getWorldName(), true);
                        if (success) {
                            player.sendMessage(ChatColor.GREEN + "Mundo descarregado com sucesso!");
                        } else {
                            player.sendMessage(ChatColor.RED + "Falha ao descarregar mundo!");
                        }
                    }
                } else {
                    // Carregar mundo
                    plugin.getWorldManager().loadWorld(world);
                    player.sendMessage(ChatColor.GREEN + "Mundo carregado com sucesso!");
                }

                // Reabrir menu
                openWorldDetailsMenu(player, world);
                break;

            case 23: // Gerenciar jogadores
                openPlayerManagementMenu(player, world);
                break;

            case 25: // Excluir mundo
                openConfirmDeleteMenu(player, world);
                break;

            case 49: // Voltar
                openAllWorldsMenu(player);
                break;
        }
    }

    /**
     * Handler para cliques no menu de confirmação de exclusão
     */
    private void handleConfirmDeleteMenuClick(Player player, int slot) {
        UUID playerUUID = player.getUniqueId();
        CustomWorld world = selectedWorlds.get(playerUUID);

        if (world == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 11: // Confirmar
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Excluindo mundo " + world.getName() + "...");

                // Excluir mundo
                plugin.getWorldManager().deleteWorld(world, player).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(ChatColor.GREEN + "Mundo excluído com sucesso!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Falha ao excluir mundo!");
                    }
                });
                break;

            case 15: // Cancelar
                openWorldDetailsMenu(player, world);
                break;
        }
    }

    /**
     * Handler para cliques no menu de gerenciamento de jogadores
     */
    private void handlePlayerManagementMenuClick(Player player, int slot) {
        UUID playerUUID = player.getUniqueId();
        CustomWorld world = selectedWorlds.get(playerUUID);

        if (world == null) {
            player.closeInventory();
            return;
        }

        // Verifica se é o botão de voltar
        if (slot == 45 && event.getCurrentItem().getType() == Material.ARROW) {
            openWorldDetailsMenu(player, world);
            return;
        }

        // Verifica se é o botão de adicionar jogador
        if (slot == 49 && event.getCurrentItem().getType() == Material.EMERALD) {
            promptForPlayerName(player, world);
            return;
        }

        // Verifica se é um jogador
        Map<Integer, OfflinePlayer> playerMap = playerPlayerSlotMap.getOrDefault(playerUUID, new HashMap<>());
        if (playerMap.containsKey(slot)) {
            OfflinePlayer targetPlayer = playerMap.get(slot);

            // Remover jogador da lista de confiados
            world.removeTrustedPlayer(targetPlayer.getUniqueId());
            plugin.getWorldManager().saveAllWorlds();

            player.sendMessage(ChatColor.GREEN + "Jogador " + targetPlayer.getName() +
                    " removido da lista de confiados do mundo " + world.getName());

            // Reabrir menu
            openPlayerManagementMenu(player, world);
        }
    }

    /**
     * Solicita um nome de jogador para pesquisa
     *
     * @param player Jogador admin
     */
    private void promptForPlayerSearch(Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Digite o nome do jogador que você deseja pesquisar:");
        player.sendMessage(ChatColor.GRAY + "Digite 'cancel' para cancelar");

        // Usa o mapa selectedPlayers para marcar que está aguardando input para busca
        selectedPlayers.put(player.getUniqueId(), UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }

    /**
     * Solicita um filtro para a lista de mundos
     *
     * @param player Jogador admin
     */
    private void promptForFilter(Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Digite o termo de pesquisa para filtrar mundos:");
        player.sendMessage(ChatColor.GRAY + "Digite 'clear' para limpar o filtro ou 'cancel' para cancelar");

        // Usa o mapa searchFilters para marcar que está aguardando input para filtro
        searchFilters.put(player.getUniqueId(), "");
    }

    /**
     * Processa entrada de chat para diversos prompts
     *
     * @param player Jogador
     * @param message Mensagem
     * @return true se a mensagem foi processada
     */
    public boolean handleChatInput(Player player, String message) {
        UUID playerUUID = player.getUniqueId();

        // Verifica se está esperando um nome de jogador para adicionar
        if (selectedWorlds.containsKey(playerUUID) && !openInventories.containsKey(playerUUID)) {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.YELLOW + "Operação cancelada.");
                openPlayerManagementMenu(player, selectedWorlds.get(playerUUID));
                return true;
            }

            // Procura o jogador
            @SuppressWarnings("deprecation")
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(message);

            if (!targetPlayer.hasPlayedBefore()) {
                player.sendMessage(ChatColor.RED + "Jogador não encontrado!");
                return true;
            }

            // Adiciona o jogador à lista de confiados
            CustomWorld world = selectedWorlds.get(playerUUID);
            world.addTrustedPlayer(targetPlayer.getUniqueId());
            plugin.getWorldManager().saveAllWorlds();

            player.sendMessage(ChatColor.GREEN + "Jogador " + targetPlayer.getName() +
                    " adicionado à lista de confiados do mundo " + world.getName());

            // Reabrir menu
            openPlayerManagementMenu(player, world);
            return true;
        }

        // Verifica se está esperando um nome de jogador para pesquisa
        if (selectedPlayers.containsKey(playerUUID) &&
                selectedPlayers.get(playerUUID).equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.YELLOW + "Operação cancelada.");
                openMainMenu(player);
                return true;
            }

            // Procura o jogador
            @SuppressWarnings("deprecation")
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(message);

            if (!targetPlayer.hasPlayedBefore()) {
                player.sendMessage(ChatColor.RED + "Jogador não encontrado!");
                return true;
            }

            // Abre o menu de mundos do jogador
            openPlayerWorldsMenu(player, targetPlayer);
            return true;
        }

        // Verifica se está esperando um filtro
        if (searchFilters.containsKey(playerUUID) && searchFilters.get(playerUUID).equals("")) {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.YELLOW + "Operação cancelada.");
                openAllWorldsMenu(player);
                return true;
            }

            if (message.equalsIgnoreCase("clear")) {
                searchFilters.remove(playerUUID);
                player.sendMessage(ChatColor.GREEN + "Filtro removido.");
                openAllWorldsMenu(player);
                return true;
            }

            // Define o filtro
            searchFilters.put(playerUUID, message);
            player.sendMessage(ChatColor.GREEN + "Filtro aplicado: " + message);
            openAllWorldsMenu(player);
            return true;
        }

        return false;
    }

    /**
     * Obtém uma lista de todos os proprietários de mundos únicos
     *
     * @return Lista de UUIDs de proprietários
     */
    private List<UUID> getUniqueWorldOwners() {
        return plugin.getWorldManager().getAllWorlds().stream()
                .map(CustomWorld::getOwnerUUID)
                .distinct()
                .collect(Collectors.toList());
    }
}