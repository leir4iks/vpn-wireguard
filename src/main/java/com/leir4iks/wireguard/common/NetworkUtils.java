package com.leir4iks.wireguard.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtils {
    
    /**
     * Получает внешний IP-адрес сервера
     */
    public static String getPublicIPAddress() {
        try {
            // Пытаемся получить публичный IP через внешний сервис
            URL whatismyip = new URL("http://checkip.amazonaws.com");
            BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            String ip = in.readLine().trim();
            in.close();
            
            if (isValidIPAddress(ip)) {
                return ip;
            }
        } catch (Exception e) {
            // Fallback к локальному IP если не удалось получить публичный
        }
        
        return getLocalIPAddress();
    }
    
    /**
     * Получает локальный IP-адрес сервера
     */
    public static String getLocalIPAddress() {
        try {
            // Получаем все сетевые интерфейсы
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                
                // Пропускаем неактивные и loopback интерфейсы
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // Берем только IPv4 адреса, не локальные
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && 
                        !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            
            // Fallback
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
    
    /**
     * Проверяет валидность IP-адреса
     */
    public static boolean isValidIPAddress(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Генерирует следующий доступный IP в подсети
     */
    public static String getNextAvailableIP(String baseIP, int subnet, List<String> usedIPs) {
        try {
            String[] parts = baseIP.split("\\.");
            int baseNum = Integer.parseInt(parts[3]);
            
            for (int i = baseNum + 1; i < 255; i++) {
                String nextIP = parts[0] + "." + parts[1] + "." + parts[2] + "." + i;
                if (!usedIPs.contains(nextIP)) {
                    return nextIP + "/" + subnet;
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        
        return "10.0.0." + (usedIPs.size() + 2) + "/" + subnet;
    }
}
