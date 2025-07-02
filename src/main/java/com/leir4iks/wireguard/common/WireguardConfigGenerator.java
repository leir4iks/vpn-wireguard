package com.leir4iks.wireguard.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class WireguardConfigGenerator {
    
    /**
     * Создает конфигурацию клиента WireGuard
     */
    public static String generateClientConfig(String clientName, String privateKey, 
                                            String serverPublicKey, String presharedKey,
                                            String clientAddress, String serverEndpoint, 
                                            int serverPort, List<String> allowedIPs,
                                            List<String> dnsServers) {
        
        StringBuilder config = new StringBuilder();
        config.append("# WireGuard конфигурация клиента: ").append(clientName).append("\n");
        config.append("# Создано: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        config.append("# Плагин: leir4iks-wireguard-generator v2.0.0\n\n");
        
        config.append("[Interface]\n");
        config.append("PrivateKey = ").append(privateKey).append("\n");
        config.append("Address = ").append(clientAddress).append("\n");
        
        if (dnsServers != null && !dnsServers.isEmpty()) {
            config.append("DNS = ").append(String.join(", ", dnsServers)).append("\n");
        }
        
        config.append("\n[Peer]\n");
        config.append("PublicKey = ").append(serverPublicKey).append("\n");
        
        if (presharedKey != null && !presharedKey.isEmpty()) {
            config.append("PresharedKey = ").append(presharedKey).append("\n");
        }
        
        config.append("Endpoint = ").append(serverEndpoint).append(":").append(serverPort).append("\n");
        
        if (allowedIPs != null && !allowedIPs.isEmpty()) {
            config.append("AllowedIPs = ").append(String.join(", ", allowedIPs)).append("\n");
        } else {
            config.append("AllowedIPs = 0.0.0.0/0, ::/0\n");
        }
        
        config.append("PersistentKeepalive = 25\n");
        
        return config.toString();
    }
    
    /**
     * Создает конфигурацию сервера WireGuard
     */
    public static String generateServerConfig(String serverPrivateKey, String serverAddress,
                                            int listenPort, String networkInterface,
                                            List<ClientPeer> clients) {
        
        StringBuilder config = new StringBuilder();
        config.append("# WireGuard конфигурация сервера\n");
        config.append("# Создано: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        config.append("# Плагин: leir4iks-wireguard-generator v2.0.0\n\n");
        
        config.append("[Interface]\n");
        config.append("PrivateKey = ").append(serverPrivateKey).append("\n");
        config.append("Address = ").append(serverAddress).append("\n");
        config.append("ListenPort = ").append(listenPort).append("\n");
        config.append("SaveConfig = false\n\n");
        
        // Команды для настройки iptables (для Linux)
        config.append("# Команды для настройки роутинга (выполнить вручную на сервере):\n");
        config.append("# echo 'net.ipv4.ip_forward = 1' >> /etc/sysctl.conf\n");
        config.append("# sysctl -p\n");
        config.append("# iptables -A INPUT -p udp --dport ").append(listenPort).append(" -j ACCEPT\n");
        config.append("# iptables -A FORWARD -i wg0 -j ACCEPT\n");
        config.append("# iptables -t nat -A POSTROUTING -o ").append(networkInterface).append(" -j MASQUERADE\n");
        config.append("# iptables-save > /etc/iptables/rules.v4\n\n");
        
        config.append("# PostUp команды для автоматической настройки:\n");
        config.append("PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ").append(networkInterface).append(" -j MASQUERADE; iptables -A INPUT -p udp --dport ").append(listenPort).append(" -j ACCEPT\n");
        config.append("PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ").append(networkInterface).append(" -j MASQUERADE; iptables -D INPUT -p udp --dport ").append(listenPort).append(" -j ACCEPT\n\n");
        
        // Добавляем всех клиентов
        for (ClientPeer client : clients) {
            config.append("# Клиент: ").append(client.getName()).append("\n");
            config.append("[Peer]\n");
            config.append("PublicKey = ").append(client.getPublicKey()).append("\n");
            
            if (client.getPresharedKey() != null && !client.getPresharedKey().isEmpty()) {
                config.append("PresharedKey = ").append(client.getPresharedKey()).append("\n");
            }
            
            config.append("AllowedIPs = ").append(client.getAllowedIPs()).append("\n");
            config.append("PersistentKeepalive = 25\n\n");
        }
        
        return config.toString();
    }
    
    /**
     * Генерирует скрипт для запуска WireGuard на сервере
     */
    public static String generateServerStartScript(String configName) {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n");
        script.append("# Скрипт запуска WireGuard сервера\n");
        script.append("# Создано плагином leir4iks-wireguard-generator v2.0.0\n\n");
        
        script.append("# Проверяем права root\n");
        script.append("if [ \"$EUID\" -ne 0 ]; then\n");
        script.append("    echo \"Запустите скрипт с правами root: sudo $0\"\n");
        script.append("    exit 1\n");
        script.append("fi\n\n");
        
        script.append("# Копируем конфигурацию\n");
        script.append("cp ").append(configName).append("_server.conf /etc/wireguard/wg0.conf\n\n");
        
        script.append("# Включаем IP forwarding\n");
        script.append("echo 'net.ipv4.ip_forward = 1' >> /etc/sysctl.conf\n");
        script.append("sysctl -p\n\n");
        
        script.append("# Запускаем WireGuard\n");
        script.append("wg-quick up wg0\n\n");
        
        script.append("# Включаем автозапуск\n");
        script.append("systemctl enable wg-quick@wg0\n\n");
        
        script.append("echo \"WireGuard сервер запущен!\"\n");
        script.append("echo \"Статус: $(wg show)\"\n");
        
        return script.toString();
    }
    
    public static class ClientPeer {
        private final String name;
        private final String publicKey;
        private final String presharedKey;
        private final String allowedIPs;
        
        public ClientPeer(String name, String publicKey, String presharedKey, String allowedIPs) {
            this.name = name;
            this.publicKey = publicKey;
            this.presharedKey = presharedKey;
            this.allowedIPs = allowedIPs;
        }
        
        public String getName() { return name; }
        public String getPublicKey() { return publicKey; }
        public String getPresharedKey() { return presharedKey; }
        public String getAllowedIPs() { return allowedIPs; }
    }
}
