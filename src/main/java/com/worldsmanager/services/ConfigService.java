package com.worldsmanager.services;

import com.worldsmanager.models.WorldSettings;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.util.List;

/**
 * Interface de serviço para configuração do plugin
 */
public interface ConfigService {

    /**
     * Recarrega as configurações do arquivo
     */
    void reloadConfig();

    /**
     * Salva as configurações no arquivo
     */
    void saveConfig();

    /**
     * Obtém as configurações padrão para novos mundos
     *
     * @return Um objeto WorldSettings com as configurações padrão
     */
    WorldSettings getDefaultWorldSettings();

    /**
     * Verifica se o item fornecido é um item válido para ícone
     *
     * @param material Material a ser verificado
     * @return true se o material for válido para uso como ícone
     */
    boolean isValidIconMaterial(Material material);

    /**
     * Obtém uma lista de materiais disponíveis para ícones
     *
     * @return Lista de materiais disponíveis
     */
    List<Material> getAvailableIcons();

    /**
     * Verifica se o banco de dados está habilitado
     *
     * @return true se o banco de dados estiver habilitado
     */
    boolean isDatabaseEnabled();

    /**
     * Obtém o tipo do banco de dados
     *
     * @return Tipo do banco de dados
     */
    String getDatabaseType();

    /**
     * Obtém o host do banco de dados
     *
     * @return Host do banco de dados
     */
    String getDatabaseHost();

    /**
     * Obtém a porta do banco de dados
     *
     * @return Porta do banco de dados
     */
    int getDatabasePort();

    /**
     * Obtém o nome do banco de dados
     *
     * @return Nome do banco de dados
     */
    String getDatabaseName();

    /**
     * Obtém o usuário do banco de dados
     *
     * @return Usuário do banco de dados
     */
    String getDatabaseUsername();

    /**
     * Obtém a senha do banco de dados
     *
     * @return Senha do banco de dados
     */
    String getDatabasePassword();

    /**
     * Obtém o prefixo de tabelas do banco de dados
     *
     * @return Prefixo de tabelas
     */
    String getDatabaseTablePrefix();

    /**
     * Obtém o tipo de mundo para criação
     *
     * @return Tipo de mundo
     */
    WorldType getWorldType();

    /**
     * Obtém o ambiente de mundo para criação
     *
     * @return Ambiente de mundo
     */
    World.Environment getWorldEnvironment();

    /**
     * Verifica se estruturas devem ser geradas em novos mundos
     *
     * @return true se estruturas devem ser geradas
     */
    boolean isGenerateStructures();

    /**
     * Obtém o número máximo de mundos por jogador
     *
     * @return Número máximo de mundos
     */
    int getMaxWorldsPerPlayer();

    /**
     * Verifica se a economia está habilitada
     *
     * @return true se a economia estiver habilitada
     */
    boolean isEconomyEnabled();

    /**
     * Obtém o custo de criação de mundo
     *
     * @return Custo de criação de mundo
     */
    double getWorldCreationCost();

    /**
     * Obtém o custo de teleporte para mundo
     *
     * @return Custo de teleporte para mundo
     */
    double getWorldTeleportCost();

    /**
     * Verifica se o modo cross-server está habilitado
     *
     * @return true se o modo cross-server estiver habilitado
     */
    boolean isCrossServerMode();

    /**
     * Obtém o nome do servidor de mundos
     *
     * @return Nome do servidor de mundos
     */
    String getWorldsServerName();

    /**
     * Verifica se o teleporte automático está habilitado
     *
     * @return true se o teleporte automático estiver habilitado
     */
    boolean isAutoTeleport();

    /**
     * Obtém o atraso de teleporte
     *
     * @return Atraso de teleporte em ticks
     */
    int getTeleportDelay();

    /**
     * Obtém o idioma padrão
     *
     * @return Idioma padrão
     */
    String getDefaultLanguage();

    /**
     * Verifica se deve usar prefixo nas mensagens
     *
     * @return true se deve usar prefixo
     */
    boolean useMessagePrefix();

    /**
     * Obtém o prefixo de mensagens
     *
     * @return Prefixo de mensagens
     */
    String getMessagePrefix();

    /**
     * Obtém o número de linhas do menu principal
     *
     * @return Número de linhas
     */
    int getMainGUIRows();

    /**
     * Obtém o material do botão de criação
     *
     * @return Material do botão
     */
    Material getCreateButtonMaterial();

    /**
     * Obtém o slot do botão de criação
     *
     * @return Slot do botão
     */
    int getCreateButtonSlot();

    /**
     * Obtém o título do menu principal
     *
     * @return Título do menu
     */
    String getMainGUITitle();

    /**
     * Obtém o título do menu de criação
     *
     * @return Título do menu
     */
    String getCreateGUITitle();

    /**
     * Obtém o título do menu de configurações
     *
     * @return Título do menu
     */
    String getSettingsGUITitle();

    /**
     * Obtém o título do menu de jogadores
     *
     * @return Título do menu
     */
    String getPlayersGUITitle();

    /**
     * Obtém o título do menu de administração
     *
     * @return Título do menu
     */
    String getAdminGUITitle();

    /**
     * Obtém o título do menu de confirmação
     *
     * @return Título do menu
     */
    String getConfirmGUITitle();

    /**
     * Verifica se o modo de depuração está habilitado
     *
     * @return true se o modo de depuração estiver habilitado
     */
    boolean isDebugEnabled();

    /**
     * Obtém o nível de depuração
     *
     * @return Nível de depuração
     */
    int getDebugLevel();

    /**
     * Obtém um valor genérico da configuração
     *
     * @param path Caminho na configuração
     * @param defaultValue Valor padrão
     * @return Valor da configuração ou valor padrão
     */
    Object get(String path, Object defaultValue);

    /**
     * Define um valor na configuração
     *
     * @param path Caminho na configuração
     * @param value Valor a ser definido
     */
    void set(String path, Object value);

    /**
     * Obtém um token seguro para comunicações
     *
     * @return Token de segurança
     */
    String getSecurityToken();

    /**
     * Valida um token de segurança
     *
     * @param token Token a ser validado
     * @return true se o token for válido
     */
    boolean validateToken(String token);
}