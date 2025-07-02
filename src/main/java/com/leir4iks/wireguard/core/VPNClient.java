package com.leir4iks.wireguard.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Представляет подключенного VPN клиента
 */
public class VPNClient {
    
    private static final Logger logger = LoggerFactory.getLogger(VPNClient.class);
    
    private final String name;
    private final String publicKey;
    private final String ip;
    private final Channel channel;
    private final LocalDateTime connectedAt;
    
    private final AtomicLong bytesReceived = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong packetsReceived = new AtomicLong(0);
    private final AtomicLong packetsSent = new AtomicLong(0);
    
    private volatile boolean connected = true;
    
    public VPNClient(String name, String publicKey, String ip, Channel channel) {
        this.name = name;
        this.publicKey = publicKey;
        this.ip = ip;
        this.channel = channel;
        this.connectedAt = LocalDateTime.now();
    }
    
    /**
     * Отправка пакета клиенту
     */
    public void sendPacket(VPNPacket packet) {
        if (!connected || !channel.isActive()) {
            return;
        }
        
        try {
            byte[] data = packet.serialize();
            ByteBuf buffer = Unpooled.wrappedBuffer(data);
            
            channel.writeAndFlush(buffer).addListener(future -> {
                if (future.isSuccess()) {
                    bytesSent.addAndGet(data.length);
                    packetsSent.incrementAndGet();
                } else {
                    logger.warn("Ошибка отправки пакета клиенту {}: {}", 
                              name, future.cause().getMessage());
                }
            });
            
        } catch (Exception e) {
            logger.error("Ошибка сериализации пакета для клиента " + name, e);
        }
    }
    
    /**
     * Получение пакета от клиента
     */
    public void receivePacket(VPNPacket packet) {
        if (!connected) return;
        
        bytesReceived.addAndGet(packet.getData().length);
        packetsReceived.incrementAndGet();
        
        logger.debug("Получен пакет от {}: {} байт", name, packet.getData().length);
    }
    
    /**
     * Отключение клиента
     */
    public void disconnect() {
        if (!connected) return;
        
        connected = false;
        
        if (channel.isActive()) {
            channel.close();
        }
        
        logger.info("Клиент {} отключен", name);
    }
    
    /**
     * Проверка активности соединения
     */
    public boolean isConnected() {
        return connected && channel.isActive();
    }
    
    /**
     * Получение статистики клиента
     */
    public VPNClientStats getStats() {
        return new VPNClientStats(
            name, ip, connectedAt,
            bytesReceived.get(), bytesSent.get(),
            packetsReceived.get(), packetsSent.get(),
            isConnected()
        );
    }
    
    // Геттеры
    public String getName() { return name; }
    public String getPublicKey() { return publicKey; }
    public String getIP() { return ip; }
    public Channel getChannel() { return channel; }
    public LocalDateTime getConnectedAt() { return connectedAt; }
}
