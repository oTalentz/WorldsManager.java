package com.worldsmanager.services;

import com.worldsmanager.models.CustomWorld;

import java.sql.Connection;
import java.util.List;

/**
 * Interface de serviço para operações de banco de dados
 */
public interface DatabaseService {

    /**
     * Conecta ao banco de dados
     */
    void connect();

    /**
     * Verifica se a conexão está ativa
     *
     * @return true se a conexão estiver ativa
     */
    boolean isConnected();

    /**
     * Desconecta do banco de dados
     */
    void disconnect();

    /**
     * Salva um mundo no banco de dados
     *
     * @param world Mundo para salvar
     */
    void saveWorld(CustomWorld world);

    /**
     * Deleta um mundo do banco de dados
     *
     * @param world Mundo para deletar
     */
    void deleteWorld(CustomWorld world);

    /**
     * Obtém todos os mundos do banco de dados
     *
     * @return Lista de mundos
     */
    List<CustomWorld> getAllWorlds();

    /**
     * Obtém uma conexão do pool
     *
     * @return Conexão com o banco de dados
     */
    Connection getConnection();

    /**
     * Executa um comando SQL
     *
     * @param sql Comando SQL
     * @param params Parâmetros
     * @return true se a execução foi bem-sucedida
     */
    boolean executeUpdate(String sql, Object... params);

    /**
     * Executa uma consulta SQL
     *
     * @param sql Consulta SQL
     * @param processor Processador de resultado
     * @param params Parâmetros
     * @param <T> Tipo de retorno
     * @return Resultado processado
     */
    <T> T executeQuery(String sql, ResultProcessor<T> processor, Object... params);

    /**
     * Interface para processamento de resultados de consulta
     *
     * @param <T> Tipo de retorno
     */
    interface ResultProcessor<T> {
        T process(java.sql.ResultSet resultSet) throws java.sql.SQLException;
    }
}