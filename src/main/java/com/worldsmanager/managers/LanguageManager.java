package com.worldsmanager.managers;

import com.worldsmanager.WorldsManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class LanguageManager {

    private final WorldsManager plugin;
    private final Map<String, YamlConfiguration> languages;
    private String defaultLanguage;

    public LanguageManager(WorldsManager plugin) {
        this.plugin = plugin;
        this.languages = new HashMap<>();
        loadLanguages();
    }

    // Load all language files
    private void loadLanguages() {
        // Create language directory if it doesn't exist
        File langDir = new File(plugin.getDataFolder(), "");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // Save default language files
        saveResourceIfNotExists("messages_en.yml");
        saveResourceIfNotExists("messages_pt.yml");

        // Load all language files
        loadLanguageFile("en", "messages_en.yml");
        loadLanguageFile("pt", "messages_pt.yml");

        // Set default language from config
        this.defaultLanguage = plugin.getConfigManager().getDefaultLanguage();

        // Fallback to English if default language is not loaded
        if (!languages.containsKey(defaultLanguage)) {
            plugin.getLogger().warning("Default language '" + defaultLanguage + "' not found, falling back to English");
            this.defaultLanguage = "en";
        }
    }

    // Reload language files
    public void reload() {
        languages.clear();
        loadLanguages();
    }

    // Save a resource if it doesn't exist
    private void saveResourceIfNotExists(String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }
    }

    // Load a language file
    private void loadLanguageFile(String langCode, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.getLogger().warning("Language file " + fileName + " not found");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            // Check if it's a valid language file
            if (config.contains("prefix")) {
                languages.put(langCode, config);
                plugin.getLogger().info("Loaded language: " + langCode);
            } else {
                plugin.getLogger().warning("Invalid language file: " + fileName);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load language file: " + fileName, e);
        }
    }

    // Get message from language file
    public String getMessage(String path) {
        return getMessage(path, "");
    }

    // Get message with replacements
    public String getMessage(String path, String... replacements) {
        YamlConfiguration config = languages.get(defaultLanguage);
        if (config == null) {
            return "Missing language: " + defaultLanguage;
        }

        String message = config.getString(path);
        if (message == null) {
            return "Missing message: " + path;
        }

        // Apply replacements
        for (int i = 0; i < replacements.length; i++) {
            message = message.replace("{" + i + "}", replacements[i]);
        }

        return message;
    }

    // Get multiple messages
    public List<String> getMessages(String path) {
        YamlConfiguration config = languages.get(defaultLanguage);
        if (config == null) {
            List<String> result = new ArrayList<>();
            result.add("Missing language: " + defaultLanguage);
            return result;
        }

        List<String> messages = config.getStringList(path);
        if (messages.isEmpty()) {
            messages = new ArrayList<>();
            messages.add("Missing messages: " + path);
        }

        return messages;
    }

    // Get the prefix
    public String getPrefix() {
        YamlConfiguration config = languages.get(defaultLanguage);
        if (config == null) {
            return "[WorldsManager] ";
        }

        String prefix = config.getString("prefix");
        if (prefix == null) {
            return "[WorldsManager] ";
        }

        return prefix;
    }
}