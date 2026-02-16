package com.idee.controlescolar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ControlEscolarApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlEscolarApplication.class, args);
    }
}
