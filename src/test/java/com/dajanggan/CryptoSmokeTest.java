package com.dajanggan;

import com.dajanggan.global.crypto.AesGcmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class CryptoSmokeTest {

    @TestConfiguration
    static class TestCryptoConfig {
        @Bean
        public AesGcmService aesGcmService() {
            String key = "";
            byte[] keyBytes = Base64.getDecoder().decode(key);
            Map<Short, byte[]> keys = Map.of((short) 1, keyBytes);
            return new AesGcmService(keys, (short) 1, 200000, 16, 12);
        }
    }

    @Autowired
    private AesGcmService crypto;

    @Test
    void roundTrip() {
        String plain = "test-DB-password-123!";
        String enc = crypto.encryptString(plain);
        String dec = crypto.decryptToString(enc);

        System.out.println("암호문: " + enc);
        System.out.println("복호화: " + dec);

        assertEquals(plain, dec);
        assertTrue(enc.startsWith("AQ"));
    }
}
