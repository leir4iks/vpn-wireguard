package com.leir4iks.wireguard.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик подключений к VPN серверу
 */
public class VPNConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {
    
    private static final Logger logger = LoggerFactory.getLogger(VPNConnectionHandler.class);
    
    private final VPNServer server;
    private VPNClient client;
    
    public VPNConnectionHandler(VPNServer server) {
        this.server = server;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.info("Новое подключение: {}", ctx.channel().remoteAddress());
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);
        
        try {
            VPNPacket packet = VPNPacket.deserialize(data);
            
            if (packet.getType() == VPNPacket.PacketType.HANDSHAKE) {
                handleHandshake(ctx, packet);
            } else if (client != null) {
                client.receivePacket(packet);
                server.routePacket(packet, client);
            }
            
        } catch (Exception e) {
            logger.error("Ошибка обработки пакета от {}", 
                        ctx.channel().remoteAddress(), e);
        }
    }
    
    /**
     * Обработка рукопожатия с клиентом
     */
    private void handleHandshake(ChannelHandlerContext ctx, VPNPacket packet) {
        try {
            String handshakeData = new String(packet.getData());
            String[] parts = handshakeData.split(":");
            
            if (parts.length >= 2) {
                String clientName = parts[0];
                String publicKey = parts[1];
                
                client = server.addClient(clientName, publicKey, ctx.channel());
                
                // Отправляем подтверждение
                VPNPacket response = new VPNPacket(
                    server.getServerIP(), 
                    packet.getSourceIP(),
                    ("HANDSHAKE_OK:" + client.getIP()).getBytes(),
                    VPNPacket.PacketType.HANDSHAKE
                );
                
                ctx.writeAndFlush(response.serialize());
            }
            
        } catch (Exception e) {
            logger.error("Ошибка обработки рукопожатия", e);
            ctx.close();
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (client != null) {
            server.removeClient(client.getName());
        }
        logger.info("Соединение закрыто: {}", ctx.channel().remoteAddress());
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Ошибка в канале {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
