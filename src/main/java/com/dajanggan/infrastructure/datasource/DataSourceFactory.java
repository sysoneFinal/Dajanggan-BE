package com.dajanggan.infrastructure.datasource;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.global.crypto.AesGcmService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 다중 데이터베이스 인스턴스에 대한 JdbcTemplate을 동적으로 생성하는 팩토리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceFactory {

    private final AesGcmService aesGcmService;

    // 인스턴스+DB별 DataSource 캐싱
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * Instance와 Database 이름을 기반으로 JdbcTemplate 생성
     */
    public JdbcTemplate createJdbcTemplate(Instance instance, String databaseName) {
        DataSource dataSource = getOrCreateDataSource(instance, databaseName);
        return new JdbcTemplate(dataSource);
    }
    
    /**
     * 복호화된 비밀번호를 사용하여 JdbcTemplate 생성 (병렬 처리 최적화)
     */
    public JdbcTemplate createJdbcTemplate(Instance instance, String databaseName, String decryptedPassword) {
        DataSource dataSource = getOrCreateDataSource(instance, databaseName, decryptedPassword);
        return new JdbcTemplate(dataSource);
    }

    /**
     * DataSource 캐시에서 가져오거나 새로 생성
     */
    private DataSource getOrCreateDataSource(Instance instance, String databaseName) {
        String cacheKey = instance.getInstanceId() + ":" + databaseName;
        return dataSourceCache.computeIfAbsent(cacheKey,
                key -> createDataSource(instance, databaseName));
    }
    
    /**
     * 복호화된 비밀번호를 사용하여 DataSource 캐시에서 가져오거나 새로 생성
     */
    private DataSource getOrCreateDataSource(Instance instance, String databaseName, String decryptedPassword) {
        String cacheKey = instance.getInstanceId() + ":" + databaseName;
        return dataSourceCache.computeIfAbsent(cacheKey,
                key -> createDataSource(instance, databaseName, decryptedPassword));
    }

    /**
     * secretRef 복호화 (매번 수행 - 캐싱 제거)
     * secretRef는 암호화된 상태일 수도 있고, 이미 복호화된 평문일 수도 있음
     * decryptToString은 암호화된 값이면 복호화하고, 평문이면 그대로 반환
     */
    private String decryptPassword(Instance instance) {
        String secretRef = instance.getSecretRef();
        if (secretRef == null || secretRef.isEmpty()) {
            throw new RuntimeException("secretRef is null or empty for instance: " + instance.getInstanceName());
        }
        
        // secretRef가 암호화된 값인지 평문인지 확인을 위한 로깅
        log.debug(">>>> Processing secretRef for instance: {} (instanceId: {}, length: {}, startsWith base64: {})",
                instance.getInstanceName(), 
                instance.getInstanceId(), 
                secretRef.length(),
                secretRef.length() > 20 && secretRef.matches("^[A-Za-z0-9+/=]+$"));
        
        // decryptToString 호출:
        // - 암호화된 값(Base64 형식)이면 복호화
        // - 이미 평문이면 그대로 반환 (Base64 디코딩 실패 시 평문으로 간주)
        String password = aesGcmService.decryptToString(secretRef);
        
        if (password == null || password.isEmpty()) {
            throw new RuntimeException("Decrypted password is null or empty for instance: " + instance.getInstanceName());
        }
        
        log.debug(">>>> Password decrypted for instance: {} (instanceId: {}, password length: {})",
                instance.getInstanceName(), instance.getInstanceId(), password.length());
        return password;
    }

    /**
     * HikariCP를 사용한 DataSource 생성 (복호화된 비밀번호 사용)
     */
    private DataSource createDataSource(Instance instance, String databaseName, String decryptedPassword) {
        HikariConfig config = new HikariConfig();

        // JDBC URL 구성
        String host = instance.getHost();
        if (host != null) {
            host = host.trim(); // 공백 제거
        }
        log.warn("> [DEBUG] Host value: '{}', length: {}", host, host != null ? host.length() : 0);

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                databaseName);
        config.setJdbcUrl(jdbcUrl);
        log.info(">> Creating connection to: {}", jdbcUrl);

        // 인증 정보
        config.setUsername(instance.getUserName());
        config.setPassword(decryptedPassword);
        log.debug(">>>> Password set for instance: {} (length: {})",
                instance.getInstanceName(), decryptedPassword != null ? decryptedPassword.length() : 0);

        // SSL 설정
        if (instance.getSslmode() != null) {
            config.addDataSourceProperty("sslmode", instance.getSslmode());
            log.info(">>>>>> SSL Mode: {}", instance.getSslmode());
        } else {
            log.warn("******** No SSL mode configured for instance: {}", instance.getInstanceName());
        }

        // Connection Pool 설정
        config.setMaximumPoolSize(5);  // 메트릭 수집용이므로 작은 풀 크기
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);  // 10초
        config.setIdleTimeout(300000);  // 5분
        config.setMaxLifetime(600000);  // 10분

        // Pool 이름
        config.setPoolName("MetricsCollector-" + instance.getInstanceName() + "-" + databaseName);

        log.info(">>>>>>>>DataSource created for instance: {} ({}:{}/{})",
                instance.getInstanceName(), instance.getHost(), instance.getPort(), databaseName);

        return new HikariDataSource(config);
    }
    
    /**
     * HikariCP를 사용한 DataSource 생성 (기존 방식 - secretRef 복호화)
     */
    private DataSource createDataSource(Instance instance, String databaseName) {
        HikariConfig config = new HikariConfig();

        // JDBC URL 구성
        String host = instance.getHost();
        if (host != null) {
            host = host.trim(); // 공백 제거
        }
        log.warn("> [DEBUG] Host value: '{}', length: {}", host, host != null ? host.length() : 0);

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                databaseName);
        config.setJdbcUrl(jdbcUrl);
        log.info(">> Creating connection to: {}", jdbcUrl);

        // 인증 정보
        config.setUsername(instance.getUserName());

        // secretRef 복호화하여 비밀번호 설정 (매번 복호화 - 안전성 우선)
        if (instance.getSecretRef() != null && !instance.getSecretRef().isEmpty()) {
            try {
                String password = decryptPassword(instance);
                if (password == null || password.isEmpty()) {
                    throw new RuntimeException("Decrypted password is null or empty for instance: " + instance.getInstanceName());
                }
                config.setPassword(password);
                log.debug(">>>> Password set for instance: {} (length: {})",
                        instance.getInstanceName(), password.length());
            } catch (Exception e) {
                log.error("************Failed to decrypt password for instance: {} (instanceId: {}) - {}",
                        instance.getInstanceName(), instance.getInstanceId(), e.getMessage(), e);
                throw new RuntimeException("Failed to decrypt password for instance: " + instance.getInstanceName(), e);
            }
        } else {
            log.warn(">>>>>>>>>>No password (secretRef) configured for instance: {}", instance.getInstanceName());
        }

        // SSL 설정
        if (instance.getSslmode() != null) {
            config.addDataSourceProperty("sslmode", instance.getSslmode());
            log.info(">>>>>> SSL Mode: {}", instance.getSslmode());
        } else {
            log.warn("******** No SSL mode configured for instance: {}", instance.getInstanceName());
        }

        // Connection Pool 설정
        config.setMaximumPoolSize(5);  // 메트릭 수집용이므로 작은 풀 크기
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);  // 10초
        config.setIdleTimeout(300000);  // 5분
        config.setMaxLifetime(600000);  // 10분

        // Pool 이름
        config.setPoolName("MetricsCollector-" + instance.getInstanceName() + "-" + databaseName);

        log.info(">>>>>>>>DataSource created for instance: {} ({}:{}/{})",
                instance.getInstanceName(), instance.getHost(), instance.getPort(), databaseName);

        return new HikariDataSource(config);
    }

    /**
     * 특정 인스턴스의 모든 DataSource 제거 (인스턴스 삭제 시 호출)
     */
    public void removeDataSource(Long instanceId) {
        dataSourceCache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(instanceId + ":")) {
                DataSource dataSource = entry.getValue();
                if (dataSource instanceof HikariDataSource) {
                    ((HikariDataSource) dataSource).close();
                }
                return true;
            }
            return false;
        });
        log.info("All DataSources removed for instanceId: {}", instanceId);
    }

    /**
     * 모든 DataSource 정리 (애플리케이션 종료 시)
     */
    public void closeAll() {
        dataSourceCache.values().forEach(ds -> {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        });
        dataSourceCache.clear();
        log.info("All DataSources cleared");
    }
}