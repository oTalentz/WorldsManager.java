package com.worldsmanager.listeners;

import com.worldsmanager.WorldsManager;
import com.worldsmanager.models.CustomWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
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

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        String worldName = event.getWorld().getName();

        // Verifica se este é um mundo gerenciado pelo plugin
        CustomWorld customWorld = plugin.getWorldManager().getWorldByName(worldName);
        if (customWorld != null) {
            // Aplica configurações ao mundo quando ele é carregado
            plugin.getWorldManager().applyWorldSettings(customWorld);
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        String worldName = event.getWorld().getName();

        // Verifica se este é um mundo gerenciado pelo plugin
        CustomWorld customWorld = plugin.getWorldManager().getWorldByName(worldName);
        if (customWorld != null) {
            // Salva o mundo antes de descarregar
            plugin.getDatabaseManager().saveWorld(customWorld);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        String worldName = event.getWorld().getName();

        // Verifica se este é um mundo gerenciado pelo plugin
        CustomWorld customWorld = plugin.getWorldManager().getWorldByName(worldName);
        if (customWorld != null && !customWorld.getSettings().isWeatherEnabled()) {
            // Cancela mudanças de clima se desativado nas configurações
            if (event.toWeatherState()) {
                event.setCancelled(true);
            }
        }
    }
}