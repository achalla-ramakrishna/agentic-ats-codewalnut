package com.codewalnut.ats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AtsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtsApplication.class, args);
    }
}
