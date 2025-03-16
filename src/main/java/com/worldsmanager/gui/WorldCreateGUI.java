package com.worldsmanager.gui;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.utils.ItemBuilder;
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
    public enum CreationStage {
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

        // DEBUG: Log na inicialização
        plugin.getLogger().info("[DEBUG] WorldCreateGUI inicializado");

        // Registra como listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Abre a GUI de criação para o jogador
     *
     * @param player Jogador
     */
    public void open(Player player) {
        // DEBUG: Log de abertura do menu
        plugin.getLogger().info("[DEBUG] Abrindo menu de criação para " + player.getName());

        // Cancela qualquer criação pendente
        resetPlayerState(player);

        // Solicita o nome do mundo via chat
        playerStages.put(player.getUniqueId(), CreationStage.AWAITING_NAME);

        // DEBUG: Log do estado atual
        plugin.getLogger().info("[DEBUG] Jogador " + player.getName() + " agora está no estágio: AWAITING_NAME");
        plugin.getLogger().info("[DEBUG] Mapa de estágios atualizado: " + playerStages.toString());

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
        // DEBUG: Log de abertura da seleção de ícone
        plugin.getLogger().info("[DEBUG] Abrindo seleção de ícone para " + player.getName() + " com mundo: " + worldName);

        UUID playerUUID = player.getUniqueId();
        pendingNames.put(playerUUID, worldName);
        playerStages.put(playerUUID, CreationStage.SELECTING_ICON);

        // DEBUG: Log dos mapas atualizados
        plugin.getLogger().info("[DEBUG] pendingNames: " + pendingNames.toString());
        plugin.getLogger().info("[DEBUG] playerStages: " + playerStages.toString());

        // Cria o inventário
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getCreateGUITitle());

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Adiciona ícones comuns - garanta que estão na lista de disponíveis no config
        addIcon(inv, 10, Material.GRASS_BLOCK, "Terrestre");
        addIcon(inv, 11, Material.STONE, "Caverna");
        addIcon(inv, 12, Material.SAND, "Deserto");
        addIcon(inv, 13, Material.SNOW_BLOCK, "Neve");
        addIcon(inv, 14, Material.JUNGLE_LOG, "Selva");
        addIcon(inv, 15, Material.WATER_BUCKET, "Oceano");
        addIcon(inv, 16, Material.LAVA_BUCKET, "Nether");

        addIcon(inv, 19, Material.NETHERRACK, "Nether");
        addIcon(inv, 20, Material.END_STONE, "End");  // Este estava causando problemas
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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        // DEBUG: Log de clique no inventário
        plugin.getLogger().info("[DEBUG] Clique no inventário por " + player.getName() + ", slot: " + event.getSlot());
        plugin.getLogger().info("[DEBUG] Estado atual: " + (playerStages.containsKey(playerUUID) ? playerStages.get(playerUUID) : "NONE"));

        // Ignora se não está no estágio de seleção de ícone
        if (!playerStages.containsKey(playerUUID) ||
                playerStages.get(playerUUID) != CreationStage.SELECTING_ICON) {
            plugin.getLogger().info("[DEBUG] Ignorando clique - jogador não está no estágio SELECTING_ICON");
            return;
        }

        // Verifica se é o inventário correto - mais flexível
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getCreateGUITitle());

        if (!event.getView().getTitle().equals(title) &&
                !event.getView().getTitle().contains("Criar Novo Mundo")) {
            plugin.getLogger().info("[DEBUG] Ignorando clique - título do inventário não corresponde");
            return;
        }

        event.setCancelled(true);

        // Se clicou fora ou é slot inválido
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            plugin.getLogger().info("[DEBUG] Clique em slot vazio ou fora");
            return;
        }

        // Se clicou no botão de cancelar
        if (event.getCurrentItem().getType() == Material.BARRIER) {
            plugin.getLogger().info("[DEBUG] Clique no botão cancelar");
            player.closeInventory();
            resetPlayerState(player);
            player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-creation-cancelled"));
            return;
        }

        // Seleciona o ícone
        Material selectedIcon = event.getCurrentItem().getType();
        plugin.getLogger().info("[DEBUG] Ícone selecionado: " + selectedIcon);

        // Removida a verificação de ícone válido aqui, pois estava duplicada e causando problemas
        // A verificação já é feita no ConfigManager

        // Armazena o ícone selecionado
        pendingIcons.put(playerUUID, selectedIcon);
        plugin.getLogger().info("[DEBUG] Ícone armazenado para " + player.getName() + ": " + selectedIcon);

        // Fecha o inventário
        player.closeInventory();

        // Cria o mundo
        createWorld(player);
    }

    /**
     * Manipula eventos de fechamento do inventário
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // DEBUG: Log de fechamento do inventário
        plugin.getLogger().info("[DEBUG] Inventário fechado por " + player.getName());
        plugin.getLogger().info("[DEBUG] Estado atual: " + (playerStages.containsKey(playerUUID) ? playerStages.get(playerUUID) : "NONE"));

        // Check if player is in SELECTING_ICON state
        if (playerStages.containsKey(playerUUID) &&
                playerStages.get(playerUUID) == CreationStage.SELECTING_ICON) {

            // Get the inventory title
            String title = event.getView().getTitle();
            String expectedTitle = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getCreateGUITitle());

            // Check if this is the icon selection inventory
            if (title.equals(expectedTitle) || title.contains("Criar Novo Mundo")) {
                // If player has an icon selected, the createWorld method will handle state management
                // If not, cancel the world creation
                if (!pendingIcons.containsKey(playerUUID)) {
                    plugin.getLogger().info("[DEBUG] Fechou sem selecionar ícone - cancelando");
                    resetPlayerState(player);

                    // Schedule message for next tick to ensure it's shown after closing
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-creation-cancelled"));
                    });
                }
            }
        }
    }

    /**
     * Manipula eventos de chat para capturar o nome do mundo
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // DEBUG: Sempre logar mensagens de chat para debug
        plugin.getLogger().info("[CHAT-DEBUG] Chat recebido de " + player.getName() + ": '" + event.getMessage() + "'");
        plugin.getLogger().info("[CHAT-DEBUG] Estado atual: " + (playerStages.containsKey(playerUUID) ? playerStages.get(playerUUID) : "NONE"));
        plugin.getLogger().info("[CHAT-DEBUG] Mapa de estágios: " + playerStages.toString());

        // IMPORTANTE: Verifique primeiro se o evento já foi cancelado por outro plugin
        if (event.isCancelled()) {
            plugin.getLogger().info("[CHAT-DEBUG] Evento já cancelado por outro plugin - ignorando");
            return;
        }

        // Ignora se não está aguardando nome
        if (!playerStages.containsKey(playerUUID) ||
                playerStages.get(playerUUID) != CreationStage.AWAITING_NAME) {
            plugin.getLogger().info("[CHAT-DEBUG] Ignorando chat - jogador não está no estágio AWAITING_NAME");
            return;
        }

        // IMPORTANTE! Cancela o evento para evitar que a mensagem apareça no chat
        // Cancela SOMENTE se estamos processando este chat para criação de mundo
        event.setCancelled(true);
        plugin.getLogger().info("[CHAT-DEBUG] Evento de chat cancelado - processando nome do mundo");

        String worldName = event.getMessage();

        // Verifica se o jogador cancelou
        if (worldName.equalsIgnoreCase("cancel")) {
            plugin.getLogger().info("[CHAT-DEBUG] Jogador cancelou a criação via chat");
            resetPlayerState(player);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + plugin.getLanguageManager().getMessage("world-creation-cancelled"));
            });
            return;
        }

        // IMPORTANTE: Salve o nome imediatamente para evitar problemas com eventos duplicados
        // Isso evita que o mesmo chat seja processado duas vezes
        final String savedWorldName = worldName;
        playerStages.put(playerUUID, CreationStage.NONE); // Temporariamente limpe o estado

        // Verifica se o nome é válido
        if (savedWorldName.length() < 3 || savedWorldName.length() > 16) {
            plugin.getLogger().info("[CHAT-DEBUG] Nome de mundo inválido (comprimento): " + savedWorldName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("invalid-world-name-length"));
                // Volte para o estado de espera de nome depois da mensagem de erro
                playerStages.put(playerUUID, CreationStage.AWAITING_NAME);
            });
            return;
        }

        // Verifica se contém apenas caracteres válidos
        if (!savedWorldName.matches("[a-zA-Z0-9_]+")) {
            plugin.getLogger().info("[CHAT-DEBUG] Nome de mundo inválido (caracteres): " + savedWorldName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("invalid-world-name-chars"));
                // Volte para o estado de espera de nome depois da mensagem de erro
                playerStages.put(playerUUID, CreationStage.AWAITING_NAME);
            });
            return;
        }

        // Verifica se o nome já existe
        boolean nameExists = plugin.getWorldManager().getAllWorlds().stream()
                .anyMatch(world -> world.getName().equalsIgnoreCase(savedWorldName));

        if (nameExists) {
            plugin.getLogger().info("[CHAT-DEBUG] Nome de mundo já existe: " + savedWorldName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-name-exists"));
                // Volte para o estado de espera de nome depois da mensagem de erro
                playerStages.put(playerUUID, CreationStage.AWAITING_NAME);
            });
            return;
        }

        plugin.getLogger().info("[CHAT-DEBUG] Nome de mundo válido, prosseguindo para seleção de ícone: " + savedWorldName);

        // Use uma flag booleana para evitar aberturas duplicadas
        final boolean[] opened = {false};

        // Abre a GUI de seleção de ícone na thread principal do Bukkit
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!opened[0]) {
                opened[0] = true;
                openIconSelection(player, savedWorldName);
            }
        });
    }

    /**
     * Cria o mundo após a seleção de nome e ícone
     *
     * @param player Jogador
     */
    private void createWorld(Player player) {
        UUID playerUUID = player.getUniqueId();

        // DEBUG: Log do início da criação de mundo
        plugin.getLogger().info("[DEBUG] Iniciando criação de mundo para " + player.getName());
        plugin.getLogger().info("[DEBUG] pendingNames: " + pendingNames.toString());
        plugin.getLogger().info("[DEBUG] pendingIcons: " + pendingIcons.toString());
        plugin.getLogger().info("[DEBUG] playerStages: " + playerStages.toString());

        // Verifica se tem nome e ícone
        if (!pendingNames.containsKey(playerUUID) || !pendingIcons.containsKey(playerUUID)) {
            plugin.getLogger().info("[DEBUG] ERRO: Nome ou ícone pendente não encontrado para " + player.getName());
            player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-creation-error"));
            resetPlayerState(player);
            return;
        }

        String worldName = pendingNames.get(playerUUID);
        Material selectedIcon = pendingIcons.get(playerUUID);

        plugin.getLogger().info("[DEBUG] Criando mundo: " + worldName + " com ícone: " + selectedIcon);

        // Limpa os dados pendentes APÓS extrair os valores necessários
        String savedWorldName = worldName; // Salva o nome para usar no log
        resetPlayerState(player);

        // DEBUG: Log após limpar os dados pendentes
        plugin.getLogger().info("[DEBUG] Dados pendentes limpos. playerStages: " + playerStages.toString());

        // Mensagem de criação
        player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("creating-world")
                .replace("%name%", worldName));

        // Cria o mundo
        plugin.getWorldManager().createWorld(worldName, player.getUniqueId(), selectedIcon, player)
                .thenAccept(customWorld -> {
                    if (customWorld != null) {
                        plugin.getLogger().info("[DEBUG] Mundo criado com sucesso: " + savedWorldName);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-created-success"));
                        });
                    } else {
                        plugin.getLogger().info("[DEBUG] Falha ao criar mundo: " + savedWorldName);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-creation-failed"));
                        });
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("[DEBUG] Exceção ao criar mundo: " + e.getMessage());
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Erro ao criar mundo: " + e.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Obtém o estágio atual de criação de um jogador
     * @param player Jogador a verificar
     * @return O estágio atual ou NONE se não estiver criando um mundo
     */
    public CreationStage getPlayerStage(Player player) {
        return playerStages.getOrDefault(player.getUniqueId(), CreationStage.NONE);
    }

    /**
     * Limpa os dados de criação de um jogador
     * @param player Jogador
     */
    public void clearPlayerData(Player player) {
        resetPlayerState(player);
    }

    /**
     * Reseta o estado do jogador
     * @param player Jogador
     */
    public void resetPlayerState(Player player) {
        UUID playerUUID = player.getUniqueId();
        plugin.getLogger().info("[DEBUG] Resetando estado para o jogador " + player.getName());
        playerStages.put(playerUUID, CreationStage.NONE);
        pendingNames.remove(playerUUID);
        pendingIcons.remove(playerUUID);
    }

    /**
     * Cria mundo diretamente com nome fornecido pelo jogador e ícone padrão
     * @param player Jogador
     * @param worldName Nome do mundo
     */
    public void createWorldDirectly(Player player, String worldName) {
        UUID playerUUID = player.getUniqueId();

        plugin.getLogger().info("[DEBUG] Criando mundo diretamente com nome: " + worldName);

        // Define o estágio como NONE para evitar conflitos com outros eventos
        resetPlayerState(player);

        // Mensagem de criação
        player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("creating-world")
                .replace("%name%", worldName));

        // Usa o ícone padrão (GRASS_BLOCK)
        Material defaultIcon = Material.GRASS_BLOCK;

        // Cria o mundo
        plugin.getWorldManager().createWorld(worldName, playerUUID, defaultIcon, player)
                .thenAccept(customWorld -> {
                    if (customWorld != null) {
                        plugin.getLogger().info("[DEBUG] Mundo criado diretamente com sucesso: " + worldName);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.GREEN + plugin.getLanguageManager().getMessage("world-created-success"));
                        });
                    } else {
                        plugin.getLogger().info("[DEBUG] Falha ao criar mundo diretamente: " + worldName);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.RED + plugin.getLanguageManager().getMessage("world-creation-failed"));
                        });
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("[DEBUG] Exceção ao criar mundo diretamente: " + e.getMessage());
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Erro ao criar mundo: " + e.getMessage());
                    });
                    return null;
                });
    }
}