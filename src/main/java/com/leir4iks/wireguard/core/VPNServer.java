package com.leir4iks.wireguard.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Основной VPN сервер, работающий полностью в Java без системных зависимостей
 */
public class VPNServer {
    
    private static final Logger logger = LoggerFactory.getLogger(VPNServer.class);
    
    private final int port;
    private final String serverIP;
    private final ConcurrentHashMap<String, VPNClient> clients = new ConcurrentHashMap<>();
    private final AtomicInteger clientCounter = new AtomicInteger(1);
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private boolean running = false;
    
    public VPNServer(int port) {
        this.port = port;
        this.serverIP = getServerIP();
    }
    
    /**
     * Запуск VPN сервера
     */
    public void start() throws InterruptedException {
        if (running) {
            throw new IllegalStateException("VPN сервер уже запущен");
        }
        
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // Обработка пакетов по длине
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 4, 0, 4));
                            pipeline.addLast(new LengthFieldPrepender(4));
                            
                            // Основной обработчик VPN
                            pipeline.addLast(new VPNConnectionHandler(VPNServer.this));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);
            
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            running = true;
            
            logger.info("VPN сервер запущен на {}:{}", serverIP, port);
            logger.info("Ожидание подключений клиентов...");
            
        } catch (Exception e) {
            logger.error("Ошибка запуска VPN сервера", e);
            stop();
            throw e;
        }
    }
    
    /**
     * Остановка VPN сервера
     */
    public void stop() {
        if (!running) return;
        
        running = false;
        
        // Отключаем всех клиентов
        clients.values().forEach(VPNClient::disconnect);
        clients.clear();
        
        // Закрываем сервер
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        logger.info("VPN сервер остановлен");
    }
    
    /**
     * Добавление нового клиента
     */
    public VPNClient addClient(String clientName, String publicKey, Channel channel) {
        String clientIP = "10.0.0." + clientCounter.getAndIncrement();
        VPNClient client = new VPNClient(clientName, publicKey, clientIP, channel);
        
        clients.put(clientName, client);
        logger.info("Подключен VPN клиент: {} ({})", clientName, clientIP);
        
        return client;
    }
    
    /**
     * Удаление клиента
     */
    public void removeClient(String clientName) {
        VPNClient client = clients.remove(clientName);
        if (client != null) {
            client.disconnect();
            logger.info("Отключен VPN клиент: {}", clientName);
        }
    }
    
    /**
     * Получение клиента по имени
     */
    public VPNClient getClient(String clientName) {
        return clients.get(clientName);
    }
    
    /**
     * Получение всех клиентов
     */
    public ConcurrentHashMap<String, VPNClient> getClients() {
        return clients;
    }
    
    /**
     * Маршрутизация пакета между клиентами
     */
    public void routePacket(VPNPacket packet, VPNClient sender) {
        String destinationIP = packet.getDestinationIP();
        
        // Поиск клиента назначения
        VPNClient destination = clients.values().stream()
                .filter(client -> client.getIP().equals(destinationIP))
                .findFirst()
                .orElse(null);
        
        if (destination != null && destination != sender) {
            destination.sendPacket(packet);
            logger.debug("Маршрутизация пакета {} -> {}", sender.getIP(), destinationIP);
        } else {
            // Если локальный клиент не найден, отправляем в интернет
            forwardToInternet(packet, sender);
        }
    }
    
    /**
     * Пересылка пакета в интернет через прокси
     */
    private void forwardToInternet(VPNPacket packet, VPNClient sender) {
        // Здесь реализуется простой HTTP/SOCKS прокси
        logger.debug("Пересылка пакета в интернет от {}", sender.getName());
        
        // Для упрощения - заглушка, в реальности здесь HTTP/SOCKS прокси
        VPNPacket response = new VPNPacket(
            packet.getDestinationIP(),
            packet.getSourceIP(),
            "HTTP/1.1 200 OK\r\n\r\nHello from VPN!".getBytes()
        );
        
        sender.sendPacket(response);
    }
    
    /**
     * Получение IP адреса сервера
     */
    private String getServerIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            logger.warn("Не удалось определить IP сервера, используем localhost");
            return "127.0.0.1";
        }
    }
    
    public String getServerIP() {
        return serverIP;
    }
    
    public int getPort() {
        return port;
    }
    
    public boolean isRunning() {
        return running;
    }
}
