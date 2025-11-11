package com.dajanggan.global.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class CryptoConfig {

    @Value("${app.crypto.keys-v1}")
    private String keysV1;

    @Value("${app.crypto.active-key-id}")
    private short activeKeyId;

    @Value("${app.crypto.pbkdf2-iter:200000}")
    private int pbkdf2Iter;

    @Value("${app.crypto.salt-len:16}")
    private int saltLen;

    @Value("${app.crypto.iv-len:12}")
    private int ivLen;

    @Bean
    public AesGcmService aesGcmService() {
        Map<Short, byte[]> store = new HashMap<>();
        for (String e : keysV1.split(",")) {
            String[] kv = e.trim().split(":");
            store.put(Short.parseShort(kv[0]), Base64.getDecoder().decode(kv[1]));
        }
        return new AesGcmService(store, activeKeyId, pbkdf2Iter, saltLen, ivLen);
    }
}