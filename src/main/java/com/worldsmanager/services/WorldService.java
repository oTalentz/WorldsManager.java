package com.worldsmanager.services;

import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface de serviço para operações relacionadas a mundos
 */
public interface WorldService {

    /**
     * Cria um novo mundo
     *
     * @param name Nome de exibição do mundo
     * @param ownerUUID UUID do proprietário
     * @param icon Ícone do mundo
     * @param requester Jogador que solicitou a criação
     * @return CompletableFuture com o CustomWorld criado
     */
    CompletableFuture<CustomWorld> createWorld(String name, UUID ownerUUID, Material icon, Player requester);

    /**
     * Exclui um mundo
     *
     * @param customWorld Mundo personalizado a ser excluído
     * @param requester Jogador que solicitou a exclusão (pode ser null)
     * @return CompletableFuture com resultado booleano
     */
    CompletableFuture<Boolean> deleteWorld(CustomWorld customWorld, Player requester);

    /**
     * Carrega um mundo se ainda não estiver carregado
     *
     * @param customWorld Mundo personalizado a ser carregado
     * @return O mundo carregado ou null se falhar
     */
    World loadWorld(CustomWorld customWorld);

    /**
     * Teleporta um jogador para um mundo
     *
     * @param player Jogador a ser teleportado
     * @param customWorld Mundo de destino
     * @return true se o teleporte foi bem-sucedido
     */
    boolean teleportPlayerToWorld(Player player, CustomWorld customWorld);

    /**
     * Aplica configurações de mundo a um mundo carregado
     *
     * @param customWorld Mundo personalizado
     */
    void applyWorldSettings(CustomWorld customWorld);

    /**
     * Atualiza as configurações de um mundo
     *
     * @param customWorld Mundo a ser atualizado
     * @param settings Novas configurações
     * @param requester Jogador que solicitou a atualização
     */
    void updateWorldSettings(CustomWorld customWorld, WorldSettings settings, Player requester);

    /**
     * Obtém todos os mundos de um jogador
     *
     * @param playerUUID UUID do jogador
     * @return Lista de mundos do jogador
     */
    List<CustomWorld> getPlayerWorlds(UUID playerUUID);

    /**
     * Obtém todos os mundos carregados
     *
     * @return Coleção de mundos carregados
     */
    Collection<CustomWorld> getAllWorlds();

    /**
     * Obtém um mundo pelo nome
     *
     * @param worldName Nome do mundo
     * @return O mundo correspondente ou null se não encontrado
     */
    CustomWorld getWorldByName(String worldName);

    /**
     * Salva todos os mundos no banco de dados
     */
    void saveAllWorlds();

    /**
     * Verifica se existe um mundo com o nome especificado
     *
     * @param worldName Nome do mundo
     * @return true se o mundo existir
     */
    boolean worldExists(String worldName);

    /**
     * Registra um mundo no Multiverse
     *
     * @param worldName Nome do mundo
     * @return true se o registro foi bem-sucedido
     */
    boolean registerWithMultiverse(String worldName);

    /**
     * Verifica se um mundo está registrado no Multiverse
     *
     * @param worldName Nome do mundo
     * @return true se o mundo estiver registrado
     */
    boolean isRegisteredInMultiverse(String worldName);

    /**
     * Adiciona um teleporte pendente para um jogador
     *
     * @param playerUUID UUID do jogador
     * @param worldName Nome do mundo
     */
    void addPendingTeleport(UUID playerUUID, String worldName);

    /**
     * Verifica e processa teleportes pendentes para um jogador
     *
     * @param player Jogador
     */
    void checkPendingTeleports(Player player);
}