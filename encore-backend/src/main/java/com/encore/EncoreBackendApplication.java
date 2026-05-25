package com.encore;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.encore.mapper")
@SpringBootApplication
public class EncoreBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EncoreBackendApplication.class, args);
    }
}
