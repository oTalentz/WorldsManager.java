package com.worldsmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe para tratamento centralizado de erros
 */
public class ErrorHandler {

    private final Plugin plugin;
    private final Logger logger;
    private final File errorLogFile;
    private final boolean debugMode;

    // Cache de erros recentes para evitar spam
    private final Map<String, ErrorEntry> recentErrors = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    // Níveis de erro
    public enum ErrorLevel {
        INFO, WARNING, ERROR, CRITICAL
    }

    // Classe para armazenar informações de erro
    private static class ErrorEntry {
        private final String message;
        private final Throwable exception;
        private final long timestamp;
        private int count;

        public ErrorEntry(String message, Throwable exception) {
            this.message = message;
            this.exception = exception;
            this.timestamp = System.currentTimeMillis();
            this.count = 1;
        }

        public void increment() {
            count++;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 300000; // 5 minutos
        }
    }

    /**
     * Construtor
     *
     * @param plugin Plugin
     * @param debugMode Ativar modo de depuração
     */
    public ErrorHandler(Plugin plugin, boolean debugMode) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.debugMode = debugMode;
        this.errorLogFile = new File(plugin.getDataFolder(), "errors.log");

        // Cria pasta de logs se necessário
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Agenda limpeza de erros antigos
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanExpiredErrors, 6000L, 6000L); // 5 minutos
    }

    /**
     * Manipula um erro genérico
     *
     * @param message Mensagem de erro
     * @param exception Exceção (pode ser null)
     * @param level Nível de erro
     */
    public void handleError(String message, Throwable exception, ErrorLevel level) {
        // Gera um identificador único para o erro
        String errorId = generateErrorId(message, exception);

        // Verifica se é um erro recente (para evitar spam no log)
        ErrorEntry entry = recentErrors.get(errorId);
        if (entry != null) {
            entry.increment();

            // Só loga a cada 10 ocorrências
            if (entry.count % 10 != 0) {
                return;
            }

            message += " (repetido " + entry.count + " vezes)";
        } else {
            recentErrors.put(errorId, new ErrorEntry(message, exception));
        }

        // Loga o erro no console
        switch (level) {
            case INFO:
                logger.info(message);
                break;
            case WARNING:
                logger.warning(message);
                break;
            case ERROR:
                logger.log(Level.SEVERE, message, exception);
                break;
            case CRITICAL:
                logger.log(Level.SEVERE, "CRITICAL ERROR: " + message, exception);
                break;
        }

        // Escreve no arquivo de log de erros
        logToFile(message, exception, level);
    }

    /**
     * Manipula um erro e notifica um jogador
     *
     * @param player Jogador a ser notificado
     * @param message Mensagem de erro
     * @param exception Exceção (pode ser null)
     * @param level Nível de erro
     */
    public void handlePlayerError(Player player, String message, Throwable exception, ErrorLevel level) {
        // Gera um ID para o erro
        String errorId = generateErrorId(message, exception);
        handleError(message, exception, level);

        // Evita spam de mensagens para o jogador
        UUID playerUUID = player.getUniqueId();
        Long lastNotification = playerCooldowns.get(playerUUID);
        long now = System.currentTimeMillis();

        if (lastNotification != null && now - lastNotification < 5000) { // 5 segundos de cooldown
            return;
        }

        playerCooldowns.put(playerUUID, now);

        // Notifica o jogador com base no nível do erro
        switch (level) {
            case INFO:
                player.sendMessage(ChatColor.AQUA + "[Info] " + ChatColor.WHITE + message);
                break;
            case WARNING:
                player.sendMessage(ChatColor.GOLD + "[Aviso] " + ChatColor.YELLOW + message);
                break;
            case ERROR:
                player.sendMessage(ChatColor.RED + "[Erro] " + ChatColor.WHITE + message +
                        (debugMode ? " (ID: " + errorId.substring(0, 6) + ")" : ""));
                break;
            case CRITICAL:
                player.sendMessage(ChatColor.DARK_RED + "[ERRO CRÍTICO] " + ChatColor.RED + message);
                player.sendMessage(ChatColor.RED + "Por favor, informe um administrador e forneça o ID do erro: " +
                        ChatColor.GOLD + errorId.substring(0, 8));
                break;
        }
    }

    /**
     * Manipula um erro de comando
     *
     * @param sender Executor do comando
     * @param command Comando executado
     * @param args Argumentos do comando
     * @param message Mensagem de erro
     * @param exception Exceção (pode ser null)
     */
    public void handleCommandError(CommandSender sender, String command, String[] args, String message, Throwable exception) {
        String fullCommand = command + " " + String.join(" ", args);
        String errorMsg = "Erro ao executar comando '" + fullCommand + "': " + message;

        if (sender instanceof Player) {
            handlePlayerError((Player) sender, message, exception, ErrorLevel.ERROR);
        } else {
            handleError(errorMsg, exception, ErrorLevel.ERROR);
        }
    }

    /**
     * Manipula um erro de tarefa agendada
     *
     * @param taskName Nome da tarefa
     * @param message Mensagem de erro
     * @param exception Exceção
     */
    public void handleTaskError(String taskName, String message, Throwable exception) {
        String errorMsg = "Erro na tarefa '" + taskName + "': " + message;
        handleError(errorMsg, exception, ErrorLevel.ERROR);
    }

    /**
     * Manipula um erro de carregamento de recurso
     *
     * @param resourceType Tipo de recurso
     * @param resourceName Nome do recurso
     * @param message Mensagem de erro
     * @param exception Exceção
     */
    public void handleResourceError(String resourceType, String resourceName, String message, Throwable exception) {
        String errorMsg = "Erro ao carregar " + resourceType + " '" + resourceName + "': " + message;
        handleError(errorMsg, exception, ErrorLevel.ERROR);
    }

    /**
     * Manipula um erro de banco de dados
     *
     * @param operation Operação que falhou
     * @param message Mensagem de erro
     * @param exception Exceção
     */
    public void handleDatabaseError(String operation, String message, Throwable exception) {
        String errorMsg = "Erro de banco de dados durante " + operation + ": " + message;
        handleError(errorMsg, exception, ErrorLevel.ERROR);
    }

    /**
     * Manipula um erro de comunicação entre servidores
     *
     * @param server Servidor alvo
     * @param message Mensagem de erro
     * @param exception Exceção
     */
    public void handleCrossServerError(String server, String message, Throwable exception) {
        String errorMsg = "Erro de comunicação com servidor " + server + ": " + message;
        handleError(errorMsg, exception, ErrorLevel.ERROR);
    }

    /**
     * Manipula um erro crítico que pode exigir reinicialização
     *
     * @param component Componente que falhou
     * @param message Mensagem de erro
     * @param exception Exceção
     * @return true se o plugin deve ser desativado
     */
    public boolean handleCriticalError(String component, String message, Throwable exception) {
        String errorMsg = "ERRO CRÍTICO em " + component + ": " + message;
        handleError(errorMsg, exception, ErrorLevel.CRITICAL);

        // Notifica todos os operadores online
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || player.hasPermission("worldsmanager.admin")) {
                player.sendMessage(ChatColor.RED + "[WorldsManager] ERRO CRÍTICO detectado no componente " +
                        ChatColor.GOLD + component + ChatColor.RED + "!");
                player.sendMessage(ChatColor.RED + "Causa: " + ChatColor.WHITE + message);
                player.sendMessage(ChatColor.RED + "O plugin pode precisar ser reiniciado.");
            }
        }

        // Determina se o erro é fatal
        return isFatalError(exception);
    }

    /**
     * Verifica se um erro é fatal e exige desativação do plugin
     *
     * @param exception Exceção a ser verificada
     * @return true se o erro for fatal
     */
    private boolean isFatalError(Throwable exception) {
        if (exception == null) {
            return false;
        }

        // Lista de erros considerados fatais
        return exception instanceof OutOfMemoryError ||
                exception instanceof StackOverflowError ||
                exception instanceof LinkageError ||
                exception instanceof ThreadDeath;
    }

    /**
     * Gera um ID único para o erro
     *
     * @param message Mensagem de erro
     * @param exception Exceção
     * @return ID do erro
     */
    private String generateErrorId(String message, Throwable exception) {
        String errorDetails = message;

        if (exception != null) {
            errorDetails += exception.getClass().getName();

            if (exception.getStackTrace().length > 0) {
                errorDetails += exception.getStackTrace()[0].toString();
            }
        }

        // Gera um hash do erro para identificação
        return Integer.toHexString(errorDetails.hashCode()) + "-" +
                Long.toHexString(System.currentTimeMillis() % 10000);
    }

    /**
     * Escreve o erro no arquivo de log
     *
     * @param message Mensagem de erro
     * @param exception Exceção
     * @param level Nível de erro
     */
    private void logToFile(String message, Throwable exception, ErrorLevel level) {
        try (FileWriter fw = new FileWriter(errorLogFile, true);
             PrintWriter pw = new PrintWriter(fw)) {

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());

            pw.println("[" + timestamp + "] [" + level + "] " + message);

            if (exception != null) {
                pw.println("Exception: " + exception.getClass().getName() + ": " + exception.getMessage());

                // Escreve a stack trace se for erro ou crítico
                if (level == ErrorLevel.ERROR || level == ErrorLevel.CRITICAL) {
                    exception.printStackTrace(pw);
                } else {
                    // Para warnings, apenas a primeira linha da stack
                    StackTraceElement[] stack = exception.getStackTrace();
                    if (stack.length > 0) {
                        pw.println("at " + stack[0]);
                    }
                }

                pw.println();
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erro ao escrever no arquivo de log", e);
        }
    }

    /**
     * Limpa erros expirados do cache
     */
    private void cleanExpiredErrors() {
        Iterator<Map.Entry<String, ErrorEntry>> iterator = recentErrors.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ErrorEntry> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
            }
        }

        // Limpa também cooldowns expirados de jogadores
        Iterator<Map.Entry<UUID, Long>> playerIterator = playerCooldowns.entrySet().iterator();
        long threshold = System.currentTimeMillis() - 60000; // 1 minuto
        while (playerIterator.hasNext()) {
            Map.Entry<UUID, Long> entry = playerIterator.next();
            if (entry.getValue() < threshold) {
                playerIterator.remove();
            }
        }
    }
}