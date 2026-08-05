package com.example.sagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagentApplication.class, args);
    }
}
