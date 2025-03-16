package com.worldsmanager.listeners;

import com.worldsmanager.WorldsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Listener para eventos relacionados a mundos
 */
public class WorldsListener implements Listener {

    private final WorldsManager plugin;

    public WorldsListener(WorldsManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Verifica teleportes pendentes quando o jogador entra
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getWorldManager().checkPendingTeleports(event.getPlayer());
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Limpa dados de jogadores que saem
        Player player = event.getPlayer();

        // Limpa dados da GUI se o jogador estava criando um mundo
        if (plugin.getWorldsCommand() != null &&
                plugin.getWorldsCommand().getWorldCreateGUI() != null) {
            plugin.getWorldsCommand().getWorldCreateGUI().clearPlayerData(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        // Log para debug
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Mundo carregado: " + event.getWorld().getName());
        }

        // Verifica se este é um mundo gerenciado pelo plugin
        String worldName = event.getWorld().getName();
        if (plugin.getWorldManager().worldExists(worldName)) {
            // Aplica configurações do mundo
            plugin.getWorldManager().applyWorldSettings(plugin.getWorldManager().getWorldByName(worldName));
            plugin.getLogger().info("Configurações aplicadas ao mundo carregado: " + worldName);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        // Log para debug
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Mundo descarregado: " + event.getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Log para debug
        if (plugin.getConfigManager().isDebugEnabled()) {
            if (event.getTo() != null && event.getTo().getWorld() != null) {
                plugin.getLogger().info("[DEBUG] Jogador " + event.getPlayer().getName() +
                        " teleportado para mundo: " + event.getTo().getWorld().getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        // Log para debug
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Jogador " + event.getPlayer().getName() +
                    " mudou do mundo " + event.getFrom().getName() +
                    " para " + event.getPlayer().getWorld().getName());
        }

        // Verifica se o mundo de destino é gerenciado pelo plugin
        String worldName = event.getPlayer().getWorld().getName();
        if (plugin.getWorldManager().worldExists(worldName)) {
            // Aplica modo de jogo correto
            plugin.getWorldManager().applyWorldSettings(plugin.getWorldManager().getWorldByName(worldName));
        }
    }
}