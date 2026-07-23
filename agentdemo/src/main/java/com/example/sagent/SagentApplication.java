package com.example.sagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example")
public class SagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagentApplication.class, args);
    }
}
