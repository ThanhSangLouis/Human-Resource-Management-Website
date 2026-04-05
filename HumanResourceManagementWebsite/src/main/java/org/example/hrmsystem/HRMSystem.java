package org.example.hrmsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HRMSystem {

    public static void main(String[] args) {
        SpringApplication.run(HRMSystem.class, args);
    }

}
