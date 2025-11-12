package com.dajanggan.global.crypto;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;

public final class AesGcmService {
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final byte VERSION = 0x01;

    private final SecureRandom rng = new SecureRandom();
    private final Map<Short, byte[]> masterKeys; // keyId -> 32byte key
    private final short activeKeyId;
    private final int pbkdf2Iter, saltLen, ivLen;

    public AesGcmService(Map<Short, byte[]> masterKeys, short activeKeyId,
                         int pbkdf2Iter, int saltLen, int ivLen) {
        this.masterKeys = Objects.requireNonNull(masterKeys);
        this.activeKeyId = activeKeyId;
        this.pbkdf2Iter = pbkdf2Iter;
        this.saltLen = saltLen;   // 16
        this.ivLen   = ivLen;     // 12
        if (!masterKeys.containsKey(activeKeyId))
            throw new IllegalArgumentException("Active key not found: " + activeKeyId);
    }

    public String encryptString(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] salt = new byte[saltLen]; rng.nextBytes(salt);
            SecretKey sk = deriveKey(masterKeys.get(activeKeyId), salt);

            byte[] iv = new byte[ivLen]; rng.nextBytes(iv);

            Cipher c = Cipher.getInstance(CIPHER);
            c.init(Cipher.ENCRYPT_MODE, sk, new GCMParameterSpec(TAG_BITS, iv));
            
            // 먼저 ciphertext를 생성
            byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ct = c.doFinal(plainBytes);

            // 전체 크기: version(1) + keyId(2) + salt + iv + ciphertext
            ByteBuffer bb = ByteBuffer.allocate(1 + 2 + salt.length + iv.length + ct.length);
            bb.put(VERSION).putShort(activeKeyId).put(salt).put(iv).put(ct);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    public String decryptToString(String blobB64) {
        if (blobB64 == null) return null;
        try {
            byte[] blob = Base64.getDecoder().decode(blobB64);
            
            // 최소 크기 체크: version(1) + keyId(2) + salt + iv + 최소 암호문
            int minSize = 1 + 2 + saltLen + ivLen;
            if (blob.length < minSize) {
                // 너무 짧으면 평문으로 간주
                return blobB64;
            }
            
            ByteBuffer bb = ByteBuffer.wrap(blob);

            byte version = bb.get();

            // 기존 평문일 경우 그냥 반환
            if (version != 0x01) {
                return blobB64; // 평문 그대로 반환
            }

            short keyId = bb.getShort();
            byte[] master = masterKeys.get(keyId);
            if (master == null) throw new CryptoException("Unknown keyId: " + keyId);

            byte[] salt = new byte[saltLen]; bb.get(salt);
            byte[] iv   = new byte[ivLen];   bb.get(iv);
            byte[] ct   = new byte[bb.remaining()]; bb.get(ct);

            SecretKey sk = deriveKey(master, salt);
            Cipher c = Cipher.getInstance(CIPHER);
            c.init(Cipher.DECRYPT_MODE, sk, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Base64 decoding 실패 → 평문
            return blobB64;
        } catch (AEADBadTagException e) {
            throw new CryptoException("Authentication failed - data may be corrupted: " + e.getMessage(), e);
        } catch (java.nio.BufferUnderflowException e) {
            // 버퍼 언더플로우 발생 시 평문으로 간주
            return blobB64;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Decryption failed: " + e.getMessage(), e);
        }
    }


    private SecretKey deriveKey(byte[] master, byte[] salt) throws GeneralSecurityException {
        KeySpec spec = new PBEKeySpec(
                Base64.getEncoder().encodeToString(master).toCharArray(),
                salt, pbkdf2Iter, 256);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    }

    public static final class CryptoException extends RuntimeException {
        public CryptoException(String m, Throwable c){ super(m,c); }
        public CryptoException(String m){ super(m); }
    }
}
