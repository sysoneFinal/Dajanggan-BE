package com.dajanggan.global.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.dajanggan.domain.**.repository")
public class MyBatisConfig {
}
