package com.splitease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SpliteaseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpliteaseBackendApplication.class, args);
    }
}