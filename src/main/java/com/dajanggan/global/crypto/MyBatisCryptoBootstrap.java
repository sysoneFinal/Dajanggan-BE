package com.dajanggan.global.crypto;

import com.dajanggan.global.crypto.mybatis.SecretStringTypeHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 암호화 부트스트랩
 *
 * 주요 책임:
 * - SecretStringTypeHandler에 AesGcmService 주입
 * - 애플리케이션 시작 시 암호화 서비스 초기화
 *
 * 동작 방식:
 * - @PostConstruct로 애플리케이션 시작 시 실행
 * - SecretStringTypeHandler.setCrypto()를 호출하여 정적 필드 초기화
 * - MyBatis TypeHandler에서 암호화/복호화 가능하도록 설정
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-11  김민서    1. 최초작성
 */

@Configuration
@RequiredArgsConstructor
public class MyBatisCryptoBootstrap {

    private final AesGcmService aesGcmService;

    @PostConstruct
    public void init() {
        SecretStringTypeHandler.setCrypto(aesGcmService);
    }
}
