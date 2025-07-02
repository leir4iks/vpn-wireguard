package com.leir4iks.wireguard.bukkit;

import com.leir4iks.wireguard.common.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class WireguardGeneratorBukkitPlugin extends JavaPlugin {

    private Map<String, ClientInfo> clients = new HashMap<>();
    private String serverPrivateKey;
    private String serverPublicKey;
    private String serverIP;
    private int serverPort = 51820;
    private String serverAddress = "10.0.0.1/24";

    @Override
    public void onEnable() {
        getLogger().info("WireGuard VPN Generator v2.0.0 включен!");
        
        // Создаем папки
        File configDir = new File(getDataFolder(), "configs");
        File clientsDir = new File(getDataFolder(), "clients");
        if (!configDir.exists()) configDir.mkdirs();
        if (!clientsDir.exists()) clientsDir.mkdirs();
        
        // Получаем IP сервера
        serverIP = NetworkUtils.getPublicIPAddress();
        getLogger().info("Определен IP сервера: " + serverIP);
        
        // Генерируем ключи сервера
        WireguardCrypto.WireguardKeyPair serverKeys = WireguardCrypto.generateKeyPair();
        serverPrivateKey = serverKeys.getPrivateKey();
        serverPublicKey = serverKeys.getPublicKey();
        
        getLogger().info("Сервер готов к созданию VPN конфигураций!");
    }

    @Override
    public void onDisable() {
        getLogger().info("WireGuard VPN Generator выключен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("wireguard")) {
            return false;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "generate":
            case "create":
                return handleGenerate(sender, args);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            case "remove":
            case "delete":
                return handleRemove(sender, args);
            case "server":
                return handleServer(sender);
            case "help":
            default:
                showHelp(sender);
                return true;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== WireGuard VPN Generator v2.0.0 ===");
        sender.sendMessage("§a/wg generate <client_name> [port] - Создать VPN конфигурацию");
        sender.sendMessage("§a/wg list - Показать всех клиентов");
        sender.sendMessage("§a/wg info <client_name> - Информация о клиенте");
        sender.sendMessage("§a/wg remove <client_name> - Удалить клиента");
        sender.sendMessage("§a/wg server - Показать информацию о сервере");
        sender.sendMessage("§7Aliases: /wireguard, /vpn");
    }

    private boolean handleGenerate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /wg generate <client_name> [port]");
            return true;
        }

        String clientName = args[1];
        if (clients.containsKey(clientName)) {
            sender.sendMessage("§cКлиент с именем '" + clientName + "' уже существует!");
            return true;
        }

        int port = serverPort;
        if (args.length > 2) {
            try {
                port = Integer.parseInt(args[2]);
                if (port < 1024 || port > 65535) {
                    sender.sendMessage("§cПорт должен быть от 1024 до 65535");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cНекорректный номер порта");
                return true;
            }
        }

        try {
            generateVPNConfig(sender, clientName, port);
        } catch (Exception e) {
            sender.sendMessage("§cОшибка при создании VPN конфигурации: " + e.getMessage());
            getLogger().log(Level.SEVERE, "Ошибка генерации VPN конфига", e);
        }
        
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (clients.isEmpty()) {
            sender.sendMessage("§eНет созданных VPN клиентов");
            return true;
        }

        sender.sendMessage("§6=== VPN Клиенты ===");
        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            ClientInfo client = entry.getValue();
            sender.sendMessage("§a• " + entry.getKey() + " §7(" + client.getAddress() + ") - " + client.getCreatedDate());
        }
        sender.sendMessage("§7Всего клиентов: " + clients.size());
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /wg info <client_name>");
            return true;
        }

        String clientName = args[1];
        ClientInfo client = clients.get(clientName);
        
        if (client == null) {
            sender.sendMessage("§cКлиент '" + clientName + "' не найден");
            return true;
        }

        sender.sendMessage("§6=== Информация о клиенте: " + clientName + " ===");
        sender.sendMessage("§7IP адрес: §f" + client.getAddress());
        sender.sendMessage("§7Публичный ключ: §f" + client.getPublicKey());
        sender.sendMessage("§7Создан: §f" + client.getCreatedDate());
        sender.sendMessage("§7Конфигурация: §f" + clientName + "_client.conf");
        
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /wg remove <client_name>");
            return true;
        }

        String clientName = args[1];
        if (!clients.containsKey(clientName)) {
            sender.sendMessage("§cКлиент '" + clientName + "' не найден");
            return true;
        }

        clients.remove(clientName);
        
        // Удаляем файлы
        File configDir = new File(getDataFolder(), "configs");
        new File(configDir, clientName + "_client.conf").delete();
        new File(configDir, clientName + "_server_peer.conf").delete();
        
        sender.sendMessage("§aКлиент '" + clientName + "' удален");
        return true;
    }

    private boolean handleServer(CommandSender sender) {
        sender.sendMessage("§6=== Информация о VPN сервере ===");
        sender.sendMessage("§7IP адрес: §f" + serverIP);
        sender.sendMessage("§7Порт: §f" + serverPort);
        sender.sendMessage("§7Внутренний адрес: §f" + serverAddress);
        sender.sendMessage("§7Публичный ключ: §f" + serverPublicKey);
        sender.sendMessage("§7Активных клиентов: §f" + clients.size());
        return true;
    }

    private void generateVPNConfig(CommandSender sender, String clientName, int port) throws IOException {
        // Генерируем ключи клиента
        WireguardCrypto.WireguardKeyPair clientKeys = WireguardCrypto.generateKeyPair();
        String presharedKey = WireguardCrypto.generatePresharedKey();

        // Определяем IP клиента
        List<String> usedIPs = new ArrayList<>();
        for (ClientInfo client : clients.values()) {
            usedIPs.add(client.getAddress().split("/")[0]);
        }
        String clientAddress = NetworkUtils.getNextAvailableIP("10.0.0.1", 32, usedIPs);

        // Создаем конфигурацию клиента
        List<String> dnsServers = Arrays.asList("8.8.8.8", "8.8.4.4", "1.1.1.1");
        String clientConfig = WireguardConfigGenerator.generateClientConfig(
            clientName,
            clientKeys.getPrivateKey(),
            serverPublicKey,
            presharedKey,
            clientAddress,
            serverIP,
            port,
            Arrays.asList("0.0.0.0/0", "::/0"),
            dnsServers
        );

        // Создаем peer конфигурацию для сервера
        WireguardConfigGenerator.ClientPeer clientPeer = new WireguardConfigGenerator.ClientPeer(
            clientName,
            clientKeys.getPublicKey(),
            presharedKey,
            clientAddress
        );

        // Генерируем полную серверную конфигурацию
        List<WireguardConfigGenerator.ClientPeer> allClients = new ArrayList<>();
        allClients.add(clientPeer);
        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            ClientInfo client = entry.getValue();
            allClients.add(new WireguardConfigGenerator.ClientPeer(
                entry.getKey(),
                client.getPublicKey(),
                client.getPresharedKey(),
                client.getAddress()
            ));
        }

        String serverConfig = WireguardConfigGenerator.generateServerConfig(
            serverPrivateKey,
            serverAddress,
            port,
            "eth0", // Основной сетевой интерфейс
            allClients
        );

        // Создаем скрипт запуска
        String startScript = WireguardConfigGenerator.generateServerStartScript(clientName);

        // Сохраняем файлы
        File configDir = new File(getDataFolder(), "configs");
        
        // Конфигурация клиента
        File clientConfigFile = new File(configDir, clientName + "_client.conf");
        try (FileWriter writer = new FileWriter(clientConfigFile)) {
            writer.write(clientConfig);
        }

        // Обновленная конфигурация сервера
        File serverConfigFile = new File(configDir, "server_complete.conf");
        try (FileWriter writer = new FileWriter(serverConfigFile)) {
            writer.write(serverConfig);
        }

        // Скрипт запуска
        File startScriptFile = new File(configDir, "start_server.sh");
        try (FileWriter writer = new FileWriter(startScriptFile)) {
            writer.write(startScript);
        }
        startScriptFile.setExecutable(true);

        // QR код конфигурации (текстовый для консоли)
        File qrFile = new File(configDir, clientName + "_qr.txt");
        try (FileWriter writer = new FileWriter(qrFile)) {
            writer.write("=== QR код для импорта в WireGuard приложение ===\n");
            writer.write("Отсканируйте данный текст QR-кодом или импортируйте файл:\n");
            writer.write(clientName + "_client.conf\n\n");
            writer.write("Альтернативно - скопируйте содержимое файла в WireGuard приложение.\n");
        }

        // Сохраняем информацию о клиенте
        clients.put(clientName, new ClientInfo(
            clientKeys.getPublicKey(),
            presharedKey,
            clientAddress,
            new Date().toString()
        ));

        // Сообщение об успехе
        sender.sendMessage("§a=== VPN конфигурация создана успешно! ===");
        sender.sendMessage("§7Клиент: §f" + clientName);
        sender.sendMessage("§7IP клиента: §f" + clientAddress);
        sender.sendMessage("§7Сервер: §f" + serverIP + ":" + port);
        sender.sendMessage("§7Файлы сохранены в: §f" + configDir.getAbsolutePath());
        sender.sendMessage("§e▪ Клиент: §f" + clientConfigFile.getName());
        sender.sendMessage("§e▪ Сервер: §fserver_complete.conf");
        sender.sendMessage("§e▪ Скрипт запуска: §fstart_server.sh");
        sender.sendMessage("§6Для настройки сервера выполните:");
        sender.sendMessage("§7sudo ./start_server.sh");
        
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.sendMessage("§bПубличный ключ клиента: §f" + clientKeys.getPublicKey());
        }
    }

    private static class ClientInfo {
        private final String publicKey;
        private final String presharedKey;
        private final String address;
        private final String createdDate;

        public ClientInfo(String publicKey, String presharedKey, String address, String createdDate) {
            this.publicKey = publicKey;
            this.presharedKey = presharedKey;
            this.address = address;
            this.createdDate = createdDate;
        }

        public String getPublicKey() { return publicKey; }
        public String getPresharedKey() { return presharedKey; }
        public String getAddress() { return address; }
        public String getCreatedDate() { return createdDate; }
    }
}
