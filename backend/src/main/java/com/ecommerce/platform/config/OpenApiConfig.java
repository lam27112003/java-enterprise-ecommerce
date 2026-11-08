package com.ecommerce.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ecommerceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Java Enterprise E-commerce Platform API")
                        .description("Modular monolith REST API — Phase 1 foundation")
                        .version("0.1.0")
                        .contact(new Contact().name("Platform Team"))
                        .license(new License().name("Proprietary")));
    }
}
