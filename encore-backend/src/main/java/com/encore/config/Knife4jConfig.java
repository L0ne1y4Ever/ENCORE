package com.encore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI encoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ENCORE API")
                        .version("0.1.0")
                        .description("ENCORE ticketing management backend APIs"));
    }
}
