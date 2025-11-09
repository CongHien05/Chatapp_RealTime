package org.example.danbainoso.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "config.properties";
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            properties.load(fis);
        } catch (IOException e) {
            // Use default values if config file doesn't exist
            setDefaults();
        }
    }
    
    private static void setDefaults() {
        // Database configuration
        properties.setProperty("db.url", "jdbc:mysql://localhost:3306/chat_app?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        properties.setProperty("db.username", "root");
        properties.setProperty("db.password", "");
        properties.setProperty("db.driver", "com.mysql.cj.jdbc.Driver");
        properties.setProperty("db.pool.size", "10");
        
        // Server configuration
        properties.setProperty("server.host", "localhost");
        properties.setProperty("server.rmi.port", "1099");
        properties.setProperty("server.chat.port", "8888");
        properties.setProperty("server.video.port", "9999");
        
        // Client configuration
        properties.setProperty("client.rmi.registry", "localhost");
        properties.setProperty("client.rmi.port", "1099");
        
        // Application configuration
        properties.setProperty("app.name", "DanBaiNoSo Chat");
        properties.setProperty("app.version", "1.0.0");
    }
    
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public static int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    // Database getters
    public static String getDbUrl() {
        return getProperty("db.url");
    }
    
    public static String getDbUsername() {
        return getProperty("db.username");
    }
    
    public static String getDbPassword() {
        return getProperty("db.password");
    }
    
    public static String getDbDriver() {
        return getProperty("db.driver");
    }
    
    public static int getDbPoolSize() {
        return getIntProperty("db.pool.size", 10);
    }
    
    // Server getters
    public static String getServerHost() {
        return getProperty("server.host", "localhost");
    }
    
    public static int getServerRmiPort() {
        return getIntProperty("server.rmi.port", 1099);
    }
    
    public static int getServerChatPort() {
        return getIntProperty("server.chat.port", 8888);
    }
    
    public static int getServerVideoPort() {
        return getIntProperty("server.video.port", 9999);
    }
    
    // Client getters
    public static String getClientRmiRegistry() {
        return getProperty("client.rmi.registry", "localhost");
    }
    
    public static int getClientRmiPort() {
        return getIntProperty("client.rmi.port", 1099);
    }
}
