/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.service;

import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import com.dajanggan.domain.instance.domain.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VacuumMetricsService {

    private final DataSourceFactory dataSourceFactory;

    public VacuumMetricsService(DataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
    }

    public record VacuumStats(long deadTuples, long autovacuumRuns) {}

    public VacuumStats aggregate(Instance instance, String databaseName) {
        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, databaseName);
        Long deadTuples = jdbc.queryForObject("SELECT sum(n_dead_tup) FROM pg_stat_all_tables", Long.class);
        Long autoVacuum = jdbc.queryForObject("SELECT count(*) FROM pg_stat_all_tables WHERE last_autovacuum IS NOT NULL", Long.class);

        return new VacuumStats(deadTuples != null ? deadTuples : 0, autoVacuum != null ? autoVacuum : 0);
    }
}
