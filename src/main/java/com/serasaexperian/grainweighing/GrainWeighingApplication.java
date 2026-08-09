package com.serasaexperian.grainweighing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GrainWeighingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrainWeighingApplication.class, args);
    }
}
