package com.leir4iks.wireguard.bukkit;

import com.leir4iks.wireguard.core.*;
import com.leir4iks.wireguard.crypto.WireguardCrypto;
import com.leir4iks.wireguard.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class WireguardVPNBukkitPlugin extends JavaPlugin {

    private VPNServer vpnServer;
    private ConfigManager configManager;
    private int vpnPort = 51820;
    private boolean autoStart = true;

    @Override
    public void onEnable() {
        getLogger().info("Leir4iks WireGuard VPN v3.0.0 загружается...");
        
        // Создаем конфигурационные папки
        createDirectories();
        
        // Инициализируем менеджер конфигураций
        configManager = new ConfigManager(getDataFolder());
        
        // Загружаем настройки
        loadConfig();
        
        // Запускаем VPN сервер
        if (autoStart) {
            startVPNServer();
        }
        
        getLogger().info("WireGuard VPN плагин готов! Используйте /vpn help");
    }

    @Override
    public void onDisable() {
        stopVPNServer();
        getLogger().info("WireGuard VPN плагин выгружен");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("vpn")) {
            return false;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                return handleStart(sender);
            case "stop":
                return handleStop(sender);
            case "restart":
                return handleRestart(sender);
            case "status":
                return handleStatus(sender);
            case "create":
            case "add":
                return handleCreateClient(sender, args);
            case "remove":
            case "delete":
                return handleRemoveClient(sender, args);
            case "list":
                return handleListClients(sender);
            case "info":
                return handleClientInfo(sender, args);
            case "config":
                return handleGetConfig(sender, args);
            case "help":
            default:
                showHelp(sender);
                return true;
        }
    }

    /**
     * Показать справку по командам
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== Leir4iks WireGuard VPN v3.0.0 ===");
        sender.sendMessage("§a/vpn start §7- Запустить VPN сервер");
        sender.sendMessage("§a/vpn stop §7- Остановить VPN сервер");
        sender.sendMessage("§a/vpn restart §7- Перезапустить VPN сервер");
        sender.sendMessage("§a/vpn status §7- Статус сервера и клиентов");
        sender.sendMessage("§a/vpn create <имя> §7- Создать VPN клиента");
        sender.sendMessage("§a/vpn remove <имя> §7- Удалить VPN клиента");
        sender.sendMessage("§a/vpn list §7- Список всех клиентов");
        sender.sendMessage("§a/vpn info <имя> §7- Информация о клиенте");
        sender.sendMessage("§a/vpn config <имя> §7- Получить конфигурацию клиента");
        sender.sendMessage("§7Порт VPN сервера: §f" + vpnPort);
    }

    /**
     * Запуск VPN сервера
     */
    private boolean handleStart(CommandSender sender) {
        if (vpnServer != null && vpnServer.isRunning()) {
            sender.sendMessage("§cVPN сервер уже запущен!");
            return true;
        }

        CompletableFuture.runAsync(() -> {
            try {
                startVPNServer();
                sender.sendMessage("§aVPN сервер успешно запущен на порту " + vpnPort);
            } catch (Exception e) {
                sender.sendMessage("§cОшибка запуска VPN сервера: " + e.getMessage());
                getLogger().log(Level.SEVERE, "Ошибка запуска VPN сервера", e);
            }
        });

        return true;
    }

    /**
     * Остановка VPN сервера
     */
    private boolean handleStop(CommandSender sender) {
        if (vpnServer == null || !vpnServer.isRunning()) {
            sender.sendMessage("§cVPN сервер не запущен!");
            return true;
        }

        stopVPNServer();
        sender.sendMessage("§aVPN сервер остановлен");
        return true;
    }

    /**
     * Перезапуск VPN сервера
     */
    private boolean handleRestart(CommandSender sender) {
        sender.sendMessage("§eПерезапуск VPN сервера...");
        
        CompletableFuture.runAsync(() -> {
            stopVPNServer();
            
            try {
                Thread.sleep(2000); // Небольшая пауза
                startVPNServer();
                sender.sendMessage("§aVPN сервер перезапущен успешно");
            } catch (Exception e) {
                sender.sendMessage("§cОшибка перезапуска: " + e.getMessage());
                getLogger().log(Level.SEVERE, "Ошибка перезапуска VPN сервера", e);
            }
        });

        return true;
    }

    /**
     * Статус VPN сервера
     */
    private boolean handleStatus(CommandSender sender) {
        if (vpnServer == null || !vpnServer.isRunning()) {
            sender.sendMessage("§c❌ VPN сервер остановлен");
            return true;
        }

        sender.sendMessage("§a✅ VPN сервер запущен");
        sender.sendMessage("§7Адрес: §f" + vpnServer.getServerIP() + ":" + vpnServer.getPort());
        sender.sendMessage("§7Подключенных клиентов: §f" + vpnServer.getClients().size());

        if (!vpnServer.getClients().isEmpty()) {
            sender.sendMessage("§6Активные клиенты:");
            vpnServer.getClients().forEach((name, client) -> {
                VPNClientStats stats = client.getStats();
                sender.sendMessage(String.format("§7• %s (%s) - ↓%s ↑%s", 
                    name, stats.getIp(), 
                    formatBytes(stats.getBytesReceived()),
                    formatBytes(stats.getBytesSent())
                ));
            });
        }

        return true;
    }

    /**
     * Создание нового VPN клиента
     */
    private boolean handleCreateClient(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /vpn create <имя_клиента>");
            return true;
        }

        String clientName = args[1];
        
        // Проверяем, не существует ли уже такой клиент
        if (configManager.hasClient(clientName)) {
            sender.sendMessage("§cКлиент с именем '" + clientName + "' уже существует!");
            return true;
        }

        try {
            // Генерируем ключи для клиента
            WireguardCrypto.WireguardKeyPair keys = WireguardCrypto.generateKeyPair();
            
            // Создаем конфигурацию клиента
            String clientConfig = configManager.createClientConfig(
                clientName, 
                keys.getPrivateKey(), 
                keys.getPublicKey(),
                vpnServer != null ? vpnServer.getServerIP() : "localhost",
                vpnPort
            );

            // Сохраняем конфигурацию
            configManager.saveClientConfig(clientName, clientConfig);

            sender.sendMessage("§a✅ VPN клиент '" + clientName + "' создан успешно!");
            sender.sendMessage("§7Конфигурация сохранена в: §f" + clientName + ".conf");
            sender.sendMessage("§7Используйте §a/vpn config " + clientName + " §7для получения конфигурации");

            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage("§bПубличный ключ: §f" + keys.getPublicKey());
            }

        } catch (Exception e) {
            sender.sendMessage("§cОшибка создания клиента: " + e.getMessage());
            getLogger().log(Level.SEVERE, "Ошибка создания VPN клиента", e);
        }

        return true;
    }

    /**
     * Удаление VPN клиента
     */
    private boolean handleRemoveClient(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /vpn remove <имя_клиента>");
            return true;
        }

        String clientName = args[1];

        if (!configManager.hasClient(clientName)) {
            sender.sendMessage("§cКлиент '" + clientName + "' не найден!");
            return true;
        }

        try {
            // Отключаем клиента если он подключен
            if (vpnServer != null) {
                vpnServer.removeClient(clientName);
            }

            // Удаляем конфигурацию
            configManager.removeClient(clientName);

            sender.sendMessage("§a✅ VPN клиент '" + clientName + "' удален");

        } catch (Exception e) {
            sender.sendMessage("§cОшибка удаления клиента: " + e.getMessage());
            getLogger().log(Level.SEVERE, "Ошибка удаления VPN клиента", e);
        }

        return true;
    }

    /**
     * Список всех клиентов
     */
    private boolean handleListClients(CommandSender sender) {
        var savedClients = configManager.getAllClients();
        var activeClients = vpnServer != null ? vpnServer.getClients() : null;

        if (savedClients.isEmpty()) {
            sender.sendMessage("§eНет созданных VPN клиентов");
            return true;
        }

        sender.sendMessage("§6=== VPN Клиенты ===");
        
        for (String clientName : savedClients) {
            boolean isOnline = activeClients != null && activeClients.containsKey(clientName);
            String status = isOnline ? "§a●" : "§7○";
            
            sender.sendMessage(String.format("%s §f%s %s", 
                status, clientName, isOnline ? "§7(в сети)" : "§7(оффлайн)"));
        }

        sender.sendMessage("§7Всего клиентов: §f" + savedClients.size());
        
        if (activeClients != null) {
            sender.sendMessage("§7В сети: §a" + activeClients.size());
        }

        return true;
    }

    /**
     * Информация о клиенте
     */
    private boolean handleClientInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /vpn info <имя_клиента>");
            return true;
        }

        String clientName = args[1];

        if (!configManager.hasClient(clientName)) {
            sender.sendMessage("§cКлиент '" + clientName + "' не найден!");
            return true;
        }

        try {
            var clientInfo = configManager.getClientInfo(clientName);
            boolean isOnline = vpnServer != null && vpnServer.getClients().containsKey(clientName);

            sender.sendMessage("§6=== Информация о клиенте: " + clientName + " ===");
            sender.sendMessage("§7Статус: " + (isOnline ? "§aВ сети" : "§7Оффлайн"));
            sender.sendMessage("§7Публичный ключ: §f" + clientInfo.get("publicKey"));
            sender.sendMessage("§7Создан: §f" + clientInfo.get("created"));

            if (isOnline) {
                VPNClient client = vpnServer.getClient(clientName);
                VPNClientStats stats = client.getStats();
                
                sender.sendMessage("§7IP в VPN: §f" + stats.getIp());
                sender.sendMessage("§7Подключен: §f" + stats.getConnectedAt());
                sender.sendMessage("§7Получено: §f" + formatBytes(stats.getBytesReceived()));
                sender.sendMessage("§7Отправлено: §f" + formatBytes(stats.getBytesSent()));
                sender.sendMessage("§7Пакетов: §f" + stats.getPacketsReceived() + " ↓ " + stats.getPacketsSent() + " ↑");
            }

        } catch (Exception e) {
            sender.sendMessage("§cОшибка получения информации: " + e.getMessage());
            getLogger().log(Level.SEVERE, "Ошибка получения информации о клиенте", e);
        }

        return true;
    }

    /**
     * Получение конфигурации клиента
     */
    private boolean handleGetConfig(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /vpn config <имя_клиента>");
            return true;
        }

        String clientName = args[1];

        if (!configManager.hasClient(clientName)) {
            sender.sendMessage("§cКлиент '" + clientName + "' не найден!");
            return true;
        }

        try {
            String config = configManager.getClientConfig(clientName);
            
            sender.sendMessage("§6=== Конфигурация клиента: " + clientName + " ===");
            sender.sendMessage("§7Скопируйте эту конфигурацию в WireGuard клиент:");
            sender.sendMessage("§f" + config.replace("\n", "\n§f"));
            
            sender.sendMessage("§a💡 Совет: Импортируйте эту конфигурацию в официальное приложение WireGuard");

        } catch (Exception e) {
            sender.sendMessage("§cОшибка получения конфигурации: " + e.getMessage());
            getLogger().log(Level.SEVERE, "Ошибка получения конфигурации клиента", e);
        }

        return true;
    }

    /**
     * Запуск VPN сервера
     */
    private void startVPNServer() {
        if (vpnServer != null && vpnServer.isRunning()) {
            return;
        }

        try {
            vpnServer = new VPNServer(vpnPort);
            
            // Запускаем в отдельном потоке
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        vpnServer.start();
                    } catch (InterruptedException e) {
                        getLogger().warning("VPN сервер прерван");
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Критическая ошибка VPN сервера", e);
                    }
                }
            }.runTaskAsynchronously(this);

            // Даем серверу время на запуск
            Thread.sleep(1000);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка инициализации VPN сервера", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Остановка VPN сервера
     */
    private void stopVPNServer() {
        if (vpnServer != null) {
            vpnServer.stop();
            vpnServer = null;
        }
    }

    /**
     * Создание необходимых директорий
     */
    private void createDirectories() {
        File dataFolder = getDataFolder();
        new File(dataFolder, "configs").mkdirs();
        new File(dataFolder, "clients").mkdirs();
    }

    /**
     * Загрузка конфигурации плагина
     */
    private void loadConfig() {
        saveDefaultConfig();
        vpnPort = getConfig().getInt("vpn.port", 51820);
        autoStart = getConfig().getBoolean("vpn.auto-start", true);
    }

    /**
     * Форматирование байтов в человеко-читаемый вид
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
