package com.leir4iks.wireguard.core;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Представляет VPN пакет для передачи между клиентами
 */
public class VPNPacket {
    
    private final String sourceIP;
    private final String destinationIP;
    private final byte[] data;
    private final long timestamp;
    private final PacketType type;
    
    public enum PacketType {
        DATA, PING, PONG, HANDSHAKE, DISCONNECT
    }
    
    public VPNPacket(String sourceIP, String destinationIP, byte[] data) {
        this(sourceIP, destinationIP, data, PacketType.DATA);
    }
    
    public VPNPacket(String sourceIP, String destinationIP, byte[] data, PacketType type) {
        this.sourceIP = sourceIP;
        this.destinationIP = destinationIP;
        this.data = data.clone();
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Сериализация пакета в байты
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Заголовок пакета
        dos.writeUTF(sourceIP != null ? sourceIP : "");
        dos.writeUTF(destinationIP != null ? destinationIP : "");
        dos.writeLong(timestamp);
        dos.writeInt(type.ordinal());
        
        // Данные
        dos.writeInt(data.length);
        dos.write(data);
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Десериализация пакета из байтов
     */
    public static VPNPacket deserialize(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        
        String sourceIP = dis.readUTF();
        String destinationIP = dis.readUTF();
        long timestamp = dis.readLong();
        PacketType type = PacketType.values()[dis.readInt()];
        
        int dataLength = dis.readInt();
        byte[] data = new byte[dataLength];
        dis.readFully(data);
        
        VPNPacket packet = new VPNPacket(sourceIP, destinationIP, data, type);
        return packet;
    }
    
    /**
     * Создание PING пакета
     */
    public static VPNPacket createPing(String sourceIP, String destinationIP) {
        return new VPNPacket(sourceIP, destinationIP, 
                            "PING".getBytes(), PacketType.PING);
    }
    
    /**
     * Создание PONG пакета
     */
    public static VPNPacket createPong(String sourceIP, String destinationIP) {
        return new VPNPacket(sourceIP, destinationIP, 
                            "PONG".getBytes(), PacketType.PONG);
    }
    
    // Геттеры
    public String getSourceIP() { return sourceIP; }
    public String getDestinationIP() { return destinationIP; }
    public byte[] getData() { return data.clone(); }
    public long getTimestamp() { return timestamp; }
    public PacketType getType() { return type; }
    
    @Override
    public String toString() {
        return String.format("VPNPacket{%s->%s, type=%s, size=%d, time=%d}", 
                           sourceIP, destinationIP, type, data.length, timestamp);
    }
}
