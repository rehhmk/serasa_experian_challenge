package com.serasaexperian.grainweighing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI grainWeighingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Grain Weighing Platform")
                .description("Desafio tecnico backend - ingestao, estabilizacao e armazenamento de pesagens")
                .version("v0.1"));
    }
}
