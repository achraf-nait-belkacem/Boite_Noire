package com.pigeon.boitenoire.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI boiteNoireOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Boite Noire API")
                .version("0.0.1")
                .description("Analytics over the events of Pigeon"));
    }
}
