package com.worldsmanager.models;

import org.bukkit.GameMode;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

/**
 * Configurações de um mundo personalizado
 */
@SerializableAs("WorldSettings")
public class WorldSettings implements ConfigurationSerializable {

    private boolean pvpEnabled;
    private boolean mobSpawning;
    private boolean timeCycle;
    private long fixedTime;
    private boolean weatherEnabled;
    private boolean physicsEnabled;
    private boolean redstoneEnabled;
    private boolean fluidFlow;
    private int tickSpeed;
    private boolean keepInventory;
    private boolean announceDeaths;
    private boolean fallDamage;
    private boolean hungerDepletion;
    private boolean fireSpread;
    private boolean leafDecay;
    private boolean blockUpdates;
    private GameMode gameMode;

    /**
     * Construtor padrão com valores padrão
     */
    public WorldSettings() {
        this.pvpEnabled = false;
        this.mobSpawning = true;
        this.timeCycle = true;
        this.fixedTime = 6000; // Meio-dia
        this.weatherEnabled = true;
        this.physicsEnabled = true;
        this.redstoneEnabled = true;
        this.fluidFlow = true;
        this.tickSpeed = 3;
        this.keepInventory = false;
        this.announceDeaths = true;
        this.fallDamage = true;
        this.hungerDepletion = true;
        this.fireSpread = true;
        this.leafDecay = true;
        this.blockUpdates = true;
        this.gameMode = GameMode.SURVIVAL;
    }

    /**
     * Construtor de cópia
     *
     * @param other Configurações a serem copiadas
     */
    public WorldSettings(WorldSettings other) {
        this.pvpEnabled = other.pvpEnabled;
        this.mobSpawning = other.mobSpawning;
        this.timeCycle = other.timeCycle;
        this.fixedTime = other.fixedTime;
        this.weatherEnabled = other.weatherEnabled;
        this.physicsEnabled = other.physicsEnabled;
        this.redstoneEnabled = other.redstoneEnabled;
        this.fluidFlow = other.fluidFlow;
        this.tickSpeed = other.tickSpeed;
        this.keepInventory = other.keepInventory;
        this.announceDeaths = other.announceDeaths;
        this.fallDamage = other.fallDamage;
        this.hungerDepletion = other.hungerDepletion;
        this.fireSpread = other.fireSpread;
        this.leafDecay = other.leafDecay;
        this.blockUpdates = other.blockUpdates;
        this.gameMode = other.gameMode;
    }

