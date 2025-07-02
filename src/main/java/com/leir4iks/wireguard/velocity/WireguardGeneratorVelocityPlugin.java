package com.leir4iks.wireguard.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;

@Plugin(
    id = "leir4iks-wireguard-generator",
    name = "Leir4iks WireGuard VPN Generator",
    version = "2.0.0",
    description = "Полноценный генератор WireGuard VPN конфигураций для Velocity",
    authors = {"leir4iks"}
)
public class WireguardGeneratorVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public WireguardGeneratorVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("WireGuard VPN Generator v2.0.0 для Velocity запущен!");
        
        // Создаем папки
        File configDir = new File(dataDirectory.toFile(), "configs");
        File clientsDir = new File(dataDirectory.toFile(), "clients");
        if (!configDir.exists()) configDir.mkdirs();
        if (!clientsDir.exists()) clientsDir.mkdirs();

        // Регистрируем команды
        CommandManager commandManager = server.getCommandManager();
        commandManager.register("wireguard", new WireguardVelocityCommand(this), "wg", "vpn");
        
        logger.info("Плагин готов к работе! Используйте /wg help для справки");
    }

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public Path getDataDirectory() { return dataDirectory; }
}
