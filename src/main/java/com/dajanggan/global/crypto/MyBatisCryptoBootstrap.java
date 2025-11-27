// 작성자: 김민서
package com.dajanggan.global.crypto;

import com.dajanggan.global.crypto.mybatis.SecretStringTypeHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MyBatisCryptoBootstrap {

    private final AesGcmService aesGcmService;

    @PostConstruct
    public void init() {
        SecretStringTypeHandler.setCrypto(aesGcmService);
    }
}
