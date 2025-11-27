/** 작성자 : 서샘이 */
package com.dajanggan.global.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager batchTransactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }


    @Bean
    public JobRepository jobRepository(@Qualifier("dataSource") DataSource dataSource,
                                       PlatformTransactionManager batchTransactionManager) throws Exception {
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(batchTransactionManager);
        factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");

        // tablePrefix: 배치 테이블 접두사 (BATCH_)
        factory.setTablePrefix("BATCH_");

        // PostgreSQL의 VARCHAR 길이 제한 지정 (Spring Batch 기본값은 100)
        factory.setMaxVarCharLength(2500);

        factory.afterPropertiesSet();
        return factory.getObject();
    }

    @Bean
    public JobExplorer jobExplorer(@Qualifier("dataSource") DataSource dataSource,
                                   PlatformTransactionManager batchTransactionManager) throws Exception {
        JobExplorerFactoryBean factory = new JobExplorerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(batchTransactionManager);
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}