    /**
     * Construtor a partir de um mapa
     *
     * @param map Mapa de configurações
     */
    public WorldSettings(Map<String, Object> map) {
        this.pvpEnabled = (boolean) map.getOrDefault("pvpEnabled", false);
        this.mobSpawning = (boolean) map.getOrDefault("mobSpawning", true);
        this.timeCycle = (boolean) map.getOrDefault("timeCycle", true);
        this.fixedTime = ((Number) map.getOrDefault("fixedTime", 6000)).longValue();
        this.weatherEnabled = (boolean) map.getOrDefault("weatherEnabled", true);
        this.physicsEnabled = (boolean) map.getOrDefault("physicsEnabled", true);
        this.redstoneEnabled = (boolean) map.getOrDefault("redstoneEnabled", true);
        this.fluidFlow = (boolean) map.getOrDefault("fluidFlow", true);
        this.tickSpeed = ((Number) map.getOrDefault("tickSpeed", 3)).intValue();
        this.keepInventory = (boolean) map.getOrDefault("keepInventory", false);
        this.announceDeaths = (boolean) map.getOrDefault("announceDeaths", true);
        this.fallDamage = (boolean) map.getOrDefault("fallDamage", true);
        this.hungerDepletion = (boolean) map.getOrDefault("hungerDepletion", true);
        this.fireSpread = (boolean) map.getOrDefault("fireSpread", true);
        this.leafDecay = (boolean) map.getOrDefault("leafDecay", true);
        this.blockUpdates = (boolean) map.getOrDefault("blockUpdates", true);

        String gameModeStr = (String) map.getOrDefault("gameMode", "SURVIVAL");
        try {
            this.gameMode = GameMode.valueOf(gameModeStr);
        } catch (IllegalArgumentException e) {
            this.gameMode = GameMode.SURVIVAL;
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("pvpEnabled", pvpEnabled);
        map.put("mobSpawning", mobSpawning);
        map.put("timeCycle", timeCycle);
        map.put("fixedTime", fixedTime);
        map.put("weatherEnabled", weatherEnabled);
        map.put("physicsEnabled", physicsEnabled);
        map.put("redstoneEnabled", redstoneEnabled);
        map.put("fluidFlow", fluidFlow);
        map.put("tickSpeed", tickSpeed);
        map.put("keepInventory", keepInventory);
        map.put("announceDeaths", announceDeaths);
        map.put("fallDamage", fallDamage);
        map.put("hungerDepletion", hungerDepletion);
        map.put("fireSpread", fireSpread);
        map.put("leafDecay", leafDecay);
        map.put("blockUpdates", blockUpdates);
        map.put("gameMode", gameMode.name());
        return map;
    }

    // Getters e Setters

    /**
     * Verifica se o PVP está habilitado
     *
     * @return true se o PVP estiver habilitado
     */
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    /**
     * Define se o PVP está habilitado
     *
     * @param pvpEnabled true para habilitar o PVP
     */
    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    /**
     * Verifica se a geração de mobs está habilitada
     *
     * @return true se a geração de mobs estiver habilitada
     */
    public boolean isMobSpawning() {
        return mobSpawning;
    }

    /**
     * Define se a geração de mobs está habilitada
     *
     * @param mobSpawning true para habilitar a geração de mobs
     */
    public void setMobSpawning(boolean mobSpawning) {
        this.mobSpawning = mobSpawning;
    }

    /**
     * Verifica se o ciclo de tempo está habilitado
     *
     * @return true se o ciclo de tempo estiver habilitado
     */
    public boolean isTimeCycle() {
        return timeCycle;
    }

    /**
     * Define se o ciclo de tempo está habilitado
     *
     * @param timeCycle true para habilitar o ciclo de tempo
     */
    public void setTimeCycle(boolean timeCycle) {
        this.timeCycle = timeCycle;
    }

    /**
     * Obtém o tempo fixo se o ciclo de tempo estiver desativado
     *
     * @return O tempo fixo (0-24000)
     */
    public long getFixedTime() {
        return fixedTime;
    }

    /**
     * Define o tempo fixo se o ciclo de tempo estiver desativado
     *
     * @param fixedTime O tempo fixo (0-24000)
     */
    public void setFixedTime(long fixedTime) {
        this.fixedTime = fixedTime;
    }

    /**
     * Verifica se o clima está habilitado
     *
     * @return true se o clima estiver habilitado
     */
    public boolean isWeatherEnabled() {
        return weatherEnabled;
    }

    /**
     * Define se o clima está habilitado
     *
     * @param weatherEnabled true para habilitar o clima
     */
    public void setWeatherEnabled(boolean weatherEnabled) {
        this.weatherEnabled = weatherEnabled;
    }

    /**
     * Verifica se a física de blocos está habilitada
     *
     * @return true se a física de blocos estiver habilitada
     */
    public boolean isPhysicsEnabled() {
        return physicsEnabled;
    }

    /**
     * Define se a física de blocos está habilitada
     *
     * @param physicsEnabled true para habilitar a física de blocos
     */
    public void setPhysicsEnabled(boolean physicsEnabled) {
        this.physicsEnabled = physicsEnabled;
    }

    /**
     * Verifica se o redstone está habilitado
     *
     * @return true se o redstone estiver habilitado
     */
    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    /**
     * Define se o redstone está habilitado
     *
     * @param redstoneEnabled true para habilitar o redstone
     */
    public void setRedstoneEnabled(boolean redstoneEnabled) {
        this.redstoneEnabled = redstoneEnabled;
    }

    /**
     * Verifica se o fluxo de fluidos está habilitado
     *
     * @return true se o fluxo de fluidos estiver habilitado
     */
    public boolean isFluidFlow() {
        return fluidFlow;
    }

    /**
     * Define se o fluxo de fluidos está habilitado
     *
     * @param fluidFlow true para habilitar o fluxo de fluidos
     */
    public void setFluidFlow(boolean fluidFlow) {
        this.fluidFlow = fluidFlow;
    }

    /**
     * Obtém a velocidade de tick do mundo
     *
     * @return A velocidade de tick (0-100)
     */
    public int getTickSpeed() {
        return tickSpeed;
    }

    /**
     * Define a velocidade de tick do mundo
     *
     * @param tickSpeed A velocidade de tick (0-100)
     */
    public void setTickSpeed(int tickSpeed) {
        this.tickSpeed = tickSpeed;
    }

    /**
     * Verifica se o keepInventory está habilitado
     *
     * @return true se o keepInventory estiver habilitado
     */
    public boolean isKeepInventory() {
        return keepInventory;
    }

    /**
     * Define se o keepInventory está habilitado
     *
     * @param keepInventory true para habilitar o keepInventory
     */
    public void setKeepInventory(boolean keepInventory) {
        this.keepInventory = keepInventory;
    }

    /**
     * Verifica se o anúncio de mortes está habilitado
     *
     * @return true se o anúncio de mortes estiver habilitado
     */
    public boolean isAnnounceDeaths() {
        return announceDeaths;
    }

    /**
     * Define se o anúncio de mortes está habilitado
     *
     * @param announceDeaths true para habilitar o anúncio de mortes
     */
    public void setAnnounceDeaths(boolean announceDeaths) {
        this.announceDeaths = announceDeaths;
    }

    /**
     * Verifica se o dano de queda está habilitado
     *
     * @return true se o dano de queda estiver habilitado
     */
    public boolean isFallDamage() {
        return fallDamage;
    }

    /**
     * Define se o dano de queda está habilitado
     *
     * @param fallDamage true para habilitar o dano de queda
     */
    public void setFallDamage(boolean fallDamage) {
        this.fallDamage = fallDamage;
    }

    /**
     * Verifica se a depleção de fome está habilitada
     *
     * @return true se a depleção de fome estiver habilitada
     */
    public boolean isHungerDepletion() {
        return hungerDepletion;
    }

    /**
     * Define se a depleção de fome está habilitada
     *
     * @param hungerDepletion true para habilitar a depleção de fome
     */
    public void setHungerDepletion(boolean hungerDepletion) {
        this.hungerDepletion = hungerDepletion;
    }

    /**
     * Verifica se a propagação de fogo está habilitada
     *
     * @return true se a propagação de fogo estiver habilitada
     */
    public boolean isFireSpread() {
        return fireSpread;
    }

    /**
     * Define se a propagação de fogo está habilitada
     *
     * @param fireSpread true para habilitar a propagação de fogo
     */
    public void setFireSpread(boolean fireSpread) {
        this.fireSpread = fireSpread;
    }

    /**
     * Verifica se a decomposição de folhas está habilitada
     *
     * @return true se a decomposição de folhas estiver habilitada
     */
    public boolean isLeafDecay() {
        return leafDecay;
    }

    /**
     * Define se a decomposição de folhas está habilitada
     *
     * @param leafDecay true para habilitar a decomposição de folhas
     */
    public void setLeafDecay(boolean leafDecay) {
        this.leafDecay = leafDecay;
    }

    /**
     * Verifica se as atualizações de blocos estão habilitadas
     *
     * @return true se as atualizações de blocos estiverem habilitadas
     */
    public boolean isBlockUpdates() {
        return blockUpdates;
    }

    /**
     * Define se as atualizações de blocos estão habilitadas
     *
     * @param blockUpdates true para habilitar as atualizações de blocos
     */
    public void setBlockUpdates(boolean blockUpdates) {
        this.blockUpdates = blockUpdates;
    }

    /**
     * Obtém o modo de jogo do mundo
     *
     * @return O modo de jogo
     */
    public GameMode getGameMode() {
        return gameMode;
    }

    /**
     * Define o modo de jogo do mundo
     *
     * @param gameMode O modo de jogo
     */
    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    /**
     * Obtém o tempo como uma string formatada
     *
     * @return String formatada do tempo
     */
    public String getTimeAsString() {
        if (timeCycle) {
            return "Ciclo Natural";
        }

        long time = fixedTime;
        if (time >= 0 && time < 6000) {
            return "Amanhecer";
        } else if (time >= 6000 && time < 12000) {
            return "Dia";
        } else if (time >= 12000 && time < 13800) {
            return "Entardecer";
        } else if (time >= 13800 && time < 22200) {
            return "Noite";
        } else if (time >= 22200 && time <= 24000) {
            return "Madrugada";
        } else {
            return "Personalizado";
        }
    }
}