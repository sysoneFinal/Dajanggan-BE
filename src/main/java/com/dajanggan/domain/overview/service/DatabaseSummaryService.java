/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.overview.dto.DatabaseMetricsAgg;
import com.dajanggan.domain.overview.repository.DatabaseMetricsAggRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DatabaseSummaryService {

    private final DatabaseMetricsAggRepository databaseMetricsAggRepository;

    public DatabaseSummaryService(DatabaseMetricsAggRepository databaseMetricsAggRepository){
        this.databaseMetricsAggRepository = databaseMetricsAggRepository;
    }

    public List<DatabaseMetricsAgg> getDatabaseSummaryMetrics(Long databaseId){
        return databaseMetricsAggRepository.getDatabaseSummary(databaseId);
    }
}
