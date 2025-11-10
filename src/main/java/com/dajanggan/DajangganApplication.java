package com.dajanggan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DajangganApplication {

    public static void main(String[] args) {
        SpringApplication.run(DajangganApplication.class, args);
    }

}
