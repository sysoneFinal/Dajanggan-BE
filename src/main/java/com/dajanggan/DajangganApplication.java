package com.dajanggan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dajanggan.domain.**.repository") // ✅ Mapper 인터페이스 패키지
public class DajangganApplication {

    public static void main(String[] args) {
        SpringApplication.run(DajangganApplication.class, args);
    }

}
