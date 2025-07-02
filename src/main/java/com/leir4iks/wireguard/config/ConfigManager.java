package com.leir4iks.wireguard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.leir4iks.wireguard.crypto.WireguardCrypto;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Менеджер конфигураций VPN клиентов
 */
public class ConfigManager {
    
    private final File dataDirectory;
    private final File clientsDirectory;
    private final Gson gson;
    
    public ConfigManager(File dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.clientsDirectory = new File(dataDirectory, "clients");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Создаем директории если их нет
        if (!clientsDirectory.exists()) {
            clientsDirectory.mkdirs();
        }
    }
    
    /**
     * Создание конфигурации для клиента
     */
    public String createClientConfig(String clientName, String privateKey, 
                                   String publicKey, String serverIP, int serverPort) {
        
        // Генерируем серверные ключи
        WireguardCrypto.WireguardKeyPair serverKeys = WireguardCrypto.generateKeyPair();
        String presharedKey = WireguardCrypto.generatePresharedKey();
        
        // Создаем конфигурацию клиента
        StringBuilder config = new StringBuilder();
        config.append("# WireGuard конфигурация для ").append(clientName).append("\n");
        config.append("# Создано: ").append(LocalDateTime.now()).append("\n");
        config.append("# Плагин: Leir4iks WireGuard VPN v3.0.0\n\n");
        
        config.append("[Interface]\n");
        config.append("PrivateKey = ").append(privateKey).append("\n");
        config.append("Address = 10.0.0.2/24\n");
        config.append("DNS = 8.8.8.8, 8.8.4.4\n\n");
        
        config.append("[Peer]\n");
        config.append("PublicKey = ").append(serverKeys.getPublicKey()).append("\n");
        config.append("PresharedKey = ").append(presharedKey).append("\n");
        config.append("Endpoint = ").append(serverIP).append(":").append(serverPort).append("\n");
        config.append("AllowedIPs = 0.0.0.0/0, ::/0\n");
        config.append("PersistentKeepalive = 25\n");
        
        // Сохраняем информацию о клиенте
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.name = clientName;
        clientInfo.publicKey = publicKey;
        clientInfo.privateKey = privateKey;
        clientInfo.serverPublicKey = serverKeys.getPublicKey();
        clientInfo.presharedKey = presharedKey;
        clientInfo.created = LocalDateTime.now();
        clientInfo.lastUsed = null;
        
        saveClientInfo(clientName, clientInfo);
        
        return config.toString();
    }
    
    /**
     * Сохранение конфигурации клиента
     */
    public void saveClientConfig(String clientName, String config) throws IOException {
        File configFile = new File(clientsDirectory, clientName + ".conf");
        Files.write(configFile.toPath(), config.getBytes());
    }
    
    /**
     * Получение конфигурации клиента
     */
    public String getClientConfig(String clientName) throws IOException {
        File configFile = new File(clientsDirectory, clientName + ".conf");
        if (!configFile.exists()) {
            throw new FileNotFoundException("Конфигурация клиента " + clientName + " не найдена");
        }
        return Files.readString(configFile.toPath());
    }
    
    /**
     * Проверка существования клиента
     */
    public boolean hasClient(String clientName) {
        File infoFile = new File(clientsDirectory, clientName + ".json");
        return infoFile.exists();
    }
    
    /**
     * Получение всех клиентов
     */
    public Set<String> getAllClients() {
        Set<String> clients = new HashSet<>();
        File[] files = clientsDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace(".json", "");
                clients.add(name);
            }
        }
        
        return clients;
    }
    
    /**
     * Удаление клиента
     */
    public void removeClient(String clientName) {
        File configFile = new File(clientsDirectory, clientName + ".conf");
        File infoFile = new File(clientsDirectory, clientName + ".json");
        
        configFile.delete();
        infoFile.delete();
    }
    
    /**
     * Сохранение информации о клиенте
     */
    private void saveClientInfo(String clientName, ClientInfo info) {
        try {
            File infoFile = new File(clientsDirectory, clientName + ".json");
            String json = gson.toJson(info);
            Files.write(infoFile.toPath(), json.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сохранения информации о клиенте", e);
        }
    }
    
    /**
     * Получение информации о клиенте
     */
    public Map<String, String> getClientInfo(String clientName) throws IOException {
        File infoFile = new File(clientsDirectory, clientName + ".json");
        if (!infoFile.exists()) {
            throw new FileNotFoundException("Информация о клиенте " + clientName + " не найдена");
        }
        
        String json = Files.readString(infoFile.toPath());
        ClientInfo info = gson.fromJson(json, ClientInfo.class);
        
        Map<String, String> result = new HashMap<>();
        result.put("name", info.name);
        result.put("publicKey", info.publicKey);
        result.put("created", info.created.toString());
        result.put("lastUsed", info.lastUsed != null ? info.lastUsed.toString() : "Никогда");
        
        return result;
    }
    
    /**
     * Информация о клиенте
     */
    private static class ClientInfo {
        String name;
        String publicKey;
        String privateKey;
        String serverPublicKey;
        String presharedKey;
        LocalDateTime created;
        LocalDateTime lastUsed;
    }
}
