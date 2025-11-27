// 작성자: 김민서
package com.dajanggan.global.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * 암호화 설정
 *
 * 주요 책임:
 * - AesGcmService 빈 생성 및 설정
 * - application.yml에서 암호화 설정 로드
 * - 마스터 키 파싱 및 관리
 *
 * 설정 항목:
 * - app.crypto.keys-v1: 마스터 키 목록 (형식: "keyId:base64Key,...")
 * - app.crypto.active-key-id: 현재 사용 중인 키 ID
 * - app.crypto.pbkdf2-iter: PBKDF2 반복 횟수 (기본: 200,000)
 * - app.crypto.salt-len: Salt 길이 (기본: 16바이트)
 * - app.crypto.iv-len: IV 길이 (기본: 12바이트)
 *
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-11  김민서    1. 최초작성
 *
 */

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
