package com.worldsmanager.services;

import com.worldsmanager.models.CustomWorld;
import com.worldsmanager.models.WorldSettings;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Interface de serviço para mensagens entre servidores
 */
public interface MessageService {

    /**
     * Envia uma mensagem para criar um mundo no servidor de mundos
     *
     * @param world CustomWorld a ser criado
     * @param requester Jogador que solicitou a criação
     * @return true se a mensagem foi enviada com sucesso
     */
    boolean sendCreateWorldMessage(CustomWorld world, Player requester);

    /**
     * Envia uma mensagem para teleportar um jogador para um mundo específico no servidor de mundos
     *
     * @param player Jogador a ser teleportado
     * @param worldName Nome do mundo para teleportar
     * @return true se a mensagem foi enviada com sucesso
     */
    boolean sendTeleportToWorldMessage(Player player, String worldName);

    /**
     * Envia uma mensagem para excluir um mundo no servidor de mundos
     *
     * @param worldName Nome do mundo a ser excluído
     * @param player Jogador que solicitou a exclusão
     * @return true se a mensagem foi enviada com sucesso
     */
    boolean sendDeleteWorldMessage(String worldName, Player player);

    /**
     * Envia uma mensagem para atualizar as configurações de um mundo no servidor de mundos
     *
     * @param worldName Nome do mundo
     * @param settings Configurações a serem aplicadas
     * @param player Jogador que solicitou a atualização
     * @return true se a mensagem foi enviada com sucesso
     */
    boolean sendUpdateWorldSettingsMessage(String worldName, WorldSettings settings, Player player);

    /**
     * Verifica se os canais necessários estão registrados
     *
     * @return true se tudo estiver corretamente registrado
     */
    boolean checkChannelsRegistered();

    /**
     * Envia um jogador para outro servidor no BungeeCord
     *
     * @param player Jogador a ser enviado
     * @param server Nome do servidor de destino
     * @return true se a mensagem foi enviada com sucesso
     */
    boolean sendPlayerToServer(Player player, String server);

    /**
     * Remove um mundo dos mundos pendentes quando a criação for concluída
     *
     * @param playerUUID UUID do jogador
     */
    void removePendingWorldCreation(UUID playerUUID);

    /**
     * Verifica se existe uma criação de mundo pendente para o jogador
     *
     * @param playerUUID UUID do jogador
     * @return true se existir uma criação pendente
     */
    boolean hasPendingWorldCreation(UUID playerUUID);

    /**
     * Obtém o nome do mundo pendente para o jogador
     *
     * @param playerUUID UUID do jogador
     * @return nome do mundo pendente ou null se não houver
     */
    String getPendingWorldName(UUID playerUUID);
}