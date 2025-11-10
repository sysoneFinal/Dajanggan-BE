package com.dajanggan.domain.overview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class MetricDefinition {

    private String name;
    private String tableName;
    private String columnName;
    private String category;
    private String level;
    private String unit;
    private String description;
    private List<String> availableChart;
    private String defaultChartType;


}
