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
     * DataSource 캐시에서 가져오거나 새로 생성
     */
    private DataSource getOrCreateDataSource(Instance instance, String databaseName) {
        String cacheKey = instance.getInstanceId() + ":" + databaseName;
        return dataSourceCache.computeIfAbsent(cacheKey, 
            key -> createDataSource(instance, databaseName));
    }

    /**
     * HikariCP를 사용한 DataSource 생성
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
        
        // secretRef 복호화하여 비밀번호 설정
        if (instance.getSecretRef() != null && !instance.getSecretRef().isEmpty()) {
            try {
                String password = aesGcmService.decryptToString(instance.getSecretRef());
                config.setPassword(password);
                log.debug(">>>> Password decrypted successfully for instance: {}", instance.getInstanceName());
                log.warn(">>>>> [TEMP DEBUG] Decrypted password length: {} chars", password.length()); // 임시 디버깅
            } catch (Exception e) {
                log.error("************Failed to decrypt password for instance: {} - {}",
                        instance.getInstanceName(), e.getMessage());
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
        log.info("All DataSources closed for instanceId: {}", instanceId);
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
        log.info("All DataSources closed");
    }
}
