package com.leir4iks.wireguard.common;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;
import org.apache.commons.codec.binary.Base64;

import java.security.SecureRandom;
import java.util.Arrays;

public class WireguardCrypto {
    
    private static final Curve25519 curve25519 = Curve25519.getInstance(Curve25519.BEST);
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Генерирует пару ключей WireGuard используя Curve25519
     */
    public static WireguardKeyPair generateKeyPair() {
        Curve25519KeyPair keyPair = curve25519.generateKeyPair();
        
        String privateKey = Base64.encodeBase64String(keyPair.getPrivateKey());
        String publicKey = Base64.encodeBase64String(keyPair.getPublicKey());
        
        return new WireguardKeyPair(privateKey, publicKey);
    }
    
    /**
     * Генерирует предварительно разделяемый ключ
     */
    public static String generatePresharedKey() {
        byte[] presharedKeyBytes = new byte[32];
        secureRandom.nextBytes(presharedKeyBytes);
        return Base64.encodeBase64String(presharedKeyBytes);
    }
    
    /**
     * Проверяет валидность приватного ключа WireGuard
     */
    public static boolean isValidPrivateKey(String privateKey) {
        try {
            byte[] keyBytes = Base64.decodeBase64(privateKey);
            return keyBytes.length == 32;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Получает публичный ключ из приватного
     */
    public static String getPublicKeyFromPrivate(String privateKey) {
        if (!isValidPrivateKey(privateKey)) {
            throw new IllegalArgumentException("Invalid private key");
        }
        
        byte[] privateKeyBytes = Base64.decodeBase64(privateKey);
        byte[] publicKeyBytes = curve25519.generatePublicKey(privateKeyBytes);
        
        return Base64.encodeBase64String(publicKeyBytes);
    }
    
    public static class WireguardKeyPair {
        private final String privateKey;
        private final String publicKey;
        
        public WireguardKeyPair(String privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
        
        public String getPrivateKey() { return privateKey; }
        public String getPublicKey() { return publicKey; }
    }
}
