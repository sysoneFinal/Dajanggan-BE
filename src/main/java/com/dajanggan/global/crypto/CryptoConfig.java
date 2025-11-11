package com.dajanggan.global.crypto;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class CryptoConfig {
    @Bean
    public AesGcmService aesGcmService() {
        String raw = System.getenv("APP_CRYPTO_KEYS_V1");
        short activeId = Short.parseShort(System.getenv("APP_CRYPTO_ACTIVE_KEY_ID"));

        Map<Short, byte[]> store = new HashMap<>();
        for (String e : Objects.requireNonNull(raw).split(",")) {
            String[] kv = e.trim().split(":");
            store.put(Short.parseShort(kv[0]), Base64.getDecoder().decode(kv[1]));
        }

        int iter   = Integer.parseInt(System.getenv().getOrDefault("APP_CRYPTO_PBKDF2_ITER","200000"));
        int salt   = Integer.parseInt(System.getenv().getOrDefault("APP_CRYPTO_SALT_LEN","16"));
        int iv     = Integer.parseInt(System.getenv().getOrDefault("APP_CRYPTO_IV_LEN","12"));

        return new AesGcmService(store, activeId, iter, salt, iv);
    }
}

